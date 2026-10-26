package org.example.takecareofmyfridgebe.domain.cooking.controller;

import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.cooking.dto.PopularRecipeResponse;
import org.example.takecareofmyfridgebe.domain.cooking.service.PopularRecipeService;
import org.example.takecareofmyfridgebe.global.config.SecurityConfig;
import org.example.takecareofmyfridgebe.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PopularRecipeController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, PopularRecipeControllerTest.TestConfig.class})
class PopularRecipeControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PopularRecipeService popularRecipeService;

    @BeforeEach
    void resetMock() {
        reset(popularRecipeService);
    }

    @Test
    @DisplayName("비로그인 상태에서 인기 레시피 기본 조회(size=6) 시 200 OK와 레시피 목록을 반환한다")
    void getPopularRecipesUnauthenticatedDefaultSizeReturnsOk() throws Exception {
        // Given
        PopularRecipeResponse recipeResponse = new PopularRecipeResponse(
                1L, "된장찌개", "image.jpg", "구수한 된장 팁", 5L, 4.8, 12L
        );
        when(popularRecipeService.getPopularRecipes(6)).thenReturn(List.of(recipeResponse));

        // When & Then
        mvc.perform(get("/api/recipes/popular"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].recipeId").value(1L))
                .andExpect(jsonPath("$.data[0].recipeName").value("된장찌개"))
                .andExpect(jsonPath("$.data[0].imageUrl").value("image.jpg"))
                .andExpect(jsonPath("$.data[0].tipSummary").value("구수한 된장 팁"))
                .andExpect(jsonPath("$.data[0].cookedMemberCount").value(5))
                .andExpect(jsonPath("$.data[0].averageRating").value(4.8))
                .andExpect(jsonPath("$.data[0].reviewCount").value(12));

        verify(popularRecipeService).getPopularRecipes(6);
    }

    @Test
    @DisplayName("size 파라미터가 지정되면 해당 size로 서비스를 호출한다")
    void getPopularRecipesWithSizeParameter() throws Exception {
        // Given
        when(popularRecipeService.getPopularRecipes(10)).thenReturn(List.of());

        // When & Then
        mvc.perform(get("/api/recipes/popular").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());

        verify(popularRecipeService).getPopularRecipes(10);
    }

    @Test
    @DisplayName("size가 최대 허용치(20)를 초과하거나 1 미만이면 400 Bad Request를 반환한다")
    void getPopularRecipesExceedingMaxSizeReturnsBadRequest() throws Exception {
        // When & Then: size=21
        mvc.perform(get("/api/recipes/popular").param("size", "21"))
                .andExpect(status().isBadRequest());

        // When & Then: size=0
        mvc.perform(get("/api/recipes/popular").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        PopularRecipeService popularRecipeService() {
            return mock(PopularRecipeService.class);
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
