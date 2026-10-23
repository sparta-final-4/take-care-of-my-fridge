package org.example.takecareofmyfridgebe.domain.refund.facade;

import org.example.takecareofmyfridgebe.domain.payment.port.PaymentGateway;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundCreateRequest;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundRequested;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundResponse;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundReasonType;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundStatus;
import org.example.takecareofmyfridgebe.domain.refund.service.RefundCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class RefundFacadeTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long ORDER_ID = 10L;
    private static final Long REFUND_ID = 100L;
    private static final String PORTONE_PAYMENT_ID = "payment-1";

    @Mock
    private RefundCommandService refundCommandService;

    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private RefundFacade refundFacade;

    private final RefundCreateRequest request = new RefundCreateRequest(
            ORDER_ID, List.of(new RefundCreateRequest.Item(1L, 2)), RefundReasonType.CHANGE_OF_MIND, null);

    @Test
    @DisplayName("요청 상태로 저장한 뒤 환불 금액만큼 PG를 부분 취소하고 취소 ID로 완료 처리한 결과를 돌려준다")
    void request_cancelsPgThenCompletes() {
        RefundResponse completed = new RefundResponse(REFUND_ID, RefundStatus.COMPLETED, 6000L,
                LocalDateTime.of(2026, 10, 23, 10, 0));
        given(refundCommandService.request(MEMBER_ID, request))
                .willReturn(new RefundRequested(REFUND_ID, PORTONE_PAYMENT_ID, 6000L, "단순 변심"));
        given(paymentGateway.cancelPayment(PORTONE_PAYMENT_ID, "단순 변심", 6000L)).willReturn("cancellation-1");
        given(refundCommandService.complete(REFUND_ID, "cancellation-1")).willReturn(completed);

        RefundResponse response = refundFacade.request(MEMBER_ID, request);

        assertThat(response).isEqualTo(completed);
        InOrder inOrder = inOrder(refundCommandService, paymentGateway);
        inOrder.verify(refundCommandService).request(MEMBER_ID, request);
        inOrder.verify(paymentGateway).cancelPayment(PORTONE_PAYMENT_ID, "단순 변심", 6000L);
        inOrder.verify(refundCommandService).complete(REFUND_ID, "cancellation-1");
    }
}
