package org.example.takecareofmyfridgebe.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.entity.BaseTimeEntity;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_sessions", uniqueConstraints =
        @UniqueConstraint(name = "uk_refresh_sessions_token_hash", columnNames = "token_hash"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshSession extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "keep_logged_in", nullable = false)
    private boolean keepLoggedIn;

    @Column(name = "last_used_at", nullable = false)
    private LocalDateTime lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Builder
    private RefreshSession(Member member, String tokenHash, LocalDateTime issuedAt) {
        this.member = member;
        this.tokenHash = tokenHash;
        this.lastUsedAt = issuedAt;
        this.expiresAt = issuedAt.plusDays(7);
    }

    public boolean isUsable(LocalDateTime now) {
        return revokedAt == null && now.isBefore(expiresAt)
                && now.isBefore(lastUsedAt.plusMinutes(30));
    }

    public void markUsed(LocalDateTime now) {
        lastUsedAt = now;
    }

    public void rotateToken(String newTokenHash, LocalDateTime now) {
        tokenHash = newTokenHash;
        lastUsedAt = now;
    }

    public void revoke(LocalDateTime now) {
        revokedAt = now;
    }
}
