package org.example.takecareofmyfridgebe.domain.search.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.config.SearchEngine;
import org.example.takecareofmyfridgebe.domain.search.service.ProductIndexer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class EsReindexSchedulerTest {

    private final ProductIndexer productIndexer = mock(ProductIndexer.class);

    @Test
    @DisplayName("서버가 여러 대여도 한 대만 실행하도록 서버 간 잠금을 건다")
    void locksAcrossServers() throws NoSuchMethodException {
        SchedulerLock lock = EsReindexScheduler.class.getMethod("reindexProducts")
                .getAnnotation(SchedulerLock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.name()).isEqualTo("esProductReindex");
    }

    @Test
    @DisplayName("ES 검색을 쓰면 상품 인덱스를 전체 재색인한다")
    void reindexesProducts() throws IOException {
        scheduler(SearchEngine.ELASTICSEARCH).reindexProducts();

        verify(productIndexer).reindex();
    }

    @Test
    @DisplayName("MySQL 검색을 쓰면 재색인하지 않는다")
    void skipsWhenMySql() throws IOException {
        scheduler(SearchEngine.MYSQL).reindexProducts();

        verify(productIndexer, never()).reindex();
    }

    @Test
    @DisplayName("재색인이 실패해도 예외를 밖으로 던지지 않는다")
    void swallowsFailure() throws IOException {
        given(productIndexer.reindex()).willThrow(new IOException("ES 연결 실패"));

        assertThatCode(() -> scheduler(SearchEngine.ELASTICSEARCH).reindexProducts())
                .doesNotThrowAnyException();
    }

    private EsReindexScheduler scheduler(SearchEngine engine) {
        return new EsReindexScheduler(productIndexer, new RecipeSearchProperties(engine, null, null));
    }
}
