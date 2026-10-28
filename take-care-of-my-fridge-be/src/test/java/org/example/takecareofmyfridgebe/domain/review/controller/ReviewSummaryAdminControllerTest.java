package org.example.takecareofmyfridgebe.domain.review.controller;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewSummaryResult;
import org.example.takecareofmyfridgebe.domain.review.facade.ReviewSummaryFacade;
import org.example.takecareofmyfridgebe.global.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewSummaryAdminController.class)
@Import({SecurityConfig.class, ReviewSummaryAdminControllerTest.TestConfig.class, ReviewSummaryAdminController.class})
class ReviewSummaryAdminControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private LockingTaskExecutor lockingTaskExecutor;

    @Autowired
    private ReviewSummaryFacade reviewSummaryFacade;

    @Test
    @DisplayName("비로그인 사용자가 실행 요청 시 401 Unauthorized를 반환한다")
    void anonymousRequestReturns401() throws Exception {
        mvc.perform(post("/api/admin/review-summaries/run")
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("A003"));
    }

    @Test
    @DisplayName("일반 회원(USER)이 실행 요청 시 403 Forbidden을 반환한다")
    void userRequestReturns403() throws Exception {
        mvc.perform(post("/api/admin/review-summaries/run")
                        .with(csrf())
                        .with(authentication(authenticationFor(Member.Role.USER))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("A004"));
    }

    @Test
    @DisplayName("관리자(ADMIN)가 실행 요청 시 200 OK와 작업 결과를 반환한다")
    @SuppressWarnings("unchecked")
    void adminRequestReturns200WithResult() throws Throwable {
        ReviewSummaryResult dummyResult = new ReviewSummaryResult(10, 8, 1, 1, 1200L);
        LockingTaskExecutor.TaskResult<ReviewSummaryResult> taskResult = mock(LockingTaskExecutor.TaskResult.class);
        when(taskResult.wasExecuted()).thenReturn(true);
        when(taskResult.getResult()).thenReturn(dummyResult);

        when(lockingTaskExecutor.executeWithLock(any(LockingTaskExecutor.TaskWithResult.class), any(LockConfiguration.class)))
                .thenReturn(taskResult);

        mvc.perform(post("/api/admin/review-summaries/run")
                        .with(csrf())
                        .with(authentication(authenticationFor(Member.Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.targetCount").value(10))
                .andExpect(jsonPath("$.data.successCount").value(8))
                .andExpect(jsonPath("$.data.skippedCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(1));
    }

    @Test
    @DisplayName("이미 잠금이 잡혀 있으면 409 Conflict(REVIEW_SUMMARY_RUNNING)를 반환하고 요약을 실행하지 않는다")
    @SuppressWarnings("unchecked")
    void returns409WhenLockAlreadyHeld() throws Throwable {
        LockingTaskExecutor.TaskResult<ReviewSummaryResult> notExecutedResult = mock(LockingTaskExecutor.TaskResult.class);
        when(notExecutedResult.wasExecuted()).thenReturn(false);

        when(lockingTaskExecutor.executeWithLock(any(LockingTaskExecutor.TaskWithResult.class), any(LockConfiguration.class)))
                .thenReturn(notExecutedResult);

        mvc.perform(post("/api/admin/review-summaries/run")
                        .with(csrf())
                        .with(authentication(authenticationFor(Member.Role.ADMIN))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("REVIEW_004"))
                .andExpect(jsonPath("$.error.message").value("후기 요약이 이미 실행 중이에요."));

        verify(reviewSummaryFacade, never()).runOnce();
    }

    private UsernamePasswordAuthenticationToken authenticationFor(Member.Role role) {
        return new UsernamePasswordAuthenticationToken(
                new AuthSessionService.AuthPrincipal(1L, 2L, role),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        JwtTokenService jwtTokenService() {
            return mock(JwtTokenService.class);
        }

        @Bean
        AuthSessionService authSessionService() {
            return mock(AuthSessionService.class);
        }

        @Bean
        LockingTaskExecutor lockingTaskExecutor() {
            return mock(LockingTaskExecutor.class);
        }

        @Bean
        ReviewSummaryFacade reviewSummaryFacade() {
            return mock(ReviewSummaryFacade.class);
        }
    }
}
