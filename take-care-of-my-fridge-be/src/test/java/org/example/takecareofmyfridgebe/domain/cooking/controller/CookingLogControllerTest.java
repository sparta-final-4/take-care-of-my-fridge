package org.example.takecareofmyfridgebe.domain.cooking.controller;

import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingCompleteRequest;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingCompleteResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingLogResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingLogUpdateRequest;
import org.example.takecareofmyfridgebe.domain.cooking.dto.IngredientDeductionResponse;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingResult;
import org.example.takecareofmyfridgebe.domain.cooking.facade.CookingFacade;
import org.example.takecareofmyfridgebe.domain.cooking.service.CookingService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CookingLogController의 웹 계층(MockMvc + SecurityConfig) 슬라이스 테스트.
 * 요리 완료 요청 유효성 검증(recipeId·result 필수, memo 50자 이하, 중복 ingredientId 400 등),
 * 요리 기록 수정(PATCH) 유효성 검증, 401/404 예외 처리,
 * 정상 처리 시 200 OK와 응답 포맷을 확인한다.
 */
@WebMvcTest(CookingLogController.class)
@Import({SecurityConfig.class, CookingLogControllerTest.TestConfig.class})
class CookingLogControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CookingFacade cookingFacade;

    @Autowired
    private CookingService cookingService;

    private UsernamePasswordAuthenticationToken auth(Long memberId) {
        AuthSessionService.AuthPrincipal principal = new AuthSessionService.AuthPrincipal(
                memberId, 1L, Member.Role.USER);
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @BeforeEach
    void resetMock() {
        reset(cookingFacade, cookingService);
    }

    /**
     * 비로그인(미인증) 상태로 요리 기록 목록을 요청하면
     * 서비스를 호출하지 않고 401 Unauthorized(A003) 에러를 반환하는지 검증한다.
     */
    @Test
    void unauthenticatedRequestReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/cooking-logs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.getCode()));

        verifyNoInteractions(cookingService);
    }

    /**
     * 빈 쿼리 파라미터와 위조된 memberId(999) 파라미터를 보냈을 때:
     * 1) 회원 ID는 요청 파라미터가 아닌 인증 토큰(7L)에서만 꺼내는지
     * 2) 빈 파라미터들이 기본값(page=1, size=20)으로 보정되는지
     * 3) 응답 JSON 필드가 ISO 날짜 포맷 규격대로 직렬화되는지 검증한다.
     */
    @Test
    void getCookingLogsWithEmptyQueryParamsUsesDefaultsAndTokenMemberId() throws Exception {
        // Given
        CookingLogResponse response = new CookingLogResponse(
                100L, 12L, CookingResult.SUCCESS, "맛있어요", true,
                LocalDateTime.of(2026, 10, 19, 20, 13, 49)
        );
        PageResponse<CookingLogResponse> pageResponse = PageResponse.of(
                new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1)
        );

        when(cookingService.getCookingLogs(
                eq(7L),
                argThat(req -> req.recipeId() == null && req.from() == null && req.to() == null
                        && req.page() == 1 && req.size() == 20)
        )).thenReturn(pageResponse);

        // When & Then
        mvc.perform(get("/api/cooking-logs")
                        .param("recipeId", "")
                        .param("from", "")
                        .param("to", "")
                        .param("page", "")
                        .param("size", "")
                        .param("memberId", "999")
                        .with(authentication(auth(7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(100L))
                .andExpect(jsonPath("$.data.content[0].recipeId").value(12L))
                .andExpect(jsonPath("$.data.content[0].result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content[0].fromChat").value(true))
                .andExpect(jsonPath("$.data.content[0].createdAt").value("2026-10-19T20:13:49"));
    }

    /**
     * 허용 최대치를 초과한 페이지 크기(size=101)를 전달하면
     * 400 Bad Request(C001)를 반환하는지 검증한다.
     */
    @Test
    void getCookingLogsWithExcessiveSizeReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/cooking-logs")
                        .param("size", "101")
                        .with(authentication(auth(7L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));

        verifyNoInteractions(cookingService);
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
     * 존재하지 않는 레시피로 요리 완료 요청 시 404 Not Found(RECIPE_001)를 반환하는지 검증한다.
     */
    @Test
    void completeCookingWhenRecipeNotFoundReturns404() throws Exception {
        when(cookingFacade.completeCooking(eq(7L), any(CookingCompleteRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.RECIPE_NOT_FOUND));

        mvc.perform(post("/api/cooking-logs")
                        .with(authentication(auth(7L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipeId": 999,
                                  "result": "SUCCESS"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.RECIPE_NOT_FOUND.getCode()));
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

    /**
     * 요리 기록 수정 정상 요청 시 200 OK와 수정된 정보를 반환하는지 검증한다.
     */
    @Test
    void updateCookingLogSuccessReturnsOk() throws Exception {
        Long memberId = 7L;
        Long cookingLogId = 10L;
        CookingLogResponse response = new CookingLogResponse(
                cookingLogId, 12L, CookingResult.SUCCESS, "수정된 메모", false, LocalDateTime.now());

        when(cookingService.updateCookingLog(eq(memberId), eq(cookingLogId), any(CookingLogUpdateRequest.class)))
                .thenReturn(response);

        mvc.perform(patch("/api/cooking-logs/{cookingLogId}", cookingLogId)
                        .with(authentication(auth(memberId)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "result": "SUCCESS",
                                  "memo": "수정된 메모"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(cookingLogId))
                .andExpect(jsonPath("$.data.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.memo").value("수정된 메모"));
    }

    /**
     * 메모를 null로 보내어 메모를 지우는 경우에도 200 OK를 반환하는지 검증한다.
     */
    @Test
    void updateCookingLogWithNullMemoReturnsOk() throws Exception {
        Long memberId = 7L;
        Long cookingLogId = 10L;
        CookingLogResponse response = new CookingLogResponse(
                cookingLogId, 12L, CookingResult.FAILED, null, false, LocalDateTime.now());

        when(cookingService.updateCookingLog(eq(memberId), eq(cookingLogId), any(CookingLogUpdateRequest.class)))
                .thenReturn(response);

        mvc.perform(patch("/api/cooking-logs/{cookingLogId}", cookingLogId)
                        .with(authentication(auth(memberId)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "result": "FAILED",
                                  "memo": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.result").value("FAILED"))
                .andExpect(jsonPath("$.data.memo").isEmpty());
    }

    /**
     * 요리 결과(result)가 null이면 400 Bad Request를 반환하는지 검증한다.
     */
    @Test
    void updateCookingLogWithNullResultReturnsBadRequest() throws Exception {
        mvc.perform(patch("/api/cooking-logs/10")
                        .with(authentication(auth(7L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "result": null,
                                  "memo": "메모"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * 메모가 50자를 초과하면 400 Bad Request를 반환하는지 검증한다.
     */
    @Test
    void updateCookingLogWithExcessiveMemoReturnsBadRequest() throws Exception {
        String longMemo = "a".repeat(51);

        mvc.perform(patch("/api/cooking-logs/10")
                        .with(authentication(auth(7L)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "result": "SUCCESS",
                                  "memo": "%s"
                                }
                                """.formatted(longMemo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    /**
     * 미인증 상태로 요리 기록 수정을 요청하면 401 Unauthorized를 반환하는지 검증한다.
     */
    @Test
    void updateCookingLogUnauthenticatedReturnsUnauthorized() throws Exception {
        mvc.perform(patch("/api/cooking-logs/10")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "result": "SUCCESS",
                                  "memo": "메모"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.UNAUTHORIZED.getCode()));
    }

    /**
     * 본인 기록이 아니거나 존재하지 않는 요리 기록이면 404 Not Found(COOKING_001)를 반환하는지 검증한다.
     */
    @Test
    void updateCookingLogNotFoundReturnsNotFound() throws Exception {
        Long memberId = 7L;
        Long cookingLogId = 999L;

        when(cookingService.updateCookingLog(eq(memberId), eq(cookingLogId), any(CookingLogUpdateRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.COOKING_LOG_NOT_FOUND));

        mvc.perform(patch("/api/cooking-logs/{cookingLogId}", cookingLogId)
                        .with(authentication(auth(memberId)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "result": "SUCCESS",
                                  "memo": "메모"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.COOKING_LOG_NOT_FOUND.getCode()));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        CookingFacade cookingFacade() {
            return mock(CookingFacade.class);
        }

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
