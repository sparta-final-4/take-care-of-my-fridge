package org.example.takecareofmyfridgebe.domain.cooking.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService.AuthPrincipal;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingCompleteRequest;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingCompleteResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingLogResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingLogSearchRequest;
import org.example.takecareofmyfridgebe.domain.cooking.facade.CookingFacade;
import org.example.takecareofmyfridgebe.domain.cooking.service.CookingService;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cooking-logs")
public class CookingLogController {

    private final CookingFacade cookingFacade;
    private final CookingService cookingService;

    /**
     * 로그인한 회원의 요리 기록을 레시피 및 기간 조건으로 최신순 페이지 조회한다.
     *
     * @param principal 인증된 요청자의 회원 정보
     * @param request   조회 조건 (recipeId, from, to, page, size)
     * @return 조건에 맞는 요리 기록 페이지 응답
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CookingLogResponse>>> getCookingLogs(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @ModelAttribute CookingLogSearchRequest request
    ) {
        PageResponse<CookingLogResponse> response = cookingService.getCookingLogs(principal.memberId(), request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 요리 완료를 기록하고 냉장고 재료를 차감하며, 필요한 경우 상담 세션을 종료한다.
     *
     * @param principal 인증된 요청자의 회원 정보
     * @param request   요리 완료 요청 (recipeId, result, memo, chatSessionId, usedIngredients)
     * @return 요리 기록 ID 및 재료별 차감/잔여량 응답
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CookingCompleteResponse>> completeCooking(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CookingCompleteRequest request
    ) {
        CookingCompleteResponse response = cookingFacade.completeCooking(principal.memberId(), request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
