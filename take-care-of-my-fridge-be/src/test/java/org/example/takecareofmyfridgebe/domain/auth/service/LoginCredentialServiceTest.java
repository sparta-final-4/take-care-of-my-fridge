package org.example.takecareofmyfridgebe.domain.auth.service;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginCredentialServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private LoginCredentialService service;

    @Test
    void acceptsActiveMemberWithCorrectPassword() {
        Member member = member(Member.Status.ACTIVE);
        when(memberRepository.findByEmail("cook@example.com")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("password", member.getPasswordHash())).thenReturn(true);

        assertSame(member, service.authenticate("cook@example.com", "password"));
    }

    @Test
    void usesSameFailureForMissingEmailAndWrongPassword() {
        when(memberRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        Member member = member(Member.Status.ACTIVE);
        when(memberRepository.findByEmail("cook@example.com")).thenReturn(Optional.of(member));

        BusinessException missing = assertThrows(BusinessException.class,
                () -> service.authenticate("missing@example.com", "password"));
        BusinessException wrong = assertThrows(BusinessException.class,
                () -> service.authenticate("cook@example.com", "wrong"));

        assertEquals(ErrorCode.LOGIN_FAILED, missing.getErrorCode());
        assertEquals(missing.getErrorCode(), wrong.getErrorCode());
    }

    @Test
    void revealsSuspensionOnlyAfterCorrectPassword() {
        Member member = member(Member.Status.SUSPENDED);
        when(memberRepository.findByEmail("cook@example.com")).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("wrong", member.getPasswordHash())).thenReturn(false);
        when(passwordEncoder.matches("password", member.getPasswordHash())).thenReturn(true);

        BusinessException wrong = assertThrows(BusinessException.class,
                () -> service.authenticate("cook@example.com", "wrong"));
        BusinessException suspended = assertThrows(BusinessException.class,
                () -> service.authenticate("cook@example.com", "password"));

        assertEquals(ErrorCode.LOGIN_FAILED, wrong.getErrorCode());
        assertEquals(ErrorCode.ACCOUNT_SUSPENDED, suspended.getErrorCode());
    }

    private Member member(Member.Status status) {
        Member member = Member.builder()
                .email("cook@example.com")
                .passwordHash("{bcrypt}hash")
                .nickname("요리왕")
                .build();
        ReflectionTestUtils.setField(member, "status", status);
        return member;
    }
}
