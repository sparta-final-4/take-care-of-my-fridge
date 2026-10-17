package org.example.takecareofmyfridgebe.domain.auth.service;

import org.example.takecareofmyfridgebe.domain.auth.entity.RefreshSession;
import org.example.takecareofmyfridgebe.domain.auth.repository.RefreshSessionRepository;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RefreshServiceTest {

    private final JwtTokenService jwt = new JwtTokenService("0123456789abcdef0123456789abcdef");
    private final RefreshSessionRepository repository = mock(RefreshSessionRepository.class);
    private final RefreshService service = new RefreshService(repository, jwt);

    @Test
    void rotatesRefreshTokenAndRejectsReplay() {
        String oldToken = jwt.issueRefresh(4L, Instant.now());
        RefreshSession session = session(oldToken, LocalDateTime.now(ZoneOffset.UTC));
        when(repository.findByTokenHashForUpdate(anyString())).thenAnswer(invocation ->
                session.getTokenHash().equals(invocation.getArgument(0))
                        ? Optional.of(session) : Optional.empty());

        RefreshService.RefreshedTokens tokens = service.refresh(oldToken);

        assertEquals(new JwtTokenService.AccessClaims(4L, 8L), jwt.parseAccess(tokens.accessToken()));
        assertEquals(4L, jwt.parseRefresh(tokens.refreshToken()));
        assertNotEquals(oldToken, tokens.refreshToken());
        assertEquals(jwt.hash(tokens.refreshToken()), session.getTokenHash());
        assertTrue(tokens.refreshMaxAge().compareTo(Duration.ofDays(7)) < 0);
        BusinessException replay = assertThrows(BusinessException.class, () -> service.refresh(oldToken));
        assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, replay.getErrorCode());
    }

    @Test
    void rejectsExpiredIdleSessionAndInvalidSignature() {
        String token = jwt.issueRefresh(4L, Instant.now());
        RefreshSession session = session(token, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(31));
        when(repository.findByTokenHashForUpdate(jwt.hash(token))).thenReturn(Optional.of(session));

        BusinessException expired = assertThrows(BusinessException.class, () -> service.refresh(token));
        BusinessException invalid = assertThrows(BusinessException.class, () -> service.refresh("invalid"));

        assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, expired.getErrorCode());
        assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, invalid.getErrorCode());
    }

    @Test
    void suspendedMemberGetsSuspensionCode() {
        String token = jwt.issueRefresh(4L, Instant.now());
        RefreshSession session = session(token, LocalDateTime.now(ZoneOffset.UTC));
        ReflectionTestUtils.setField(session.getMember(), "status", Member.Status.SUSPENDED);
        when(repository.findByTokenHashForUpdate(jwt.hash(token))).thenReturn(Optional.of(session));

        BusinessException suspended = assertThrows(BusinessException.class, () -> service.refresh(token));

        assertEquals(ErrorCode.SESSION_REVOKED_BY_SUSPENSION, suspended.getErrorCode());
    }

    private RefreshSession session(String token, LocalDateTime now) {
        Member member = Member.builder()
                .email("cook@example.com")
                .passwordHash("hash")
                .nickname("요리왕")
                .build();
        ReflectionTestUtils.setField(member, "id", 4L);
        RefreshSession session = RefreshSession.create(member, jwt.hash(token), now);
        ReflectionTestUtils.setField(session, "id", 8L);
        return session;
    }
}
