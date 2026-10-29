package org.example.takecareofmyfridgebe.domain.search.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.product.event.ProductChangedEvent;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.service.ProductIndexer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.IOException;

/**
 * 상품 등록·수정·판매 중지 이벤트가 들어오면 한 건 재색인
 * <br>
 * 실패하더라도 무시. 검색 결과의 가격·상태는 DB 값으로 다시 만들므로 색인이 늦어도 틀린 값은 보이지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductIndexEventListener {

    private final ProductIndexer productIndexer;
    private final RecipeSearchProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductChanged(ProductChangedEvent event) {
        if (!properties.useElasticSearch()) {
            return;
        }

        try {
            productIndexer.indexOne(event.productId());
        } catch (IOException | RuntimeException e) {
            log.error("상품 단건 색인 실패: productId={}", event.productId(), e);
        }
    }

}
