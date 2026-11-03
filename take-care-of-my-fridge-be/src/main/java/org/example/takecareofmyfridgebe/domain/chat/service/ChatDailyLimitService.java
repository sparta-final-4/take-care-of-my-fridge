package org.example.takecareofmyfridgebe.domain.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatProperties;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatDailyLimitService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String KEY_PREFIX = "chat:limit:";

    // 키가 있고 0보다 클 때만 1 줄인다. 만료된 키를 DECR하면 TTL 없는 -1 키가 생기므로 확인과 감소를 한 번에 실행한다
    private static final RedisScript<Long> REFUND_SCRIPT = new DefaultRedisScript<>(
            "local v = tonumber(redis.call('get', KEYS[1])) "
                    + "if v and v > 0 then return redis.call('decr', KEYS[1]) else return -1 end",
            Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;
    private final ChatProperties chatProperties;

    /**
     * 회원의 오늘 상담 메시지 전송 횟수를 증가시키고 일일 제한(30개) 초과 여부를 검증한다.
     * 키 생성 시 또는 TTL이 누락된 경우 다음 날 0시(Asia/Seoul) + 1시간으로 TTL을 설정한다.
     * 제한을 초과하면 429(CHAT_DAILY_LIMIT_EXCEEDED) 예외를 발생시키며, 카운트는 롤백되지 않고 유지된다.
     * Redis 연결 장애 시 503(CHAT_SERVICE_UNAVAILABLE) 예외를 발생시킨다.
     *
     * @param memberId 회원 ID
     * @return 늘린 키. 답을 못 받아 돌려줄 때 같은 날짜 키를 줄이도록 refund에 넘긴다
     */
    public String checkAndIncrement(Long memberId) {
        ZonedDateTime nowSeoul = ZonedDateTime.now(SEOUL_ZONE);
        String yyyyMMdd = nowSeoul.format(DATE_FORMATTER);
        String key = KEY_PREFIX + memberId + ":" + yyyyMMdd;

        Long count;
        try {
            count = stringRedisTemplate.opsForValue().increment(key);

            Long expireSeconds = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
            if (expireSeconds == null || expireSeconds < 0) {
                ZonedDateTime nextDayOneAm = nowSeoul.toLocalDate().plusDays(1).atTime(1, 0).atZone(SEOUL_ZONE);
                Duration ttl = Duration.between(nowSeoul, nextDayOneAm);
                stringRedisTemplate.expire(key, ttl);
            }
        } catch (DataAccessException e) {
            log.error("Redis 일일 상담 제한 확인 실패 (Redis 장애) - memberId: {}", memberId, e);
            throw new BusinessException(ErrorCode.CHAT_SERVICE_UNAVAILABLE);
        }

        if (count != null && count > chatProperties.dailyLimit()) {
            throw new BusinessException(ErrorCode.CHAT_DAILY_LIMIT_EXCEEDED);
        }
        return key;
    }

    /**
     * checkAndIncrement로 늘린 횟수를 1 돌려준다. 키가 없거나(만료) 0 이하면 줄이지 않는다.
     * 돌려주기에 실패해도 상담 오류 응답을 막지 않도록 던지지 않는다.
     *
     * @param key checkAndIncrement가 돌려준 키
     */
    public void refund(String key) {
        if (key == null) {
            return;
        }
        try {
            Long result = stringRedisTemplate.execute(REFUND_SCRIPT, List.of(key));
            if (result == null || result < 0) {
                log.warn("상담 하루 횟수를 돌려줄 키가 없거나 0이라 줄이지 않음 - key: {}", key);
            }
        } catch (DataAccessException e) {
            log.warn("Redis 상담 하루 횟수 돌려주기 실패 - key: {}", key, e);
        }
    }
}
