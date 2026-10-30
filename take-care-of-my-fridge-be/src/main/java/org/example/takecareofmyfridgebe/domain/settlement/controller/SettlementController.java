package org.example.takecareofmyfridgebe.domain.settlement.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SettlementPaymentResponse;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SettlementPaymentSearchRequest;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SettlementResponse;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SettlementSearchRequest;
import org.example.takecareofmyfridgebe.domain.settlement.dto.TodaySalesResponse;
import org.example.takecareofmyfridgebe.domain.settlement.facade.SettlementQueryFacade;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 관리자 정산 화면. /api/admin/** 은 보안 설정에서 ADMIN만 접근할 수 있다
@RestController
@RequestMapping("/api/admin/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementQueryFacade settlementQueryFacade;

    @GetMapping
    public ResponseEntity<ApiResponse<SettlementResponse>> getSettlement(
            @Valid @ModelAttribute SettlementSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(settlementQueryFacade.getSettlement(request)));
    }

    // 결제 한 건씩. 금액은 조회 기간 안에서 일어난 결제·환불만 세므로 모든 행을 더하면 정산 조회 합계와 같다
    @GetMapping("/payments")
    public ResponseEntity<ApiResponse<PageResponse<SettlementPaymentResponse>>> getPayments(
            @Valid @ModelAttribute SettlementPaymentSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(settlementQueryFacade.getPayments(request)));
    }

    @GetMapping("/today")
    public ResponseEntity<ApiResponse<TodaySalesResponse>> getTodaySales() {
        return ResponseEntity.ok(ApiResponse.ok(settlementQueryFacade.getTodaySales()));
    }
}
