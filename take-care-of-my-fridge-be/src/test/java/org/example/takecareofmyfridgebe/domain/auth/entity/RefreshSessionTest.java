package org.example.takecareofmyfridgebe.domain.auth.entity;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshSessionTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 10, 16, 12, 0);
    private final Member member = Member.create("cook@example.com", "hash", "요리왕", Set.of());

    @Test
    void expiresAfterThirtyMinutesWithoutUse() {
        RefreshSession session = RefreshSession.create(member, "token-hash", now);

        assertTrue(session.isUsable(now.plusMinutes(29)));
        assertFalse(session.isUsable(now.plusMinutes(30)));
    }

    @Test
    void useExtendsIdleWindowButNotSevenDayLimit() {
        RefreshSession session = RefreshSession.create(member, "token-hash", now);
        session.markUsed(now.plusMinutes(20));

        assertTrue(session.isUsable(now.plusMinutes(49)));
        session.markUsed(now.plusDays(7).minusMinutes(1));
        assertFalse(session.isUsable(now.plusDays(7)));
    }

    @Test
    void revokedSessionCannotBeUsed() {
        RefreshSession session = RefreshSession.create(member, "token-hash", now);
        session.revoke(now.plusMinutes(1));

        assertFalse(session.isUsable(now.plusMinutes(2)));
    }
}
