package org.example.takecareofmyfridgebe.domain.refund.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService.AuthPrincipal;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundCreateRequest;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundResponse;
import org.example.takecareofmyfridgebe.domain.refund.facade.RefundFacade;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundFacade refundFacade;

    // 환불 신청 화면의 환불하기. 신청 즉시 PG 취소까지 마친 결과를 돌려준다
    @PostMapping
    public ResponseEntity<ApiResponse<RefundResponse>> request(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody RefundCreateRequest request) {
        RefundResponse response = refundFacade.request(principal.memberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }
}
