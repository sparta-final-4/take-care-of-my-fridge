package org.example.takecareofmyfridgebe.domain.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatProperties;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatDailyLimitService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String KEY_PREFIX = "chat:limit:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ChatProperties chatProperties;

    /**
     * 회원의 오늘 상담 메시지 전송 횟수를 증가시키고 일일 제한(30개) 초과 여부를 검증한다.
     * 키 생성 시 또는 TTL이 누락된 경우 다음 날 0시(Asia/Seoul) + 1시간으로 TTL을 설정한다.
     * 제한을 초과하면 429(CHAT_DAILY_LIMIT_EXCEEDED) 예외를 발생시키며, 카운트는 롤백되지 않고 유지된다.
     * Redis 연결 장애 시 503(CHAT_SERVICE_UNAVAILABLE) 예외를 발생시킨다.
     *
     * @param memberId 회원 ID
     */
    public void checkAndIncrement(Long memberId) {
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
    }
}
