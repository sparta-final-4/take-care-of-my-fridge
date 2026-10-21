package org.example.takecareofmyfridgebe.domain.auth.entity;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshSessionTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 10, 16, 12, 0);
    private final Member member = Member.builder()
            .email("cook@example.com")
            .passwordHash("hash")
            .nickname("요리왕")
            .build();

    @Test
    void expiresAfterThirtyMinutesWithoutUse() {
        RefreshSession session = RefreshSession.builder()
                .member(member)
                .tokenHash("token-hash")
                .issuedAt(now)
                .build();

        assertTrue(session.isUsable(now.plusMinutes(29)));
        assertFalse(session.isUsable(now.plusMinutes(30)));
    }

    @Test
    void useExtendsIdleWindowButNotSevenDayLimit() {
        RefreshSession session = RefreshSession.builder()
                .member(member)
                .tokenHash("token-hash")
                .issuedAt(now)
                .build();
        session.markUsed(now.plusMinutes(20));

        assertTrue(session.isUsable(now.plusMinutes(49)));
        session.markUsed(now.plusDays(7).minusMinutes(1));
        assertFalse(session.isUsable(now.plusDays(7)));
    }

    @Test
    void revokedSessionCannotBeUsed() {
        RefreshSession session = RefreshSession.builder()
                .member(member)
                .tokenHash("token-hash")
                .issuedAt(now)
                .build();
        session.revoke(now.plusMinutes(1));

        assertFalse(session.isUsable(now.plusMinutes(2)));
    }
}
