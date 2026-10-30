package org.example.takecareofmyfridgebe.domain.fridge.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.fridge.facade.FridgeFacade;
import org.example.takecareofmyfridgebe.global.config.KafkaTopics;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// 구매 확정을 받아 주문 상품을 냉장고에 넣는다.
// 이미 입고된 주문 상품은 Facade가 먼저 거르고, 같은 메시지를 동시에 처리해 입고 이력 유니크 제약을 어기면 건너뛴다.
// 위반은 저장 트랜잭션이 롤백된 뒤 여기서 잡는다. 트랜잭션 안에서 잡으면 롤백 표시가 남아 커밋 때 다시 실패한다.
// 그 밖의 실패는 다시 던져 재시도 뒤 DLT로 보낸다.
// 이 리스너만 auto.offset.reset=latest로 둔다. 커밋한 위치가 없는 첫 배포 때 토픽에 남은 지난 구매 확정을 한꺼번에 넣지 않기 위해서다.
// 커밋한 위치가 있으면 이 설정과 관계없이 거기서 이어 읽는다
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseConfirmedConsumer {

    private static final String COMMERCE_HISTORY_UNIQUE = "uk_fridge_commerce_histories_order_item_id";

    private final FridgeFacade fridgeFacade;

    @KafkaListener(
            topics = KafkaTopics.PURCHASE_CONFIRMED,
            groupId = "fridge",
            properties = "auto.offset.reset=latest"
    )
    public void consume(PurchaseConfirmedMessage message) {
        try {
            fridgeFacade.stockPurchasedItems(message);
        } catch (DataIntegrityViolationException e) {
            if (!constraintName(e).contains(COMMERCE_HISTORY_UNIQUE)) {
                throw e;
            }
            log.info("이미 입고된 구매 확정 건너뜀: orderId={}", message.orderId());
        }
    }

    private String constraintName(DataIntegrityViolationException e) {
        if (e.getCause() instanceof ConstraintViolationException cause && cause.getConstraintName() != null) {
            return cause.getConstraintName();
        }
        return "";
    }
}
