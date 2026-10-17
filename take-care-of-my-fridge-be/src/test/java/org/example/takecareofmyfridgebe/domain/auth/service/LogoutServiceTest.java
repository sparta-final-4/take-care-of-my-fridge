package org.example.takecareofmyfridgebe.domain.auth.service;

import org.example.takecareofmyfridgebe.domain.auth.entity.RefreshSession;
import org.example.takecareofmyfridgebe.domain.auth.repository.RefreshSessionRepository;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LogoutServiceTest {

    private final JwtTokenService jwt = new JwtTokenService("0123456789abcdef0123456789abcdef");
    private final RefreshSessionRepository repository = mock(RefreshSessionRepository.class);
    private final LogoutService service = new LogoutService(repository, jwt);

    @Test
    void accessCookieRevokesSessionEvenWithoutRefreshCookie() {
        RefreshSession session = session("refresh");
        when(repository.findByIdForUpdate(8L)).thenReturn(Optional.of(session));

        service.logout(jwt.issueAccess(4L, 8L, Instant.now()), null);

        assertNotNull(session.getRevokedAt());
    }

    @Test
    void expiredAccessFallsBackToRefreshCookieAndRejectsFutureRefresh() {
        String refresh = jwt.issueRefresh(4L, Instant.now());
        RefreshSession session = session(refresh);
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(session));

        service.logout(jwt.issueAccess(4L, 8L, Instant.now().minusSeconds(16 * 60)), refresh);

        assertNotNull(session.getRevokedAt());
        assertThrows(BusinessException.class, () -> new RefreshService(repository, jwt).refresh(refresh));
    }

    @Test
    void missingOrUnknownCookiesAreIdempotent() {
        service.logout(null, null);
        service.logout(null, "unknown");
    }

    private RefreshSession session(String refresh) {
        Member member = Member.builder()
                .email("cook@example.com")
                .passwordHash("hash")
                .nickname("요리왕")
                .build();
        ReflectionTestUtils.setField(member, "id", 4L);
        RefreshSession session = RefreshSession.builder()
                .member(member)
                .tokenHash(jwt.hash(refresh))
                .issuedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();
        ReflectionTestUtils.setField(session, "id", 8L);
        return session;
    }
}
