package org.example.takecareofmyfridgebe.domain.fridge.event;

import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeSource;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// 구매 확정 토픽에서 받는 메시지. 타입 헤더 없이 필드 이름으로 바꾸므로 주문 쪽 OrderConfirmedEvent와 필드 이름을 맞춘다.
// items에는 확정 수량이 남은 주문 상품만 오고, 모두 환불된 주문이면 비어 있다
public record PurchaseConfirmedMessage(
        Long orderId,
        Long memberId,
        LocalDateTime confirmedAt,
        List<Item> items
) {

    public record Item(
            Long orderItemId,
            Long productId,
            String productName,
            int confirmedQuantity
    ) {

        // 표시 이름은 상품명을 그대로 쓰고, 주문 상품 ID로 입고 출처를 남긴다
        public Fridge toEntity(
                Long memberId,
                Ingredient ingredient,
                BigDecimal quantity,
                StorageType storage,
                LocalDate expirationDate
        ) {
            return Fridge.builder()
                    .memberId(memberId)
                    .ingredient(ingredient)
                    .orderItemId(orderItemId)
                    .rawName(productName)
                    .quantity(quantity)
                    .storage(storage)
                    .expirationDate(expirationDate)
                    .source(FridgeSource.COMMERCE)
                    .build();
        }
    }
}
