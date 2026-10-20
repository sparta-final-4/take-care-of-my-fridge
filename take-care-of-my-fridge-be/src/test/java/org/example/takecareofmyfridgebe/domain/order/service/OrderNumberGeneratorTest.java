package org.example.takecareofmyfridgebe.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
}
