package org.example.takecareofmyfridgebe.domain.payment.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService.AuthPrincipal;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentPinChangeRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentPinResetRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentPinSetRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentPinStatusResponse;
import org.example.takecareofmyfridgebe.domain.payment.facade.PaymentPinFacade;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentPinService;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment-pin")
@RequiredArgsConstructor
public class PaymentPinController {

    private final PaymentPinService paymentPinService;
    private final PaymentPinFacade paymentPinFacade;

    // 등록 카드로 결제하기 전에 비밀번호 입력창을 띄울지, 설정창을 띄울지 정할 때 쓴다
    @GetMapping
    public ResponseEntity<ApiResponse<PaymentPinStatusResponse>> getStatus(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(paymentPinService.getStatus(principal.memberId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> set(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody PaymentPinSetRequest request) {
        paymentPinService.set(principal.memberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok());
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<Void>> change(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody PaymentPinChangeRequest request) {
        paymentPinService.change(principal.memberId(), request);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // 로그인 비밀번호를 30분 안에 5번 틀리면 잠시 막는다
    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<Void>> reset(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody PaymentPinResetRequest request) {
        paymentPinFacade.reset(principal.memberId(), request);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
