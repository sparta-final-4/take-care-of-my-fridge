package org.example.takecareofmyfridgebe.domain.cooking.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService.AuthPrincipal;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeListResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSearchRequest;
import org.example.takecareofmyfridgebe.domain.cooking.service.CookingService;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me/recipes")
public class MyRecipeController {

    private final CookingService cookingService;

    /**
     * 로그인한 회원이 요리 완료한 레시피를 레시피별로 묶어 페이징 목록과 전체 요약 통계를 조회한다.
     *
     * @param principal 인증된 요청자의 회원 정보
     * @param request   조회 조건 (reviewStatus, sort, page, size)
     * @return 전체 요약 통계와 레시피 목록 페이징 응답
     */
    @GetMapping
    public ResponseEntity<ApiResponse<MyRecipeListResponse>> getMyRecipes(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @ModelAttribute MyRecipeSearchRequest request
    ) {
        MyRecipeListResponse response = cookingService.getMyRecipes(principal.memberId(), request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
