package org.example.takecareofmyfridgebe.domain.review.controller;

import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewCreateRequest;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewResponse;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewSearchRequest;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewSortType;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewUpdateRequest;
import org.example.takecareofmyfridgebe.domain.review.service.RecipeReviewService;
import org.example.takecareofmyfridgebe.global.config.SecurityConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RecipeReviewController의 웹 계층(MockMvc + SecurityConfig) 슬라이스 테스트.
 * 비로그인 조회 허용 및 토큰 기반 사용자 식별, 파라미터 바인딩 기본값 적용,
 * DTO 유효성 검증(입력 범위, 문자 수 제한), 성공/오류 응답 형식을 확인한다.
 */
@WebMvcTest(RecipeReviewController.class)
@Import({SecurityConfig.class, RecipeReviewControllerTest.TestConfig.class})
class RecipeReviewControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RecipeReviewService recipeReviewService;

    private UsernamePasswordAuthenticationToken auth(Long memberId) {
        AuthSessionService.AuthPrincipal principal = new AuthSessionService.AuthPrincipal(
                memberId, 1L, Member.Role.USER);
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @BeforeEach
    void resetMock() {
        reset(recipeReviewService);
    }

    /**
     * 비로그인 상태에서도 후기 목록을 조회할 수 있으며,
     * 서비스에 전달되는 currentMemberId가 null인지 검증한다.
     */
    @Test
    void getReviewsPermittedWithoutAuthentication() throws Exception {
        // Given
        ReviewResponse review = new ReviewResponse(
                100L, 10L, "김치찌개", 5, "맛있어요", "닉네임", false, LocalDateTime.now(), LocalDateTime.now());
        PageResponse<ReviewResponse> pageResponse = PageResponse.of(
                new PageImpl<>(List.of(review), PageRequest.of(0, 10), 1));

        when(recipeReviewService.getReviews(any(ReviewSearchRequest.class), isNull()))
                .thenReturn(pageResponse);

        // When & Then
        mvc.perform(get("/api/reviews")
                        .param("recipeId", "10")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(100L))
                .andExpect(jsonPath("$.data.content[0].mine").value(false));
    }

    /**
     * 빈 쿼리 파라미터와 위조된 memberId(999) 파라미터를 보냈을 때:
     * 1) 회원 ID는 요청 파라미터(999)가 아닌 인증 토큰(7L)에서만 꺼내는지
     * 2) 빈 파라미터들이 기본값(sort=LATEST, page=1, size=10)으로 보정되는지
     * 3) 응답 JSON 규격대로 직렬화되는지 검증한다.
     */
    @Test
    void getReviewsWithEmptyQueryParamsUsesDefaultsAndTokenMemberId() throws Exception {
        // Given
        ReviewResponse review = new ReviewResponse(
                100L, 10L, "김치찌개", 5, "맛있어요", "닉네임", true, LocalDateTime.now(), LocalDateTime.now());
        PageResponse<ReviewResponse> pageResponse = PageResponse.of(
                new PageImpl<>(List.of(review), PageRequest.of(0, 10), 1));

        when(recipeReviewService.getReviews(
                argThat(req -> req.sort() == ReviewSortType.LATEST && req.page() == 1 && req.size() == 10),
                eq(7L)
        )).thenReturn(pageResponse);

        // When & Then
        mvc.perform(get("/api/reviews")
                        .param("sort", "")
                        .param("page", "")
                        .param("size", "")
                        .param("memberId", "999")
                        .with(authentication(auth(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(100L))
                .andExpect(jsonPath("$.data.content[0].content").value("맛있어요"));
    }

    /**
     * 정의되지 않은 정렬 기준(sort=ABC)을 전달하면
     * 바인딩 단계에서 거절되어 400 Bad Request(C001)를 반환하는지 검증한다.
     */
    @Test
    void getReviewsWithInvalidSortReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/reviews")
                        .param("sort", "ABC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
    }

    /**
     * 잘못된 페이지 번호 형식(page=abc)을 전달하면
     * 바인딩 단계에서 거절되어 400 Bad Request(C001)를 반환하는지 검증한다.
     */
    @Test
    void getReviewsWithInvalidPageReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/reviews")
                        .param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
    }

    /**
     * 허용 최대치를 초과한 페이지 크기(size=51)를 전달하면
     * 유효성 검증(@Max(50))에서 거절되어 400 Bad Request(C001)를 반환하는지 검증한다.
     */
    @Test
    void getReviewsWithExcessiveSizeReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/reviews")
                        .param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
    }

    /**
     * 후기 작성 시 필수 값(recipeId, rating, content)이 유효하면
     * 200 OK와 함께 작성된 ReviewResponse를 반환하는지 검증한다.
     */
    @Test
    void createReviewReturnsSuccess() throws Exception {
        // Given
        ReviewResponse review = new ReviewResponse(
                100L, 10L, "김치찌개", 5, "맛있어요", "닉네임", true, LocalDateTime.now(), LocalDateTime.now());

        when(recipeReviewService.createReview(eq(7L), any(ReviewCreateRequest.class)))
                .thenReturn(review);

        // When & Then
        mvc.perform(post("/api/reviews")
                        .with(authentication(auth(7L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipeId": 10,
                                  "rating": 5,
                                  "content": "맛있어요"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100L));
    }

    /**
     * 존재하지 않는 레시피에 후기를 작성하려고 하면 404 Not Found(RECIPE_001)를 반환하는지 검증한다.
     */
    @Test
    void createReviewWhenRecipeNotFoundReturns404() throws Exception {
        when(recipeReviewService.createReview(eq(7L), any(ReviewCreateRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.RECIPE_NOT_FOUND));

        mvc.perform(post("/api/reviews")
                        .with(authentication(auth(7L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipeId": 999,
                                  "rating": 5,
                                  "content": "맛있어요"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.RECIPE_NOT_FOUND.getCode()));
    }

    /**
     * 후기 작성 시 내용이 100자를 초과하거나 별점이 범위를 벗어나면
     * 서비스를 호출하지 않고 400 Bad Request 에러를 반환하는지 검증한다.
     */
    @Test
    void createReviewValidatesInput() throws Exception {
        // Given: 101자 내용 및 5 초과 별점
        String longContent = "a".repeat(101);

        // When & Then
        mvc.perform(post("/api/reviews")
                        .with(authentication(auth(7L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipeId": 10,
                                  "rating": 6,
                                  "content": "%s"
                                }
                                """.formatted(longContent)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * 후기 수정 요청 시 200 OK와 함께 수정된 ReviewResponse를 반환하는지 검증한다.
     */
    @Test
    void updateReviewReturnsSuccess() throws Exception {
        // Given
        ReviewResponse updated = new ReviewResponse(
                100L, 10L, "김치찌개", 4, "수정된 후기", "닉네임", true, LocalDateTime.now(), LocalDateTime.now());

        when(recipeReviewService.updateReview(eq(7L), eq(100L), any(ReviewUpdateRequest.class)))
                .thenReturn(updated);

        // When & Then
        mvc.perform(patch("/api/reviews/100")
                        .with(authentication(auth(7L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": 4,
                                  "content": "수정된 후기"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.rating").value(4))
                .andExpect(jsonPath("$.data.content").value("수정된 후기"));
    }

    /**
     * 후기 삭제 성공 시 200 OK와 성공 ApiResponse를 반환하는지 검증한다.
     */
    @Test
    void deleteReviewReturnsSuccess() throws Exception {
        // When & Then
        mvc.perform(delete("/api/reviews/100")
                        .with(authentication(auth(7L)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(recipeReviewService).deleteReview(7L, 100L);
    }

    /**
     * 타인의 후기를 삭제하려 할 때 서비스에서 REVIEW_NOT_FOUND 예외가 발생하면
     * 컨트롤러가 404 Not Found 에러를 반환하는지 검증한다.
     */
    @Test
    void deleteOthersReviewReturnsNotFound() throws Exception {
        // Given
        doThrow(new BusinessException(ErrorCode.REVIEW_NOT_FOUND))
                .when(recipeReviewService).deleteReview(7L, 999L);

        // When & Then
        mvc.perform(delete("/api/reviews/999")
                        .with(authentication(auth(7L)))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.REVIEW_NOT_FOUND.getCode()));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        RecipeReviewService recipeReviewService() {
            return mock(RecipeReviewService.class);
        }

        @Bean
        JwtTokenService jwtTokenService() {
            return mock(JwtTokenService.class);
        }

        @Bean
        AuthSessionService authSessionService() {
            return mock(AuthSessionService.class);
        }
    }
}
