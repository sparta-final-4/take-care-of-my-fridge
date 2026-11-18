package org.example.takecareofmyfridgebe.global.config;

import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * TypeSafe 호출(상담 의도 판단의 Jev)을 다른 스레드에서 돌리는 크기가 정해진 실행기.
 * 스레드 수와 대기 줄이 정해져 있고, 자리가 없으면 기다리지 않고 버린다(부르는 쪽이 Jev 실패로 보고 서버 답을 준다).
 * 돌려준 CompletableFuture를 취소하면 대기 줄의 작업은 줄에서 빼고, 돌고 있는 작업은 인터럽트한다(FutureTask.cancel(true)).
 * <p>
 * Executor를 구현하지 않는다. Executor 타입 빈이 하나라도 있으면 Spring Boot 기본 applicationTaskExecutor가 만들어지지 않기 때문이다.
 */
public final class TypeSafeExecutor implements AutoCloseable {

    private final ThreadPoolExecutor pool;

    public TypeSafeExecutor(int threads, int queueCapacity) {
        AtomicInteger count = new AtomicInteger();
        this.pool = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                task -> {
                    Thread thread = new Thread(task, "typesafe-jev-" + count.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 작업을 다른 스레드에서 시작한다. 스레드와 대기 줄이 모두 차 있으면 시작하지 않고 빈 값이다.
     * 돌려준 값을 취소하면(cancel) 아직 시작하지 않은 작업은 돌지 않고, 돌고 있는 작업 스레드는 인터럽트된다.
     */
    public <T> Optional<CompletableFuture<T>> trySupply(Supplier<T> task) {
        CompletableFuture<T> result = new CompletableFuture<>();
        FutureTask<Void> work = new FutureTask<>(() -> {
            try {
                result.complete(task.get());
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        }, null);
        try {
            pool.execute(work);
        } catch (RejectedExecutionException e) {
            return Optional.empty();
        }
        // 기다리는 쪽이 취소하면 대기 줄 자리를 비우고, 돌고 있으면 인터럽트한다
        result.whenComplete((value, error) -> {
            if (result.isCancelled()) {
                work.cancel(true);
                pool.remove(work);
            }
        });
        return Optional.of(result);
    }

    // 대기 줄에 있는 작업 수 (테스트·확인용)
    int queued() {
        return pool.getQueue().size();
    }

    // 앱을 끌 때 남은 작업을 기다리지 않는다 (기다리던 상담은 제한 시간 뒤 서버 답으로 간다)
    @Override
    public void close() {
        pool.shutdownNow();
    }
}
