package org.example.takecareofmyfridgebe.domain.cooking.controller;

import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingStatsResponse;
import org.example.takecareofmyfridgebe.domain.cooking.service.CookingService;
import org.example.takecareofmyfridgebe.global.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CookingStatsController의 웹 계층(MockMvc + SecurityConfig) 슬라이스 테스트.
 * 비로그인(미인증) 상태에서도 호출 가능한지(/api/recipes/** permitAll),
 * 정상 통계 반환 시 200 OK와 응답 포맷, 요리 기록이 없을 때 0 반환을 확인한다.
 */
@WebMvcTest(CookingStatsController.class)
@Import({SecurityConfig.class, CookingStatsControllerTest.TestConfig.class})
class CookingStatsControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CookingService cookingService;

    @BeforeEach
    void resetMock() {
        reset(cookingService);
    }

    /**
     * 비로그인(미인증) 상태로 레시피 요리 통계를 요청해도
     * 401 Unauthorized가 아닌 200 OK와 통계 데이터를 반환하는지 검증한다.
     */
    @Test
    void getCookingStatsUnauthenticatedReturnsOk() throws Exception {
        // Given
        Long recipeId = 12L;
        CookingStatsResponse response = new CookingStatsResponse(recipeId, 10L, 8L, 2L);

        when(cookingService.getCookingStats(recipeId)).thenReturn(response);

        // When & Then: 인증 없이 GET 요청 시 200 OK 및 응답 구조 검증
        mvc.perform(get("/api/recipes/{recipeId}/cooking-stats", recipeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recipeId").value(recipeId))
                .andExpect(jsonPath("$.data.totalCount").value(10))
                .andExpect(jsonPath("$.data.successCount").value(8))
                .andExpect(jsonPath("$.data.failedCount").value(2));

        verify(cookingService).getCookingStats(recipeId);
    }

    /**
     * 요리 기록이 없는 레시피를 조회할 때
     * 404가 아닌 200 OK와 0으로 채워진 통계를 반환하는지 검증한다.
     */
    @Test
    void getCookingStatsWithNoLogsReturnsZeros() throws Exception {
        // Given
        Long recipeId = 99L;
        CookingStatsResponse response = new CookingStatsResponse(recipeId, 0L, 0L, 0L);

        when(cookingService.getCookingStats(recipeId)).thenReturn(response);

        // When & Then: 기록 없는 레시피 요청 시 200 OK 및 0 카운트 확인
        mvc.perform(get("/api/recipes/{recipeId}/cooking-stats", recipeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.recipeId").value(recipeId))
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.successCount").value(0))
                .andExpect(jsonPath("$.data.failedCount").value(0));

        verify(cookingService).getCookingStats(recipeId);
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
