package org.example.takecareofmyfridgebe.domain.search.event;

import org.example.takecareofmyfridgebe.domain.recipe.event.RecipeChangedEvent;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeIndexer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeIndexEventListenerTest {

    @Mock
    private RecipeIndexer recipeIndexer;
    @Mock
    private RecipeSearchProperties properties;
    @InjectMocks
    private RecipeIndexEventListener listener;

    @Test
    @DisplayName("검색 엔진이 ES면 바뀐 레시피 하나를 다시 색인한다")
    void indexesChangedRecipe() throws IOException {
        // Given
        when(properties.useElasticSearch()).thenReturn(true);

        // When
        listener.onRecipeChanged(new RecipeChangedEvent(7L));

        // Then
        verify(recipeIndexer).indexOne(7L);
    }

    @Test
    @DisplayName("검색 엔진이 MySQL이면 색인하지 않는다")
    void skipsWhenMySql() throws IOException {
        // Given
        when(properties.useElasticSearch()).thenReturn(false);

        // When
        listener.onRecipeChanged(new RecipeChangedEvent(7L));

        // Then
        verify(recipeIndexer, never()).indexOne(anyLong());
    }

    @Test
    @DisplayName("색인이 실패해도 예외를 밖으로 던지지 않는다")
    void swallowsIndexFailure() throws IOException {
        // Given
        when(properties.useElasticSearch()).thenReturn(true);
        doThrow(new IOException("ES 연결 실패")).when(recipeIndexer).indexOne(7L);

        // When & Then
        assertDoesNotThrow(() -> listener.onRecipeChanged(new RecipeChangedEvent(7L)));
    }
}
