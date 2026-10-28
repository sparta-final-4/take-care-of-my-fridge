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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private ReviewSummaryAdminController controller;

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

    @Test
    @DisplayName("수동 실행이 끝난 직후 다시 실행하면 200 OK를 반환하고 lockAtLeastFor는 0이다")
    @SuppressWarnings("unchecked")
    void canExecuteAgainImmediatelyAfterManualExecutionFinishes() throws Throwable {
        ReviewSummaryResult dummyResult1 = new ReviewSummaryResult(5, 5, 0, 0, 500L);
        ReviewSummaryResult dummyResult2 = new ReviewSummaryResult(3, 3, 0, 0, 300L);

        LockingTaskExecutor.TaskResult<ReviewSummaryResult> firstTaskResult = mock(LockingTaskExecutor.TaskResult.class);
        when(firstTaskResult.wasExecuted()).thenReturn(true);
        when(firstTaskResult.getResult()).thenReturn(dummyResult1);

        LockingTaskExecutor.TaskResult<ReviewSummaryResult> secondTaskResult = mock(LockingTaskExecutor.TaskResult.class);
        when(secondTaskResult.wasExecuted()).thenReturn(true);
        when(secondTaskResult.getResult()).thenReturn(dummyResult2);

        ArgumentCaptor<LockConfiguration> lockConfigCaptor = ArgumentCaptor.forClass(LockConfiguration.class);

        when(lockingTaskExecutor.executeWithLock(any(LockingTaskExecutor.TaskWithResult.class), lockConfigCaptor.capture()))
                .thenReturn(firstTaskResult, secondTaskResult);

        // 첫 번째 수동 실행 -> 200 OK
        mvc.perform(post("/api/admin/review-summaries/run")
                        .with(csrf())
                        .with(authentication(authenticationFor(Member.Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.targetCount").value(5));

        // 직후 다시 실행 -> 200 OK
        mvc.perform(post("/api/admin/review-summaries/run")
                        .with(csrf())
                        .with(authentication(authenticationFor(Member.Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.targetCount").value(3));

        List<LockConfiguration> configs = lockConfigCaptor.getAllValues();
        assertThat(configs).hasSize(2);
        assertThat(configs.get(0).getLockAtLeastFor()).isEqualTo(Duration.ZERO);
        assertThat(configs.get(1).getLockAtLeastFor()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("잠금 실행 중 RuntimeException이 발생하면 500 Internal Server Error를 반환한다")
    @SuppressWarnings("unchecked")
    void returns500WhenRuntimeExceptionOccurs() throws Throwable {
        when(lockingTaskExecutor.executeWithLock(any(LockingTaskExecutor.TaskWithResult.class), any(LockConfiguration.class)))
                .thenThrow(new RuntimeException("Redis 분산 락 통신 실패"));

        mvc.perform(post("/api/admin/review-summaries/run")
                        .with(csrf())
                        .with(authentication(authenticationFor(Member.Role.ADMIN))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_002"));
    }

    @Test
    @DisplayName("잠금 실행 중 Error가 발생하면 잡아서 500 응답으로 바꾸지 않고 그대로 던져진다")
    @SuppressWarnings("unchecked")
    void rethrowsErrorWhenErrorOccurs() throws Throwable {
        OutOfMemoryError expectedError = new OutOfMemoryError("자바 힙 메모리 부족");
        when(lockingTaskExecutor.executeWithLock(any(LockingTaskExecutor.TaskWithResult.class), any(LockConfiguration.class)))
                .thenThrow(expectedError);

        // 1. MockMvc 호출 시 Handler dispatch failed로 감싸져서 서블릿까지 Error가 그대로 전달되는지 확인
        MvcResult mvcResult = mvc.perform(post("/api/admin/review-summaries/run")
                        .with(csrf())
                        .with(authentication(authenticationFor(Member.Role.ADMIN))))
                .andReturn();

        assertThat(mvcResult.getResolvedException())
                .isNotNull()
                .hasRootCause(expectedError);

        // 2. 컨트롤러 메소드 직접 호출 시에도 잡히지 않고 Error가 그대로 던져지는지 확인
        assertThatThrownBy(() -> controller.runReviewSummary())
                .isSameAs(expectedError);
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
