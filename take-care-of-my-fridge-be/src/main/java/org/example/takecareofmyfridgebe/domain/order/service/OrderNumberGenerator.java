package org.example.takecareofmyfridgebe.domain.order.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

// 주문번호 = 밀리초 시각 17자리 + 같은 밀리초 안의 순번 3자리 (예: 20261020143005123001)
// DB에 묻지 않고 서버 메모리에서 만든다. 서버 1대 기준이며, 서버가 늘면 서버 구분 자리가 필요하다.
@Service
public class OrderNumberGenerator {

    private static final DateTimeFormatter MILLIS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private long lastMillis = -1;
    private int sequence;

    public synchronized String next() {
        // 시계가 뒤로 가도 번호가 거꾸로 가지 않게 마지막 시각보다 앞으로 가지 않는다
        long millis = Math.max(System.currentTimeMillis(), lastMillis);
        if (millis == lastMillis) {
            sequence++;
        } else {
            sequence = 0;
        }
        lastMillis = millis;

        LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
        return time.format(MILLIS_FORMAT) + String.format("%03d", sequence);
    }
}
