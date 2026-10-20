package org.example.takecareofmyfridgebe.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderNumberGeneratorTest {

    private final OrderNumberGenerator generator = new OrderNumberGenerator();

    @Test
    @DisplayName("밀리초 시각 17자리와 순번 3자리로 20자리 숫자를 만든다")
    void next_returnsTwentyDigits() {
        assertThat(generator.next()).matches("\\d{20}");
    }

    @Test
    @DisplayName("연달아 만든 번호는 계속 커진다")
    void next_increases() {
        // 같은 밀리초 순번은 3자리까지라, 한 밀리초 안에 다 만들어져도 넘치지 않게 999개만 만든다
        List<String> numbers = IntStream.range(0, 999).mapToObj(i -> generator.next()).toList();

        assertThat(numbers).isSorted().doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("여러 스레드가 동시에 만들어도 번호가 겹치지 않는다")
    void next_concurrently_noDuplicates() throws InterruptedException {
        int threads = 20;
        int perThread = 1_000;
        Set<String> numbers = ConcurrentHashMap.newKeySet();
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        IntStream.range(0, threads).forEach(t -> executor.submit(() ->
            IntStream.range(0, perThread).forEach(i -> numbers.add(generator.next()))));
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(numbers).hasSize(threads * perThread);
    }

    @Test
    @DisplayName("같은 밀리초에 1000개를 넘으면 다음 밀리초 번호로 넘어가 20자리를 지킨다")
    void next_sequenceOverflow_movesToNextMillis() {
        Instant now = Instant.parse("2026-10-21T05:30:00.123Z");
        OrderNumberGenerator fixed = new OrderNumberGenerator(Clock.fixed(now, ZoneOffset.UTC));

        List<String> numbers = IntStream.range(0, 1_001).mapToObj(i -> fixed.next()).toList();

        assertThat(numbers).allMatch(n -> n.length() == 20).isSorted().doesNotHaveDuplicates();
        assertThat(numbers.get(999)).isEqualTo("20261021143000123999");
        assertThat(numbers.get(1_000)).isEqualTo("20261021143000124000");
    }

    @Test
    @DisplayName("시계가 뒤로 가도 번호는 계속 커진다")
    void next_clockGoesBack_keepsIncreasing() {
        AtomicLong millis = new AtomicLong(Instant.parse("2026-10-21T05:30:00Z").toEpochMilli());
        OrderNumberGenerator generator = new OrderNumberGenerator(new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(millis.get());
            }
        });

        String before = generator.next();
        millis.addAndGet(-5_000);
        String after = generator.next();

        assertThat(after).isGreaterThan(before);
    }

    @Test
    @DisplayName("번호의 시각은 서버 시간대와 관계없이 한국 시각이다")
    void next_usesSeoulTime() {
        Instant utcMidnight = Instant.parse("2026-10-21T00:00:00Z");

        String number = new OrderNumberGenerator(Clock.fixed(utcMidnight, ZoneOffset.UTC)).next();

        assertThat(number).startsWith("20261021090000000");
    }
}
