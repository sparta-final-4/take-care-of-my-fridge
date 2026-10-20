package org.example.takecareofmyfridgebe.domain.cooking.controller;

import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingCompleteRequest;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingCompleteResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.IngredientDeductionResponse;
import org.example.takecareofmyfridgebe.domain.cooking.facade.CookingFacade;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.config.SecurityConfig;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CookingLogController의 웹 계층(MockMvc + SecurityConfig) 슬라이스 테스트.
 * 요리 완료 요청 유효성 검증(recipeId·result 필수, memo 50자 이하, 중복 ingredientId 400 등),
 * 정상 처리 시 200 OK와 응답 포맷을 확인한다.
 */
@WebMvcTest(CookingLogController.class)
@Import({SecurityConfig.class, CookingLogControllerTest.TestConfig.class})
class CookingLogControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CookingFacade cookingFacade;

    private UsernamePasswordAuthenticationToken auth(Long memberId) {
        AuthSessionService.AuthPrincipal principal = new AuthSessionService.AuthPrincipal(
                memberId, 1L, Member.Role.USER);
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @BeforeEach
    void resetMock() {
        reset(cookingFacade);
    }

    /**
     * 유효한 요리 완료 요청 시 200 OK와 함께 CookingCompleteResponse를 반환하는지 검증한다.
     */
    @Test
    void completeCookingReturnsSuccessResponse() throws Exception {
        CookingCompleteResponse response = new CookingCompleteResponse(
                100L,
                List.of(new IngredientDeductionResponse(3L, new BigDecimal("1.00"), BigDecimal.ZERO))
        );

        when(cookingFacade.completeCooking(eq(7L), any(CookingCompleteRequest.class))).thenReturn(response);

        mvc.perform(post("/api/cooking-logs")
                        .with(authentication(auth(7L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipeId": 12,
                                  "result": "SUCCESS",
                                  "memo": "맛있어요",
                                  "chatSessionId": 5,
                                  "usedIngredients": [
                                    { "ingredientId": 3, "quantity": 1 }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cookingLogId").value(100L))
                .andExpect(jsonPath("$.data.deductions[0].ingredientId").value(3L));
    }

    /**
     * 중복된 ingredientId가 요청 본문에 포함되면 400 Bad Request(C001) 에러를 반환하는지 검증한다.
     */
    @Test
    void completeCookingWithDuplicateIngredientIdReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/cooking-logs")
                        .with(authentication(auth(7L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipeId": 12,
                                  "result": "SUCCESS",
                                  "usedIngredients": [
                                    { "ingredientId": 3, "quantity": 1 },
                                    { "ingredientId": 3, "quantity": 2 }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
    }

    /**
     * 메모가 50자를 초과하면 400 Bad Request를 반환하는지 검증한다.
     */
    @Test
    void completeCookingWithExcessiveMemoReturnsBadRequest() throws Exception {
        String longMemo = "a".repeat(51);

        mvc.perform(post("/api/cooking-logs")
                        .with(authentication(auth(7L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipeId": 12,
                                  "result": "SUCCESS",
                                  "memo": "%s"
                                }
                                """.formatted(longMemo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        CookingFacade cookingFacade() {
            return mock(CookingFacade.class);
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
