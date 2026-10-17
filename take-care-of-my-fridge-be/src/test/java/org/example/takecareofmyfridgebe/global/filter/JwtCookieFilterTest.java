package org.example.takecareofmyfridgebe.global.filter;

import jakarta.servlet.http.Cookie;
import org.example.takecareofmyfridgebe.domain.auth.service.AuthSessionService;
import org.example.takecareofmyfridgebe.domain.auth.service.JwtTokenService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtCookieFilterTest {

    private final JwtTokenService jwt = new JwtTokenService("0123456789abcdef0123456789abcdef");
    private final AuthSessionService sessions = mock(AuthSessionService.class);
    private final JwtCookieFilter filter = new JwtCookieFilter(jwt, sessions);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validCookieSetsPrincipalAndRole() throws Exception {
        String token = jwt.issueAccess(4L, 8L, Instant.now());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("access_token", token));
        when(sessions.authenticate(new JwtTokenService.AccessClaims(4L, 8L)))
                .thenReturn(new AuthSessionService.AuthPrincipal(4L, 8L, Member.Role.USER));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals(4L, ((AuthSessionService.AuthPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal()).memberId());
        assertEquals("ROLE_USER", SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().iterator().next().getAuthority());
        verify(sessions).authenticate(new JwtTokenService.AccessClaims(4L, 8L));
    }

    @Test
    void invalidCookieLeavesRequestAnonymous() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("access_token", "invalid"));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
