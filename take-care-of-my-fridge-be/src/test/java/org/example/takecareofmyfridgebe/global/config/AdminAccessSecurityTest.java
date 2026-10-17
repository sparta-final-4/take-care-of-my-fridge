package org.example.takecareofmyfridgebe.global.config;

import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminAccessSecurityTest.AdminProbeController.class)
@Import({SecurityConfig.class, AdminAccessSecurityTest.TestConfig.class, AdminAccessSecurityTest.AdminProbeController.class})
class AdminAccessSecurityTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mvc.perform(get("/api/admin/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("A003"));
    }

    @Test
    void userRequestIsForbidden() throws Exception {
        mvc.perform(get("/api/admin/probe").with(authentication(authenticationFor(Member.Role.USER))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("A004"));
    }

    @Test
    void adminRequestIsAllowed() throws Exception {
        mvc.perform(get("/api/admin/probe").with(authentication(authenticationFor(Member.Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private UsernamePasswordAuthenticationToken authenticationFor(Member.Role role) {
        return new UsernamePasswordAuthenticationToken(
                new AuthSessionService.AuthPrincipal(7L, 8L, role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    @RestController
    public static class AdminProbeController {
        @GetMapping("/api/admin/probe")
        public ResponseEntity<ApiResponse<Void>> probe() {
            return ResponseEntity.ok(ApiResponse.ok());
        }
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
    }
}
