package org.example.takecareofmyfridgebe.domain.order.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

// 주문번호 = 밀리초 시각 17자리 + 같은 밀리초 안의 순번 3자리 (예: 20261020143005123001)
// 순번은 Redis에서 밀리초 시각을 키로 INCR해 받아, 서버가 여러 대여도 같은 밀리초의 번호가 겹치지 않는다.
@Slf4j
@Service
public class OrderNumberGenerator {

    private static final String KEY_PREFIX = "order:number-seq:";
    // 서버끼리 시계가 어긋나도 늦은 서버가 같은 밀리초 키를 이어 세도록, 시계 차이보다 넉넉히 둔다
    private static final Duration KEY_TTL = Duration.ofSeconds(60);
    // 서버 시간대가 바뀌어도 번호의 시각이 뒤로 가지 않도록 고정한다
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter MILLIS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final int MAX_SEQUENCE = 999;

    private final StringRedisTemplate stringRedisTemplate;
    private final Clock clock;

    @Autowired
    public OrderNumberGenerator(StringRedisTemplate stringRedisTemplate) {
        this(stringRedisTemplate, Clock.systemUTC());
    }

    // 테스트에서 시각을 정해 넣을 때 쓴다
    OrderNumberGenerator(StringRedisTemplate stringRedisTemplate, Clock clock) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.clock = clock;
    }

    public String next() {
        String millis = LocalDateTime.ofInstant(Instant.ofEpochMilli(clock.millis()), ZONE).format(MILLIS_FORMAT);
        long sequence = increment(KEY_PREFIX + millis) - 1;
        // 모든 서버의 주문을 합쳐 한 밀리초에 1000건을 넘을 때만 걸린다. 다시 주문하게 한다.
        if (sequence > MAX_SEQUENCE) {
            throw new BusinessException(ErrorCode.ORDER_NUMBER_CONFLICT);
        }
        return millis + String.format("%03d", sequence);
    }

    private long increment(String key) {
        try {
            long count = stringRedisTemplate.opsForValue().increment(key);
            if (count == 1) {
                stringRedisTemplate.expire(key, KEY_TTL);
            }
            return count;
        } catch (DataAccessException e) {
            log.error("주문번호 순번 발급 실패 (Redis 장애) - key: {}", key, e);
            throw new BusinessException(ErrorCode.ORDER_NUMBER_UNAVAILABLE);
        }
    }
}
