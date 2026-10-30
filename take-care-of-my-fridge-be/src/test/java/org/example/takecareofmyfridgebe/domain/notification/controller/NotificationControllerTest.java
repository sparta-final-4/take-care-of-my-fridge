package org.example.takecareofmyfridgebe.domain.notification.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.notification.service.NotificationService;
import org.example.takecareofmyfridgebe.global.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, NotificationControllerTest.TestConfig.class})
class NotificationControllerTest {

    private final MockMvc mvc;
    private final NotificationService notificationService;

    @Autowired
    NotificationControllerTest(MockMvc mvc, NotificationService notificationService) {
        this.mvc = mvc;
        this.notificationService = notificationService;
    }

    @BeforeEach
    void resetMock() {
        reset(notificationService);
    }

    @Test
    @DisplayName("기준 알림 ID가 0이면 400이다")
    void getMyNotifications_rejectsZeroBeforeId() throws Exception {
        mvc.perform(get("/api/notifications")
                        .param("beforeId", "0")
                        .with(loginAsMember7()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("C001"));

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("기준 알림 ID가 숫자가 아니면 400이다")
    void getMyNotifications_rejectsNonNumericBeforeId() throws Exception {
        mvc.perform(get("/api/notifications")
                        .param("beforeId", "a")
                        .with(loginAsMember7()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("C001"));

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("전부 읽음에 마지막 알림 ID가 없으면 400이다")
    void readAll_rejectsMissingLastId() throws Exception {
        mvc.perform(patch("/api/notifications/read-all")
                        .with(csrf())
                        .with(loginAsMember7())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("C001"));

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("전부 읽음에 마지막 알림 ID가 0이면 400이다")
    void readAll_rejectsZeroLastId() throws Exception {
        mvc.perform(patch("/api/notifications/read-all")
                        .with(csrf())
                        .with(loginAsMember7())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastNotificationId\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("C001"));

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("로그인하지 않으면 다섯 API 모두 401이다")
    void rejectsAnonymous() throws Exception {
        mvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications/unread-count"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/notifications/read-all")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastNotificationId\":1}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/notifications/1").with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/notifications").with(csrf()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(notificationService);
    }

    private RequestPostProcessor loginAsMember7() {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthSessionService.AuthPrincipal(7L, 8L, Member.Role.USER), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        NotificationService notificationService() {
            return mock(NotificationService.class);
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
