package org.example.takecareofmyfridgebe.global.config;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.example.takecareofmyfridgebe.support.RedisContainerSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {
        RedisConfig.class,
        ShedLockConfig.class
})
@ImportAutoConfiguration(DataRedisAutoConfiguration.class)
class ReviewSummaryLockIntegrationTest extends RedisContainerSupport {

    @Autowired
    private LockProvider lockProvider;

    @Autowired
    private LockingTaskExecutor lockingTaskExecutor;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("Testcontainers Redis 환경에서 두 번 연달아 잠금을 시도하면 두 번째는 실패한다")
    void consecutiveLockAttemptsFailSecondTime() {
        LockConfiguration lockConfig = new LockConfiguration(
                Instant.now(),
                "reviewSummary",
                Duration.ofMinutes(30),
                Duration.ofMinutes(5)
        );

        // 첫 번째 잠금 시도 -> 성공
        Optional<SimpleLock> firstLock = lockProvider.lock(lockConfig);
        assertThat(firstLock).isPresent();

        // Redis 키 접두어 확인 (job-lock:fridge:reviewSummary)
        Boolean keyExists = redisTemplate.hasKey("job-lock:fridge:reviewSummary");
        assertThat(keyExists).isTrue();

        // 두 번째 잠금 시도 -> 실패 (empty)
        Optional<SimpleLock> secondLock = lockProvider.lock(lockConfig);
        assertThat(secondLock).isEmpty();

        // 첫 번째 락을 명시적으로 해제하려고 해도 lockAtLeastFor(5분)에 의해 락이 유지됨
        firstLock.get().unlock();
        Optional<SimpleLock> thirdLock = lockProvider.lock(lockConfig);
        assertThat(thirdLock).isEmpty();
    }

    @Test
    @DisplayName("LockingTaskExecutor로 동일 잠금 실행 시 두 번째는 작업이 실행되지 않는다")
    void lockingTaskExecutorExecutesOnlyOnceConcurrently() throws Throwable {
        LockConfiguration lockConfig = new LockConfiguration(
                Instant.now(),
                "reviewSummaryExecutorTest",
                Duration.ofMinutes(30),
                Duration.ofMinutes(5)
        );

        AtomicInteger counter = new AtomicInteger(0);

        LockingTaskExecutor.TaskResult<Integer> firstResult = lockingTaskExecutor.executeWithLock(
                counter::incrementAndGet,
                lockConfig
        );
        assertThat(firstResult.wasExecuted()).isTrue();
        assertThat(firstResult.getResult()).isEqualTo(1);

        LockingTaskExecutor.TaskResult<Integer> secondResult = lockingTaskExecutor.executeWithLock(
                counter::incrementAndGet,
                lockConfig
        );
        assertThat(secondResult.wasExecuted()).isFalse();
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("수동 실행처럼 lockAtLeastFor가 0이면 작업 완료 즉시 다시 잠금을 획득하여 실행할 수 있다")
    void canAcquireLockImmediatelyWhenLockAtLeastForIsZero() throws Throwable {
        LockConfiguration manualLockConfig = new LockConfiguration(
                Instant.now(),
                "reviewSummaryManualZeroLockTest",
                Duration.ofMinutes(30),
                Duration.ZERO
        );

        AtomicInteger counter = new AtomicInteger(0);

        // 첫 번째 수동 실행 -> 성공
        LockingTaskExecutor.TaskResult<Integer> firstResult = lockingTaskExecutor.executeWithLock(
                counter::incrementAndGet,
                manualLockConfig
        );
        assertThat(firstResult.wasExecuted()).isTrue();
        assertThat(firstResult.getResult()).isEqualTo(1);

        // 작업이 끝난 직후 두 번째 수동 실행 -> lockAtLeastFor가 0이므로 즉시 성공
        LockingTaskExecutor.TaskResult<Integer> secondResult = lockingTaskExecutor.executeWithLock(
                counter::incrementAndGet,
                manualLockConfig
        );
        assertThat(secondResult.wasExecuted()).isTrue();
        assertThat(secondResult.getResult()).isEqualTo(2);
        assertThat(counter.get()).isEqualTo(2);
    }
}
