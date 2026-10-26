package org.example.takecareofmyfridgebe.domain.cooking.controller;

import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MostCookedRecipeResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeListResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeReviewResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeReviewStatus;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSearchRequest;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSortType;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSummaryResponse;
import org.example.takecareofmyfridgebe.domain.cooking.service.CookingService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.config.SecurityConfig;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MyRecipeController 웹 계층(MockMvc + SecurityConfig) 슬라이스 테스트.
 * 비로그인 401 차단, 쿼리 파라미터 바인딩 및 기본값 적용, 유효성 검증 실패(400) 및 응답 구조 직렬화를 확인한다.
 */
@WebMvcTest(MyRecipeController.class)
@Import({SecurityConfig.class, MyRecipeControllerTest.TestConfig.class})
class MyRecipeControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CookingService cookingService;

    private UsernamePasswordAuthenticationToken auth(Long memberId) {
        AuthSessionService.AuthPrincipal principal = new AuthSessionService.AuthPrincipal(
                memberId, 1L, Member.Role.USER);
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @BeforeEach
    void resetMock() {
        reset(cookingService);
    }

    /**
     * 비로그인(미인증) 상태로 내 레시피 목록을 요청하면
     * 서비스를 호출하지 않고 401 Unauthorized(A003) 에러를 반환하는지 검증한다.
     */
    @Test
    void unauthenticatedRequestReturnsUnauthorized() throws Exception {
        // When & Then: 인증 정보 없이 GET 요청 시 401 응답 및 서비스 미호출 확인
        mvc.perform(get("/api/me/recipes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        verifyNoInteractions(cookingService);
    }

    /**
     * 파라미터 없이 요청했을 때:
     * 1) 기본값(reviewStatus=ALL, sort=RECENT, page=1, size=10)이 서비스로 전달되는지
     * 2) 200 OK와 함께 summary 통계 및 recipes 페이징 필드가 규격대로 직렬화되는지 검증한다.
     */
    @Test
    void getMyRecipesWithDefaultParametersReturnsOk() throws Exception {
        // Given: 요약 통계와 레시피 목록 DTO 준비
        Long memberId = 7L;
        MostCookedRecipeResponse mostCooked = new MostCookedRecipeResponse(1L, "불닭 참치 두부조림", 3L);
        MyRecipeSummaryResponse summary = new MyRecipeSummaryResponse(2L, 4L, 1L, 1L, mostCooked, 7L);
        MyRecipeResponse recipe1 = new MyRecipeResponse(
                1L,
                "불닭 참치 두부조림",
                "https://image.example.com/tofu.jpg",
                3L,
                LocalDateTime.of(2026, 10, 12, 18, 30),
                true,
                new MyRecipeReviewResponse(101L, 5, "설탕 빼고 간장으로 바꾸니 성공!", LocalDateTime.of(2026, 10, 12, 19, 0))
        );
        MyRecipeResponse recipe2 = new MyRecipeResponse(
                2L,
                "계란말이",
                null,
                1L,
                LocalDateTime.of(2026, 10, 3, 12, 0),
                false,
                null
        );

        PageResponse<MyRecipeResponse> pageResponse = new PageResponse<>(
                List.of(recipe1, recipe2),
                1,
                10,
                2L,
                1,
                true
        );
        MyRecipeListResponse response = new MyRecipeListResponse(summary, pageResponse);

        when(cookingService.getMyRecipes(eq(memberId), any(MyRecipeSearchRequest.class)))
                .thenReturn(response);

        // When & Then: 인증 요청 및 응답 본문 검증
        mvc.perform(get("/api/me/recipes")
                        .with(authentication(auth(memberId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.summary.recipeCount").value(2))
                .andExpect(jsonPath("$.data.summary.totalCookCount").value(4))
                .andExpect(jsonPath("$.data.summary.writtenCount").value(1))
                .andExpect(jsonPath("$.data.summary.notWrittenCount").value(1))
                .andExpect(jsonPath("$.data.summary.mostCookedRecipe.recipeId").value(1))
                .andExpect(jsonPath("$.data.summary.mostCookedRecipe.recipeName").value("불닭 참치 두부조림"))
                .andExpect(jsonPath("$.data.summary.mostCookedRecipe.cookCount").value(3))
                .andExpect(jsonPath("$.data.summary.consumedIngredientCount").value(7))
                .andExpect(jsonPath("$.data.recipes.content[0].recipeId").value(1))
                .andExpect(jsonPath("$.data.recipes.content[0].recipeName").value("불닭 참치 두부조림"))
                .andExpect(jsonPath("$.data.recipes.content[0].recipeImageUrl").value("https://image.example.com/tofu.jpg"))
                .andExpect(jsonPath("$.data.recipes.content[0].cookCount").value(3))
                .andExpect(jsonPath("$.data.recipes.content[0].fromChat").value(true))
                .andExpect(jsonPath("$.data.recipes.content[0].review.id").value(101))
                .andExpect(jsonPath("$.data.recipes.content[0].review.rating").value(5))
                .andExpect(jsonPath("$.data.recipes.content[0].review.content").value("설탕 빼고 간장으로 바꾸니 성공!"))
                .andExpect(jsonPath("$.data.recipes.content[1].recipeId").value(2))
                .andExpect(jsonPath("$.data.recipes.content[1].recipeImageUrl").isEmpty())
                .andExpect(jsonPath("$.data.recipes.content[1].cookCount").value(1))
                .andExpect(jsonPath("$.data.recipes.content[1].fromChat").value(false))
                .andExpect(jsonPath("$.data.recipes.content[1].review").isEmpty())
                .andExpect(jsonPath("$.data.recipes.pageNumber").value(1))
                .andExpect(jsonPath("$.data.recipes.pageSize").value(10))
                .andExpect(jsonPath("$.data.recipes.totalElements").value(2))
                .andExpect(jsonPath("$.data.recipes.totalPages").value(1))
                .andExpect(jsonPath("$.data.recipes.isLast").value(true));

        // Then: 기본 파라미터가 적용된 Request로 서비스가 호출되었는지 확인
        verify(cookingService).getMyRecipes(eq(memberId), argThat(req ->
                req.reviewStatus() == MyRecipeReviewStatus.ALL &&
                req.sort() == MyRecipeSortType.RECENT &&
                req.page() == 1 &&
                req.size() == 10
        ));
    }

    /**
     * 커스텀 쿼리 파라미터(reviewStatus=WRITTEN, sort=MOST, page=2, size=5)가
     * DTO로 바인딩되어 서비스에 정확히 전달되는지 검증한다.
     */
    @Test
    void getMyRecipesWithCustomParametersPassesToService() throws Exception {
        // Given: 2페이지 조회 시 빈 페이지 응답 준비
        Long memberId = 7L;
        MyRecipeSummaryResponse summary = new MyRecipeSummaryResponse(10L, 20L, 6L, 4L, null, 0L);
        PageResponse<MyRecipeResponse> emptyPage = new PageResponse<>(List.of(), 2, 5, 6L, 2, true);
        when(cookingService.getMyRecipes(eq(memberId), any(MyRecipeSearchRequest.class)))
                .thenReturn(new MyRecipeListResponse(summary, emptyPage));

        // When & Then: 커스텀 파라미터로 GET 요청
        mvc.perform(get("/api/me/recipes")
                        .param("reviewStatus", "WRITTEN")
                        .param("sort", "MOST")
                        .param("page", "2")
                        .param("size", "5")
                        .with(authentication(auth(memberId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(cookingService).getMyRecipes(eq(memberId), argThat(req ->
                req.reviewStatus() == MyRecipeReviewStatus.WRITTEN &&
                req.sort() == MyRecipeSortType.MOST &&
                req.page() == 2 &&
                req.size() == 5
        ));
    }

    /**
     * page 번호가 1 미만(0)이면 유효성 검증 실패로 400 Bad Request를 반환하는지 검증한다.
     */
    @Test
    void getMyRecipesWithInvalidPageReturnsBadRequest() throws Exception {
        // When & Then: 0 페이지 요청 시 400 반환 및 서비스 미호출 확인
        mvc.perform(get("/api/me/recipes")
                        .param("page", "0")
                        .with(authentication(auth(7L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(cookingService);
    }

    /**
     * page size가 1 미만(0)이면 유효성 검증 실패로 400 Bad Request를 반환하는지 검증한다.
     */
    @Test
    void getMyRecipesWithZeroSizeReturnsBadRequest() throws Exception {
        // When & Then: size 0 요청 시 400 반환 및 서비스 미호출 확인
        mvc.perform(get("/api/me/recipes")
                        .param("size", "0")
                        .with(authentication(auth(7L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(cookingService);
    }

    /**
     * page size가 50을 초과(51)하면 유효성 검증 실패로 400 Bad Request를 반환하는지 검증한다.
     */
    @Test
    void getMyRecipesWithOverSizeReturnsBadRequest() throws Exception {
        // When & Then: size 51 요청 시 400 반환 및 서비스 미호출 확인
        mvc.perform(get("/api/me/recipes")
                        .param("size", "51")
                        .with(authentication(auth(7L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(cookingService);
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        CookingService cookingService() {
            return mock(CookingService.class);
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
