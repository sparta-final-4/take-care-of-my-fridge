package org.example.takecareofmyfridgebe.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.auth.entity.RefreshSession;
import org.example.takecareofmyfridgebe.domain.auth.repository.RefreshSessionRepository;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class AuthSessionService {

    private final RefreshSessionRepository sessionRepository;

    @Transactional
    public AuthPrincipal authenticate(JwtTokenService.AccessClaims claims) {
        RefreshSession session = sessionRepository.findById(claims.sessionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Member member = session.getMember();
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        if (!member.getId().equals(claims.memberId()) || !session.isUsable(now)
                || member.getStatus() != Member.Status.ACTIVE) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        session.markUsed(now);
        return new AuthPrincipal(member.getId(), member.getRole());
    }

    public record AuthPrincipal(Long memberId, Member.Role role) {
    }
}
