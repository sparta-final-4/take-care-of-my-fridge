package org.example.takecareofmyfridgebe.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.example.takecareofmyfridgebe.global.config.RedisConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.support.RedisContainerSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

// 생성기 두 개를 서버 두 대로 보고 같은 Redis를 쓰게 해, 같은 밀리초에 만든 번호가 겹치지 않는지 Redis 상태까지 확인한다.
@SpringBootTest(classes = RedisConfig.class)
@ImportAutoConfiguration(DataRedisAutoConfiguration.class)
class OrderNumberGeneratorTest extends RedisContainerSupport {

    // UTC 05:30:00.123 = 한국 시각 14:30:00.123
    private static final Instant NOW = Instant.parse("2026-10-30T05:30:00.123Z");
    private static final String KEY = "order:number-seq:20261030143000123";

    private final StringRedisTemplate stringRedisTemplate;

    @Autowired
    OrderNumberGeneratorTest(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @AfterEach
    void cleanUp() {
        Set<String> keys = stringRedisTemplate.keys("order:number-seq:*");
        if (!keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("두 서버가 같은 밀리초에 동시에 번호를 만들어도 겹치지 않는다")
    void next_twoServersSameMillis_noDuplicates() throws InterruptedException {
        List<OrderNumberGenerator> servers = List.of(fixedAt(NOW), fixedAt(NOW));
        int perServer = 500;
        Set<String> numbers = ConcurrentHashMap.newKeySet();
        ExecutorService executor = Executors.newFixedThreadPool(servers.size());
        CountDownLatch start = new CountDownLatch(1);

        servers.forEach(server -> executor.submit(() -> {
            start.await();
            IntStream.range(0, perServer).forEach(i -> numbers.add(server.next()));
            return null;
        }));
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        Set<String> expected = IntStream.range(0, 1_000)
            .mapToObj(sequence -> "20261030143000123" + String.format("%03d", sequence))
            .collect(Collectors.toSet());
        assertThat(numbers).isEqualTo(expected);
        assertThat(stringRedisTemplate.opsForValue().get(KEY)).isEqualTo("1000");
    }

    @Test
    @DisplayName("번호는 한국 시각 17자리와 순번 3자리이고, 순번 키에는 만료 시간이 걸린다")
    void next_formatAndKeyTtl() {
        Instant utcMidnight = Instant.parse("2026-10-21T00:00:00Z");

        String number = fixedAt(utcMidnight).next();

        assertThat(number).isEqualTo("20261021090000000000");
        assertThat(stringRedisTemplate.getExpire("order:number-seq:20261021090000000", TimeUnit.SECONDS))
            .isBetween(1L, 60L);
    }

    @Test
    @DisplayName("같은 밀리초에 1000개를 넘으면 다시 주문하게 거절한다")
    void next_sequenceOverflow_rejects() {
        OrderNumberGenerator generator = fixedAt(NOW);
        IntStream.range(0, 1_000).forEach(i -> generator.next());

        assertThatThrownBy(generator::next)
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NUMBER_CONFLICT);
    }

    @Test
    @DisplayName("Redis에 연결할 수 없으면 503으로 거절한다")
    void next_redisUnavailable_rejects() {
        LettuceConnectionFactory unreachable = new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", 1));
        unreachable.afterPropertiesSet();
        unreachable.start();
        OrderNumberGenerator generator = new OrderNumberGenerator(new StringRedisTemplate(unreachable), Clock.fixed(NOW, ZoneOffset.UTC));

        try {
            assertThatThrownBy(generator::next)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NUMBER_UNAVAILABLE);
        } finally {
            unreachable.destroy();
        }
    }

    private OrderNumberGenerator fixedAt(Instant instant) {
        return new OrderNumberGenerator(stringRedisTemplate, Clock.fixed(instant, ZoneOffset.UTC));
    }
}
