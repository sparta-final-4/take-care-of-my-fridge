package org.example.takecareofmyfridgebe.domain.refund.facade;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.payment.port.PaymentGateway;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundCreateRequest;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundRequested;
import org.example.takecareofmyfridgebe.domain.refund.service.RefundCommandService;
import org.springframework.stereotype.Component;

// PG 취소 같은 외부 호출은 트랜잭션 밖에서 하고, 상태 변경은 RefundCommandService에 맡긴다
@Component
@RequiredArgsConstructor
public class RefundFacade {

    private final RefundCommandService refundCommandService;
    private final PaymentGateway paymentGateway;

    // 환불을 요청 상태로 저장한 뒤 PG에서 환불 금액만큼 부분 취소하고, 성공하면 완료 처리한다.
    // TODO: PG 취소 실패·결과를 모르는 경우 처리. 지금은 예외가 그대로 올라가고 환불은 요청 상태로 남는다
    public Long request(Long memberId, Long orderId, RefundCreateRequest request) {
        RefundRequested refund = refundCommandService.request(memberId, orderId, request);
        String cancellationId = paymentGateway.cancelPayment(
                refund.portonePaymentId(), refund.reason(), refund.refundAmount());
        refundCommandService.complete(refund.refundId(), cancellationId);
        return refund.refundId();
    }
}
