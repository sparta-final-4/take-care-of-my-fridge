package org.example.takecareofmyfridgebe.domain.deal.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealAnalysisResponse;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealAnalysis;
import org.example.takecareofmyfridgebe.domain.deal.service.DealAnalysisReadService;
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

@WebMvcTest(AdminDealAnalysisController.class)
@Import({SecurityConfig.class, AdminDealAnalysisControllerTest.TestConfig.class})
class AdminDealAnalysisControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private DealAnalysisReadService service;

    @BeforeEach
    void resetMock() {
        reset(service);
    }

    @Test
    void adminReadsAnalysis() throws Exception {
        when(service.getById(8L)).thenReturn(new DealAnalysisResponse(
                8L, LocalDate.of(2026, 10, 26), DealAnalysis.Day.WED, 30, 50,
                "model-v1", null, null, null, false,
                new DealAnalysisResponse.ReviewCounts(0, 0, 0), List.of(), List.of()));

        mvc.perform(get("/api/admin/deal-analyses/8").with(authenticationFor(Member.Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisId").value(8));
    }

    @Test
    void missingAnalysisReturnsD002() throws Exception {
        when(service.getById(9L)).thenThrow(new BusinessException(ErrorCode.DEAL_ANALYSIS_NOT_FOUND));

        mvc.perform(get("/api/admin/deal-analyses/9").with(authenticationFor(Member.Role.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("D002"));
    }

    @Test
    void userAndAnonymousCannotRead() throws Exception {
        mvc.perform(get("/api/admin/deal-analyses/8").with(authenticationFor(Member.Role.USER)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/deal-analyses/8"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void adminReadsOwnLatestAnalysis() throws Exception {
        when(service.getLatestForMember(7L)).thenReturn(new DealAnalysisResponse(
                12L, LocalDate.of(2026, 10, 26), DealAnalysis.Day.WED, 30, 50,
                "model-v1", null, null, null, false,
                new DealAnalysisResponse.ReviewCounts(0, 0, 0), List.of(), List.of()));

        mvc.perform(get("/api/admin/deal-analyses/latest")
                        .with(authenticationFor(Member.Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisId").value(12));
    }

    @Test
    void userCannotReadLatestAnalysis() throws Exception {
        mvc.perform(get("/api/admin/deal-analyses/latest")
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
        DealAnalysisReadService dealAnalysisReadService() {
            return mock(DealAnalysisReadService.class);
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
