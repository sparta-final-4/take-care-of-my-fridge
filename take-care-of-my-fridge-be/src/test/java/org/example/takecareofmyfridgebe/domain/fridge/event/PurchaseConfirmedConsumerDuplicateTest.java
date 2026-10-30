package org.example.takecareofmyfridgebe.domain.fridge.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import org.example.takecareofmyfridgebe.domain.fridge.facade.FridgeFacade;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

// 같은 메시지를 동시에 처리해 입고 이력 유니크 제약을 어긴 경우만 건너뛰고, 다른 제약 위반은 다시 던지는지 확인한다.
// 실제 Kafka에서는 먼저 거르는 단계에 걸려 이 경로를 재현하기 어려워 단위 테스트로 둔다
@ExtendWith(MockitoExtension.class)
class PurchaseConfirmedConsumerDuplicateTest {

    private static final PurchaseConfirmedMessage MESSAGE = new PurchaseConfirmedMessage(
            500L, 7L, LocalDateTime.of(2026, 10, 30, 14, 30),
            List.of(new PurchaseConfirmedMessage.Item(101L, 1L, "양파 1kg", 2)));

    @Mock
    private FridgeFacade fridgeFacade;

    @InjectMocks
    private PurchaseConfirmedConsumer consumer;

    @Test
    @DisplayName("입고 이력 유니크 제약 위반이면 이미 입고된 것으로 보고 예외 없이 건너뛴다")
    void consume_commerceHistoryUniqueViolation_skips() {
        doThrow(violation("uk_fridge_commerce_histories_order_item_id")).when(fridgeFacade).stockPurchasedItems(MESSAGE);

        assertThatCode(() -> consumer.consume(MESSAGE)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("다른 제약 위반은 다시 던져 재시도 뒤 DLT로 가게 한다")
    void consume_otherViolation_rethrows() {
        DataIntegrityViolationException other = violation("fk_fridge_ingredient");
        doThrow(other).when(fridgeFacade).stockPurchasedItems(MESSAGE);

        assertThatThrownBy(() -> consumer.consume(MESSAGE)).isSameAs(other);
    }

    private static DataIntegrityViolationException violation(String constraintName) {
        return new DataIntegrityViolationException("constraint violation",
                new ConstraintViolationException("constraint violation", new SQLException(), constraintName));
    }
}
