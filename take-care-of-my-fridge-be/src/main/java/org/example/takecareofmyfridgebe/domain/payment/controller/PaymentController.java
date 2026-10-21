package org.example.takecareofmyfridgebe.domain.payment.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService.AuthPrincipal;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentConfirmRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentConfirmResponse;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentPrepareResponse;
import org.example.takecareofmyfridgebe.domain.payment.facade.PaymentFacade;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentFacade paymentFacade;

    // 결제하기를 누를 때 호출한다. 같은 주문으로 다시 결제할 때도 호출하며, 그때마다 새 결제 ID를 받는다
    @PostMapping("/orders/{orderId}/payments")
    public ResponseEntity<ApiResponse<PaymentPrepareResponse>> prepare(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.ok(paymentFacade.preparePayment(principal.memberId(), orderId)));
    }

    @PostMapping("/payments/confirm")
    public ResponseEntity<ApiResponse<PaymentConfirmResponse>> confirm(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody PaymentConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(paymentFacade.confirmPayment(principal.memberId(), request)));
    }

    // 사용자가 결제창을 닫았을 때 호출한다. 실패 사유는 서버가 USER_CANCELLED로 정한다.
    @PostMapping("/payments/{portonePaymentId}/fail")
    public ResponseEntity<ApiResponse<Void>> fail(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable String portonePaymentId) {
        paymentFacade.failByUser(principal.memberId(), portonePaymentId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
