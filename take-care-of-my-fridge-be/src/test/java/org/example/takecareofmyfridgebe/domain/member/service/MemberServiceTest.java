package org.example.takecareofmyfridgebe.domain.member.service;

import org.example.takecareofmyfridgebe.domain.member.dto.MyInfoResponse;
import org.example.takecareofmyfridgebe.domain.member.dto.ChangeNicknameRequest;
import org.example.takecareofmyfridgebe.domain.member.dto.SignUpRequest;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.entity.MemberTaste;
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
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
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
    void nicknameIsAvailableWhenNoMemberUsesIt() {
        when(memberRepository.existsByNickname("요리왕")).thenReturn(false);

        assertTrue(memberService.checkNicknameAvailability("요리왕", null).available());
    }

    @Test
    void nicknameIsUnavailableWhenMemberAlreadyUsesIt() {
        when(memberRepository.existsByNickname("요리왕")).thenReturn(true);

        assertFalse(memberService.checkNicknameAvailability("요리왕", null).available());
    }

    @Test
    void loggedInMemberCheckingOwnNicknameExcludesThemself() {
        when(memberRepository.existsByNicknameAndIdNot("요리왕", 7L)).thenReturn(false);

        assertTrue(memberService.checkNicknameAvailability("요리왕", 7L).available());
        verify(memberRepository, never()).existsByNickname(any());
    }

    @Test
    void loggedInMemberSeesNicknameTakenByOtherMember() {
        when(memberRepository.existsByNicknameAndIdNot("요리왕", 7L)).thenReturn(true);

        assertFalse(memberService.checkNicknameAvailability("요리왕", 7L).available());
    }

    @Test
    void getMyInfoReturnsMemberWithTastes() {
        Member member = Member.create("cook@example.com", "{bcrypt}encoded", "요리왕",
                Set.of(Member.Taste.SWEET));
        ReflectionTestUtils.setField(member, "id", 7L);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member));

        MyInfoResponse response = memberService.getMyInfo(7L);

        assertEquals(7L, response.memberId());
        assertEquals("cook@example.com", response.email());
        assertEquals("요리왕", response.nickname());
        assertEquals(Member.Role.USER, response.role());
        assertEquals(Member.Status.ACTIVE, response.status());
        assertEquals(List.of(Member.Taste.SWEET), response.tastes());
    }

    @Test
    void getMyInfoRejectsMissingMember() {
        when(memberRepository.findById(7L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () -> memberService.getMyInfo(7L));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void changeNicknameUpdatesOnlyCurrentMember() {
        Member member = Member.create("cook@example.com", "hash", "기존닉네임", Set.of());
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member));

        memberService.changeNickname(7L, new ChangeNicknameRequest(" 새닉네임 "));

        assertEquals("새닉네임", member.getNickname());
        verify(memberRepository).existsByNicknameAndIdNot("새닉네임", 7L);
        verify(memberRepository).flush();
    }

    @Test
    void changeNicknameRejectsAnotherMembersNickname() {
        Member member = Member.create("cook@example.com", "hash", "기존닉네임", Set.of());
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
        when(memberRepository.existsByNicknameAndIdNot("중복닉네임", 7L)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> memberService.changeNickname(7L, new ChangeNicknameRequest("중복닉네임")));

        assertEquals(ErrorCode.DUPLICATE_NICKNAME, exception.getErrorCode());
        assertEquals("기존닉네임", member.getNickname());
        verify(memberRepository, never()).flush();
    }

    @Test
    void changeNicknameMapsConcurrentUniqueConstraintConflict() {
        Member member = Member.create("cook@example.com", "hash", "기존닉네임", Set.of());
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
        doThrow(new DataIntegrityViolationException("Duplicate entry for key 'uk_members_nickname'"))
                .when(memberRepository).flush();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> memberService.changeNickname(7L, new ChangeNicknameRequest("중복닉네임")));

        assertEquals(ErrorCode.DUPLICATE_NICKNAME, exception.getErrorCode());
    }

    @Test
    void changeNicknameToSameValueDoesNotWrite() {
        Member member = Member.create("cook@example.com", "hash", "기존닉네임", Set.of());
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member));

        memberService.changeNickname(7L, new ChangeNicknameRequest("기존닉네임"));

        verify(memberRepository, never()).flush();
    }

    @Test
    void changeTastesReplacesListAndAcceptsDuplicates() {
        Member member = Member.create("cook@example.com", "hash", "요리왕",
                Set.of(Member.Taste.SWEET, Member.Taste.SALTY));
        MemberTaste existingSweet = member.getTastes().stream()
                .filter(item -> item.getTaste() == Member.Taste.SWEET).findFirst().orElseThrow();
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member));

        memberService.changeTastes(7L,
                List.of(Member.Taste.SWEET, Member.Taste.SWEET, Member.Taste.SPICY));

        assertEquals(2, member.getTastes().size());
        assertEquals(Set.of(Member.Taste.SWEET, Member.Taste.SPICY),
                Set.copyOf(member.getTastes().stream().map(MemberTaste::getTaste).toList()));
        assertSame(existingSweet, member.getTastes().stream()
                .filter(item -> item.getTaste() == Member.Taste.SWEET).findFirst().orElseThrow());
    }

    @Test
    void changeTastesWithEmptyListClearsAllPreferences() {
        Member member = Member.create("cook@example.com", "hash", "요리왕",
                Set.of(Member.Taste.SWEET));
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member));

        memberService.changeTastes(7L, List.of());

        assertTrue(member.getTastes().isEmpty());
    }

    @Test
    void changeTastesRejectsMissingMember() {
        when(memberRepository.findById(7L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> memberService.changeTastes(7L, List.of()));

        assertEquals(ErrorCode.MEMBER_NOT_FOUND, exception.getErrorCode());
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
