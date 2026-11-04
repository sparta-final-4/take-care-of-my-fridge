package org.example.takecareofmyfridgebe.domain.payment.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService.AuthPrincipal;
import org.example.takecareofmyfridgebe.domain.payment.dto.BillingKeyPrepareResponse;
import org.example.takecareofmyfridgebe.domain.payment.dto.BillingKeyRegisterRequest;
import org.example.takecareofmyfridgebe.domain.payment.dto.BillingKeyResponse;
import org.example.takecareofmyfridgebe.domain.payment.facade.BillingKeyFacade;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/billing-keys")
@RequiredArgsConstructor
public class BillingKeyController {

    private final BillingKeyFacade billingKeyFacade;

    // 카드 등록을 누를 때 호출한다. 카드가 5장이면 거절한다
    @PostMapping("/prepare")
    public ResponseEntity<ApiResponse<BillingKeyPrepareResponse>> prepare(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(billingKeyFacade.prepare(principal.memberId())));
    }

    // 카드 등록 창이 성공으로 끝나면 받은 빌링키로 호출한다
    @PostMapping
    public ResponseEntity<ApiResponse<BillingKeyResponse>> register(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody BillingKeyRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(billingKeyFacade.register(principal.memberId(), request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BillingKeyResponse>>> getCards(
            @AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(billingKeyFacade.getCards(principal.memberId())));
    }
}
