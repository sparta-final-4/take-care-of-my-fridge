package org.example.takecareofmyfridgebe.domain.admin.controller;

import org.example.takecareofmyfridgebe.domain.admin.dto.AuditLogResponse;
import org.example.takecareofmyfridgebe.domain.admin.entity.AdminAuditLog;
import org.example.takecareofmyfridgebe.domain.admin.service.AdminAuditSearchService;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.config.SecurityConfig;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminAuditController.class)
@Import({SecurityConfig.class, AdminAuditControllerTest.TestConfig.class})
class AdminAuditControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private AdminAuditSearchService auditSearchService;

    @BeforeEach
    void resetMock() {
        reset(auditSearchService);
    }

    @Test
    void adminReadsAuditLogsWithDefaults() throws Exception {
        var log = new AuditLogResponse(5L, LocalDateTime.of(2026, 10, 17, 10, 0),
                "cook@example.com", Member.Role.USER, AdminAuditLog.Action.ROLE_CHANGE,
                "Member", 12L, AdminAuditLog.Result.DENIED,
                Map.of("role", "USER"), null);
        when(auditSearchService.search(null, null, null, null, null, null, 1, 20))
                .thenReturn(new PageResponse<>(List.of(log), 1, 20, 1, 1, true));

        mvc.perform(get("/api/admin/audit-logs").with(authenticationFor(Member.Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].auditLogId").value(5))
                .andExpect(jsonPath("$.data.content[0].actorRole").value("USER"))
                .andExpect(jsonPath("$.data.content[0].beforeValue.role").value("USER"))
                .andExpect(jsonPath("$.data.pageNumber").value(1));
    }

    @Test
    void adminCanPassAllFilters() throws Exception {
        LocalDate from = LocalDate.of(2026, 10, 10);
        LocalDate to = LocalDate.of(2026, 10, 17);
        when(auditSearchService.search(from, to, AdminAuditLog.Action.ROLE_CHANGE,
                AdminAuditLog.Result.FAILED, "Member", 12L, 2, 10))
                .thenReturn(new PageResponse<>(List.of(), 2, 10, 11, 2, true));

        mvc.perform(get("/api/admin/audit-logs")
                        .param("from", "2026-10-10").param("to", "2026-10-17")
                        .param("action", "ROLE_CHANGE").param("result", "FAILED")
                        .param("targetType", "Member").param("targetId", "12")
                        .param("page", "2").param("size", "10")
                        .with(authenticationFor(Member.Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageNumber").value(2));
        verify(auditSearchService).search(from, to, AdminAuditLog.Action.ROLE_CHANGE,
                AdminAuditLog.Result.FAILED, "Member", 12L, 2, 10);
    }

    @Test
    void userAndAnonymousAreRejected() throws Exception {
        mvc.perform(get("/api/admin/audit-logs").with(authenticationFor(Member.Role.USER)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(auditSearchService);
    }

    @Test
    void invalidFiltersAreRejectedBeforeSearch() throws Exception {
        for (String query : List.of("from=bad", "action=UNKNOWN", "result=UNKNOWN",
                "targetId=0", "page=0", "size=0")) {
            mvc.perform(get("/api/admin/audit-logs?" + query)
                            .with(authenticationFor(Member.Role.ADMIN)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(auditSearchService);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor authenticationFor(Member.Role role) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthSessionService.AuthPrincipal(7L, 8L, role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        AdminAuditSearchService auditSearchService() {
            return mock(AdminAuditSearchService.class);
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
