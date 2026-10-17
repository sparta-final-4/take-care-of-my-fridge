package org.example.takecareofmyfridgebe.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.auth.entity.RefreshSession;
import org.example.takecareofmyfridgebe.domain.auth.repository.RefreshSessionRepository;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class RefreshService {

    private final RefreshSessionRepository sessionRepository;
    private final JwtTokenService jwtTokenService;

    @Transactional
    public RefreshedTokens refresh(String token) {
        Long memberId;
        try {
            memberId = jwtTokenService.parseRefresh(token);
        } catch (JwtException e) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        RefreshSession session = sessionRepository.findByTokenHashForUpdate(jwtTokenService.hash(token))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        Member member = session.getMember();
        if (!member.getId().equals(memberId) || !session.isUsable(now)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (member.getStatus() != Member.Status.ACTIVE) {
            throw new BusinessException(ErrorCode.SESSION_REVOKED_BY_SUSPENSION);
        }

        Instant issuedAt = now.toInstant(ZoneOffset.UTC);
        String newRefresh = jwtTokenService.issueRefresh(memberId, issuedAt,
                session.getExpiresAt().toInstant(ZoneOffset.UTC));
        session.rotateToken(jwtTokenService.hash(newRefresh), now);
        String access = jwtTokenService.issueAccess(memberId, session.getId(), issuedAt);
        return new RefreshedTokens(access, newRefresh,
                Duration.between(issuedAt, session.getExpiresAt().toInstant(ZoneOffset.UTC)));
    }

    public record RefreshedTokens(String accessToken, String refreshToken, Duration refreshMaxAge) {
    }
}
