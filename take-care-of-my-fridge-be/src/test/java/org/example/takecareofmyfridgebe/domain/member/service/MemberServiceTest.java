package org.example.takecareofmyfridgebe.domain.member.service;

import org.example.takecareofmyfridgebe.domain.member.dto.SignUpRequest;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private MemberService memberService;

    @Test
    void signUpCreatesActiveUserWithHashedPasswordAndTastes() {
        SignUpRequest request = new SignUpRequest("cook@example.com", "password123", "요리왕",
                List.of(Member.Taste.SWEET, Member.Taste.SPICY));
        when(passwordEncoder.encode("password123")).thenReturn("{bcrypt}encoded");
        when(memberRepository.saveAndFlush(any(Member.class))).thenAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            ReflectionTestUtils.setField(member, "id", 10L);
            return member;
        });

        assertEquals(10L, memberService.signUp(request).memberId());

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).saveAndFlush(captor.capture());
        Member member = captor.getValue();
        assertEquals(Member.Role.USER, member.getRole());
        assertEquals(Member.Status.ACTIVE, member.getStatus());
        assertNotEquals(request.password(), member.getPasswordHash());
        assertEquals(2, member.getTastes().size());
    }

    @Test
    void emailIsAvailableWhenNoMemberUsesIt() {
        when(memberRepository.existsByEmail("cook@example.com")).thenReturn(false);

        assertTrue(memberService.checkEmailAvailability("cook@example.com").available());
    }

    @Test
    void emailIsUnavailableWhenMemberAlreadyUsesIt() {
        when(memberRepository.existsByEmail("cook@example.com")).thenReturn(true);

        assertFalse(memberService.checkEmailAvailability("cook@example.com").available());
    }

    @Test
    void signUpRejectsExistingEmail() {
        SignUpRequest request = new SignUpRequest("cook@example.com", "password123", "요리왕", List.of());
        when(memberRepository.existsByEmail(request.email())).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> memberService.signUp(request));

        assertEquals(ErrorCode.DUPLICATE_EMAIL, exception.getErrorCode());
        verify(memberRepository, never()).saveAndFlush(any(Member.class));
    }

    @Test
    void signUpRejectsExistingNickname() {
        SignUpRequest request = new SignUpRequest("cook@example.com", "password123", "요리왕", List.of());
        when(memberRepository.existsByNickname(request.nickname())).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> memberService.signUp(request));

        assertEquals(ErrorCode.DUPLICATE_NICKNAME, exception.getErrorCode());
        verify(memberRepository, never()).saveAndFlush(any(Member.class));
    }

    @Test
    void signUpRejectsDuplicateTastes() {
        SignUpRequest request = new SignUpRequest("cook@example.com", "password123", "요리왕",
                List.of(Member.Taste.SWEET, Member.Taste.SWEET));

        BusinessException exception = assertThrows(BusinessException.class, () -> memberService.signUp(request));

        assertEquals(ErrorCode.INVALID_INPUT_VALUE, exception.getErrorCode());
        verify(memberRepository, never()).saveAndFlush(any(Member.class));
    }

    @Test
    void signUpMapsConcurrentNicknameConflict() {
        SignUpRequest request = new SignUpRequest("cook@example.com", "password123", "요리왕", List.of());
        when(memberRepository.saveAndFlush(any(Member.class))).thenThrow(
                new DataIntegrityViolationException("Duplicate entry for key 'uk_members_nickname'"));

        BusinessException exception = assertThrows(BusinessException.class, () -> memberService.signUp(request));

        assertEquals(ErrorCode.DUPLICATE_NICKNAME, exception.getErrorCode());
    }

    @Test
    void signUpMapsConcurrentEmailConflict() {
        SignUpRequest request = new SignUpRequest("cook@example.com", "password123", "요리왕", List.of());
        when(memberRepository.saveAndFlush(any(Member.class))).thenThrow(
                new DataIntegrityViolationException("Duplicate entry for key 'uk_members_email'"));

        BusinessException exception = assertThrows(BusinessException.class, () -> memberService.signUp(request));

        assertEquals(ErrorCode.DUPLICATE_EMAIL, exception.getErrorCode());
    }
}
