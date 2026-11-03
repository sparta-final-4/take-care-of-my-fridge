package org.example.takecareofmyfridgebe.domain.search.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.recipe.event.RecipeChangedEvent;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeIndexer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.IOException;

/**
 * 이벤트 들어오면 한 건 재색인
 * <br>
 * 실패하더라도 무시하고 로그만 남긴다. 레시피는 정기 전체 재색인이 없어서(EsReindexScheduler는 상품만),
 * 실패한 레시피는 다시 저장되거나 alias를 지우고 재시작해 전체 색인할 때까지 이전 상태로 검색된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecipeIndexEventListener {

    private final RecipeIndexer recipeIndexer;
    private final RecipeSearchProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRecipeChanged(RecipeChangedEvent event) {
        if (!properties.useElasticSearch()) {
            return;
        }

        try {
            recipeIndexer.indexOne(event.recipeId());
        } catch (IOException | RuntimeException e) {
            log.error("레시피 단건 색인 실패: recipeId={}", event.recipeId(), e);
        }
    }

}
