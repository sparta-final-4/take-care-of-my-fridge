package org.example.takecareofmyfridgebe.domain.cooking.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.cooking.dto.PopularRecipeRequest;
import org.example.takecareofmyfridgebe.domain.cooking.dto.PopularRecipeResponse;
import org.example.takecareofmyfridgebe.domain.cooking.service.PopularRecipeService;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 메인 화면용 인기 레시피 API 컨트롤러.
 * 최근 30일간의 요리 기록을 바탕으로 만든 회원 수 순 정렬된 인기 레시피를 반환한다.
 * 비로그인 사용자도 조회 가능하며, 기본 6건, 최대 20건까지 조회할 수 있다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recipes")
public class PopularRecipeController {

    private final PopularRecipeService popularRecipeService;

    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<List<PopularRecipeResponse>>> getPopularRecipes(
            @Valid @ModelAttribute PopularRecipeRequest request
    ) {
        List<PopularRecipeResponse> response = popularRecipeService.getPopularRecipes(request.size());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
