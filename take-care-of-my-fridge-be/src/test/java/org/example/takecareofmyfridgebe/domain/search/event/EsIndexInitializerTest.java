package org.example.takecareofmyfridgebe.domain.search.event;

import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.service.EsIndexer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EsIndexInitializerTest {

    private final RecipeSearchProperties properties = mock(RecipeSearchProperties.class);
    private final EsIndexer existing = mock(EsIndexer.class);
    private final EsIndexer missing = mock(EsIndexer.class);
    private final EsIndexInitializer initializer = new EsIndexInitializer(List.of(existing, missing), properties);

    @Test
    @DisplayName("alias가 없는 인덱스만 전체 색인한다")
    void reindexesOnlyMissingAlias() throws IOException {
        // Given
        when(properties.useElasticSearch()).thenReturn(true);
        when(existing.aliasExists()).thenReturn(true);
        when(missing.aliasExists()).thenReturn(false);

        // When
        initializer.createMissingIndices();

        // Then
        verify(existing, never()).reindex();
        verify(missing).reindex();
    }

    @Test
    @DisplayName("검색 엔진이 MySQL이면 ES에 아무것도 묻지 않는다")
    void skipsWhenMySql() throws IOException {
        // Given
        when(properties.useElasticSearch()).thenReturn(false);

        // When
        initializer.createMissingIndices();

        // Then
        verify(existing, never()).aliasExists();
        verify(missing, never()).aliasExists();
    }

    @Test
    @DisplayName("한 인덱스가 실패해도 다음 인덱스를 계속 확인하고, 앱 시작을 막지 않는다")
    void continuesAfterFailure() throws IOException {
        // Given
        when(properties.useElasticSearch()).thenReturn(true);
        when(existing.aliasExists()).thenThrow(new IOException("ES 연결 실패"));
        when(missing.aliasExists()).thenReturn(false);

        // When & Then
        assertDoesNotThrow(initializer::createMissingIndices);
        verify(missing).reindex();
    }
}
