package org.example.takecareofmyfridgebe.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.auth.repository.RefreshSessionRepository;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class LogoutService {

    private final RefreshSessionRepository sessionRepository;
    private final JwtTokenService jwtTokenService;

    @Transactional
    public void logout(String accessToken, String refreshToken) {
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                JwtTokenService.AccessClaims claims = jwtTokenService.parseAccess(accessToken);
                boolean revoked = sessionRepository.findByIdForUpdate(claims.sessionId())
                        .filter(session -> session.getMember().getId().equals(claims.memberId()))
                        .map(session -> {
                            session.revoke(now);
                            return true;
                        }).orElse(false);
                if (revoked) {
                    return;
                }
            } catch (JwtException ignored) {
                // 만료된 액세스 토큰은 갱신 쿠키로 세션을 찾는다.
            }
        }
        if (refreshToken != null && !refreshToken.isBlank()) {
            sessionRepository.findByTokenHashForUpdate(jwtTokenService.hash(refreshToken))
                    .ifPresent(session -> session.revoke(now));
        }
    }
}
