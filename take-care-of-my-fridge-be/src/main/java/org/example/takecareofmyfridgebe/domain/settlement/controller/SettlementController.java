package org.example.takecareofmyfridgebe.domain.settlement.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SettlementResponse;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SettlementSearchRequest;
import org.example.takecareofmyfridgebe.domain.settlement.dto.TodaySalesResponse;
import org.example.takecareofmyfridgebe.domain.settlement.facade.SettlementQueryFacade;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
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

    @GetMapping("/today")
    public ResponseEntity<ApiResponse<TodaySalesResponse>> getTodaySales() {
        return ResponseEntity.ok(ApiResponse.ok(settlementQueryFacade.getTodaySales()));
    }
}
