package org.example.takecareofmyfridgebe.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.auth.entity.RefreshSession;
import org.example.takecareofmyfridgebe.domain.auth.repository.RefreshSessionRepository;
import org.example.takecareofmyfridgebe.domain.member.dto.MyInfoResponse;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class LoginService {

    private final LoginCredentialService credentialService;
    private final RefreshSessionRepository sessionRepository;
    private final JwtTokenService jwtTokenService;

    @Transactional
    public LoginResult login(String email, String password) {
        Member member = credentialService.authenticate(email, password);
        Instant now = Instant.now();
        String refreshToken = jwtTokenService.issueRefresh(member.getId(), now);
        RefreshSession session = sessionRepository.saveAndFlush(RefreshSession.builder()
                .member(member)
                .tokenHash(jwtTokenService.hash(refreshToken))
                .issuedAt(LocalDateTime.ofInstant(now, ZoneOffset.UTC))
                .build());
        String accessToken = jwtTokenService.issueAccess(member.getId(), session.getId(), now);
        return new LoginResult(MyInfoResponse.from(member), accessToken, refreshToken);
    }

    public record LoginResult(MyInfoResponse member, String accessToken, String refreshToken) {
    }
}
