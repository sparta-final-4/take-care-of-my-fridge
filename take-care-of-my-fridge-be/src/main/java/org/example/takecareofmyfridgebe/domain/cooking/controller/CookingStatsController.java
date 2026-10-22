package org.example.takecareofmyfridgebe.domain.cooking.controller;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingStatsResponse;
import org.example.takecareofmyfridgebe.domain.cooking.service.CookingService;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recipes")
public class CookingStatsController {

    private final CookingService cookingService;

    /**
     * 레시피 상세 화면에서 사용하는 요리 통계(총 요리 횟수, 성공 횟수, 실패 횟수)를 조회한다.
     * 로그인 없이 호출 가능하며, 요리 기록이 없는 경우 모든 카운트가 0으로 반환된다.
     *
     * @param recipeId 레시피 ID
     * @return 레시피 요리 통계 응답
     */
    @GetMapping("/{recipeId}/cooking-stats")
    public ResponseEntity<ApiResponse<CookingStatsResponse>> getCookingStats(
            @PathVariable Long recipeId
    ) {
        CookingStatsResponse response = cookingService.getCookingStats(recipeId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
