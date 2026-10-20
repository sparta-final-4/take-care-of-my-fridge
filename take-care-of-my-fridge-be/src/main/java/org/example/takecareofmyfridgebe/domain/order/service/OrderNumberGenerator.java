package org.example.takecareofmyfridgebe.domain.order.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

// 주문번호 = 밀리초 시각 17자리 + 같은 밀리초 안의 순번 3자리 (예: 20261020143005123001)
// DB에 묻지 않고 서버 메모리에서 만든다. 서버 1대 기준이며, 서버가 늘면 서버 구분 자리가 필요하다.
@Service
public class OrderNumberGenerator {

    // 서버 시간대가 바뀌어도 번호의 시각이 뒤로 가지 않도록 고정한다
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter MILLIS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final int MAX_SEQUENCE = 999;

    private final Clock clock;
    private long lastMillis = -1;
    private int sequence;

    public OrderNumberGenerator() {
        this(Clock.systemUTC());
    }

    // 테스트에서 시각을 정해 넣을 때 쓴다
    OrderNumberGenerator(Clock clock) {
        this.clock = clock;
    }

    public synchronized String next() {
        // 시계가 뒤로 가도 번호가 거꾸로 가지 않게 마지막 시각보다 앞으로 가지 않는다
        long millis = Math.max(clock.millis(), lastMillis);
        if (millis == lastMillis) {
            sequence++;
        } else {
            sequence = 0;
        }
        // 시계가 뒤로 간 동안에는 같은 밀리초에 순번이 쌓이므로, 999를 넘으면 다음 밀리초로 넘겨 20자리를 지킨다
        if (sequence > MAX_SEQUENCE) {
            millis++;
            sequence = 0;
        }
        lastMillis = millis;

        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZONE);
        return time.format(MILLIS_FORMAT) + String.format("%03d", sequence);
    }
}
