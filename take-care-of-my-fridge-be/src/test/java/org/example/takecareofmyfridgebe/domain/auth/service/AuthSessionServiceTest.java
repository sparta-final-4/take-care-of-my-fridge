package org.example.takecareofmyfridgebe.domain.auth.service;

import org.example.takecareofmyfridgebe.domain.auth.entity.RefreshSession;
import org.example.takecareofmyfridgebe.domain.auth.repository.RefreshSessionRepository;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthSessionServiceTest {

    @Mock
    private RefreshSessionRepository repository;

    @InjectMocks
    private AuthSessionService service;

    @Test
    void activeSessionAuthenticatesAndUpdatesLastUse() {
        RefreshSession session = session(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        when(repository.findById(8L)).thenReturn(Optional.of(session));

        AuthSessionService.AuthPrincipal principal = service.authenticate(
                new JwtTokenService.AccessClaims(4L, 8L));

        assertEquals(4L, principal.memberId());
        assertEquals(Member.Role.USER, principal.role());
        assertEquals(8L, principal.sessionId());
        assertTrue(session.getLastUsedAt().isAfter(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)));
    }

    @Test
    void expiredOrMismatchedSessionIsRejected() {
        RefreshSession session = session(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(31));
        when(repository.findById(8L)).thenReturn(Optional.of(session));

        assertThrows(BusinessException.class,
                () -> service.authenticate(new JwtTokenService.AccessClaims(4L, 8L)));
        assertThrows(BusinessException.class,
                () -> service.authenticate(new JwtTokenService.AccessClaims(5L, 8L)));
    }

    @Test
    void revokedOrSuspendedSessionIsRejected() {
        RefreshSession session = session(LocalDateTime.now(ZoneOffset.UTC));
        when(repository.findById(8L)).thenReturn(Optional.of(session));
        session.revoke(LocalDateTime.now(ZoneOffset.UTC));
        assertThrows(BusinessException.class,
                () -> service.authenticate(new JwtTokenService.AccessClaims(4L, 8L)));

        RefreshSession suspendedSession = session(LocalDateTime.now(ZoneOffset.UTC));
        ReflectionTestUtils.setField(suspendedSession.getMember(), "status", Member.Status.SUSPENDED);
        when(repository.findById(8L)).thenReturn(Optional.of(suspendedSession));
        assertThrows(BusinessException.class,
                () -> service.authenticate(new JwtTokenService.AccessClaims(4L, 8L)));
    }

    private RefreshSession session(LocalDateTime now) {
        Member member = Member.create("cook@example.com", "hash", "요리왕", Set.of());
        ReflectionTestUtils.setField(member, "id", 4L);
        RefreshSession session = RefreshSession.create(member, "hash", now);
        ReflectionTestUtils.setField(session, "id", 8L);
        return session;
    }
}
