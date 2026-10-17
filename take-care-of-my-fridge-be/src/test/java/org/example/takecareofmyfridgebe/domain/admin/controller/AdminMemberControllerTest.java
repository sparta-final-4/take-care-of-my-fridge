package org.example.takecareofmyfridgebe.domain.admin.controller;

import org.example.takecareofmyfridgebe.domain.admin.dto.MemberSummaryResponse;
import org.example.takecareofmyfridgebe.domain.admin.service.MemberListService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminMemberController.class)
@Import({SecurityConfig.class, AdminMemberControllerTest.TestConfig.class})
class AdminMemberControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private MemberListService memberListService;

    @BeforeEach
    void resetMock() {
        reset(memberListService);
    }

    @Test
    void adminCanReadFirstPageWithDefaults() throws Exception {
        var member = new MemberSummaryResponse(7L, "cook@example.com", "요리왕",
                Member.Role.USER, Member.Status.ACTIVE, LocalDateTime.of(2026, 10, 17, 12, 0),
                null, null);
        when(memberListService.getMembers(null, null, 1, 20))
                .thenReturn(new PageResponse<>(List.of(member), 1, 20, 1, 1, true));

        mvc.perform(get("/api/admin/members").with(authenticationFor(Member.Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].memberId").value(7))
                .andExpect(jsonPath("$.data.content[0].email").value("cook@example.com"))
                .andExpect(jsonPath("$.data.content[0].promotedAt").isEmpty())
                .andExpect(jsonPath("$.data.pageNumber").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void adminCanSearchEmailAndRoleOnRequestedPage() throws Exception {
        when(memberListService.getMembers("cook", Member.Role.USER, 2, 10))
                .thenReturn(new PageResponse<>(List.of(), 2, 10, 12, 2, true));

        mvc.perform(get("/api/admin/members")
                        .param("keyword", "cook").param("role", "USER")
                        .param("page", "2").param("size", "10")
                        .with(authenticationFor(Member.Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageNumber").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2));
        verify(memberListService).getMembers("cook", Member.Role.USER, 2, 10);
    }

    @Test
    void userAndAnonymousCannotReadMembers() throws Exception {
        mvc.perform(get("/api/admin/members").with(authenticationFor(Member.Role.USER)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/members"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(memberListService);
    }

    @Test
    void invalidPageSizeAndRoleAreRejected() throws Exception {
        for (String query : List.of("page=0", "size=0", "role=OWNER")) {
            mvc.perform(get("/api/admin/members?" + query)
                            .with(authenticationFor(Member.Role.ADMIN)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(memberListService);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor authenticationFor(Member.Role role) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new AuthSessionService.AuthPrincipal(7L, 8L, role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        MemberListService memberListService() {
            return mock(MemberListService.class);
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
