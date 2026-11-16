package org.example.takecareofmyfridgebe.global.config;

import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 결과를 기다리지 않는 TypeSafe 호출(상담 Jev 그림자 모드)을 돌리는 크기가 정해진 실행기.
 * 스레드 수와 대기 줄이 정해져 있고, 자리가 없으면 기다리지 않고 버린다(부르는 쪽이 건너뛴 것으로 기록한다).
 * <p>
 * Executor를 구현하지 않는다. Executor 타입 빈이 하나라도 있으면 Spring Boot 기본 applicationTaskExecutor가 만들어지지 않기 때문이다.
 */
public final class TypeSafeShadowExecutor implements AutoCloseable {

    private final ThreadPoolExecutor pool;

    public TypeSafeShadowExecutor(int threads, int queueCapacity) {
        AtomicInteger count = new AtomicInteger();
        this.pool = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                task -> {
                    Thread thread = new Thread(task, "typesafe-shadow-" + count.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 작업을 다른 스레드에서 시작한다. 스레드와 대기 줄이 모두 차 있으면 시작하지 않고 빈 값이다.
     */
    public <T> Optional<CompletableFuture<T>> trySupply(Supplier<T> task) {
        try {
            return Optional.of(CompletableFuture.supplyAsync(task, pool));
        } catch (RejectedExecutionException e) {
            return Optional.empty();
        }
    }

    // 앱을 끌 때 남은 작업을 기다리지 않는다 (결과를 쓰지 않는 호출이다)
    @Override
    public void close() {
        pool.shutdownNow();
    }
}
