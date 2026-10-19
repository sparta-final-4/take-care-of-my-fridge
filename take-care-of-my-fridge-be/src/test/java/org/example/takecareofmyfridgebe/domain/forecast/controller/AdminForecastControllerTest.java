package org.example.takecareofmyfridgebe.domain.forecast.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.forecast.dto.ForecastResponse;
import org.example.takecareofmyfridgebe.domain.forecast.service.ForecastReadService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.config.SecurityConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
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

@WebMvcTest(AdminForecastController.class)
@Import({SecurityConfig.class, AdminForecastControllerTest.TestConfig.class})
class AdminForecastControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ForecastReadService service;

    @BeforeEach
    void resetMock() {
        reset(service);
    }

    @Test
    void adminReadsRequestedWeek() throws Exception {
        LocalDate week = LocalDate.of(2026, 10, 19);
        when(service.getByWeek(week)).thenReturn(new ForecastResponse(
                week, week.plusDays(6), "weekly-mlp-v1",
                LocalDateTime.of(2026, 10, 18, 18, 0),
                LocalDateTime.of(2026, 10, 18, 22, 0),
                false, null, false, null, null, null, List.of()));

        mvc.perform(get("/api/admin/forecasts/2026-10-19")
                        .with(authenticationFor(Member.Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weekStart").value("2026-10-19"))
                .andExpect(jsonPath("$.data.confirmed").value(false));
    }

    @Test
    void rejectsInvalidDateAndNonMonday() throws Exception {
        mvc.perform(get("/api/admin/forecasts/bad")
                        .with(authenticationFor(Member.Role.ADMIN)))
                .andExpect(status().isBadRequest());
        when(service.getByWeek(LocalDate.of(2026, 10, 20)))
                .thenThrow(new IllegalArgumentException("weekStart는 월요일이어야 합니다."));
        mvc.perform(get("/api/admin/forecasts/2026-10-20")
                        .with(authenticationFor(Member.Role.ADMIN)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingWeekReturnsF001() throws Exception {
        when(service.getByWeek(LocalDate.of(2026, 10, 19)))
                .thenThrow(new BusinessException(ErrorCode.FORECAST_NOT_FOUND));
        mvc.perform(get("/api/admin/forecasts/2026-10-19")
                        .with(authenticationFor(Member.Role.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("F001"));
    }

    @Test
    void userAndAnonymousCannotReadForecast() throws Exception {
        mvc.perform(get("/api/admin/forecasts/2026-10-19")
                        .with(authenticationFor(Member.Role.USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("A004"));
        mvc.perform(get("/api/admin/forecasts/2026-10-19"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void adminReadsLatestForecastThroughStaticRoute() throws Exception {
        LocalDate latest = LocalDate.of(2026, 11, 2);
        when(service.getLatest()).thenReturn(new ForecastResponse(
                latest, latest.plusDays(6), "weekly-mlp-v1",
                LocalDateTime.of(2026, 11, 1, 18, 0),
                LocalDateTime.of(2026, 11, 1, 22, 0),
                false, null, false, null, null, null, List.of()));

        mvc.perform(get("/api/admin/forecasts/latest")
                        .with(authenticationFor(Member.Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weekStart").value("2026-11-02"));
    }

    @Test
    void latestReturnsF001WhenNoForecastExists() throws Exception {
        when(service.getLatest()).thenThrow(new BusinessException(ErrorCode.FORECAST_NOT_FOUND));

        mvc.perform(get("/api/admin/forecasts/latest")
                        .with(authenticationFor(Member.Role.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("F001"));
    }

    @Test
    void userCannotReadLatestForecast() throws Exception {
        mvc.perform(get("/api/admin/forecasts/latest")
                        .with(authenticationFor(Member.Role.USER)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    private org.springframework.test.web.servlet.request.RequestPostProcessor authenticationFor(Member.Role role) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthSessionService.AuthPrincipal(7L, 8L, role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        ForecastReadService forecastReadService() {
            return mock(ForecastReadService.class);
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
