package org.example.takecareofmyfridgebe.domain.search.event;

import org.example.takecareofmyfridgebe.domain.recipe.event.RecipeChangedEvent;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeIndexer;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 실제 트랜잭션에서 커밋된 변경만 색인하는지 확인한다. ES 호출은 RecipeIndexer를 목으로 바꿔 막는다
@SpringBootTest(properties = "app.search.engine=elasticsearch")
class RecipeIndexEventListenerIntegrationTest extends MySqlContainerSupport {

    @MockitoBean
    private RecipeIndexer recipeIndexer;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("트랜잭션이 커밋되면 색인한다")
    void indexesAfterCommit() throws IOException {
        // When
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(new RecipeChangedEvent(7L)));

        // Then
        verify(recipeIndexer).indexOne(7L);
    }

    @Test
    @DisplayName("트랜잭션이 롤백되면 색인하지 않는다")
    void skipsAfterRollback() throws IOException {
        // When
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new RecipeChangedEvent(8L));
            status.setRollbackOnly();
        });

        // Then
        verify(recipeIndexer, never()).indexOne(anyLong());
    }
}
