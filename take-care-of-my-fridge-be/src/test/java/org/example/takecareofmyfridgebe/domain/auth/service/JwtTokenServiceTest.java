package org.example.takecareofmyfridgebe.domain.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private final JwtTokenService service = new JwtTokenService(SECRET);

    @Test
    void issuesSignedAccessTokenWithMemberAndSession() {
        String token = service.issueAccess(3L, 12L, Instant.now());

        assertEquals(new JwtTokenService.AccessClaims(3L, 12L), service.parseAccess(token));
        assertThrows(JwtException.class, () -> new JwtTokenService("different-secret-0123456789abcdef")
                .parseAccess(token));
    }

    @Test
    void refreshTokenHasUniqueIdAndCannotBeUsedAsAccessToken() {
        String first = service.issueRefresh(3L, Instant.now());
        String second = service.issueRefresh(3L, Instant.now());

        assertEquals(3L, service.parseRefresh(first));
        assertNotEquals(first, second);
        assertNotEquals(service.hash(first), service.hash(second));
        assertThrows(JwtException.class, () -> service.parseAccess(first));
    }

    @Test
    void rejectsShortSigningSecret() {
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenService("short"));
    }
}
