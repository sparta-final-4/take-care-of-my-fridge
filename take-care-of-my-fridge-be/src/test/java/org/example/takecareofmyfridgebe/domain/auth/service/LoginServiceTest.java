package org.example.takecareofmyfridgebe.domain.auth.service;

import org.example.takecareofmyfridgebe.domain.auth.entity.RefreshSession;
import org.example.takecareofmyfridgebe.domain.auth.repository.RefreshSessionRepository;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private LoginCredentialService credentialService;

    @Mock
    private RefreshSessionRepository sessionRepository;

    @Test
    void storesOnlyRefreshHashAndIssuesTokensForSavedSession() {
        Member member = Member.create("cook@example.com", "hash", "요리왕", Set.of(Member.Taste.SPICY));
        ReflectionTestUtils.setField(member, "id", 4L);
        when(credentialService.authenticate("cook@example.com", "password")).thenReturn(member);
        when(sessionRepository.saveAndFlush(any(RefreshSession.class))).thenAnswer(invocation -> {
            RefreshSession session = invocation.getArgument(0);
            ReflectionTestUtils.setField(session, "id", 8L);
            return session;
        });
        JwtTokenService jwt = new JwtTokenService("0123456789abcdef0123456789abcdef");
        LoginService service = new LoginService(credentialService, sessionRepository, jwt);

        LoginService.LoginResult result = service.login("cook@example.com", "password");

        assertEquals(new JwtTokenService.AccessClaims(4L, 8L), jwt.parseAccess(result.accessToken()));
        assertEquals(4L, jwt.parseRefresh(result.refreshToken()));
        assertEquals(Member.Role.USER, result.member().role());
        assertEquals(Member.Taste.SPICY, result.member().tastes().get(0));
        assertNotEquals(result.refreshToken(), jwt.hash(result.refreshToken()));
        verify(sessionRepository).saveAndFlush(
                argThat(session ->
                        jwt.hash(result.refreshToken()).equals(session.getTokenHash())
                                && !session.isKeepLoggedIn()
                                && session.isUsable(session.getLastUsedAt())));
    }
}
