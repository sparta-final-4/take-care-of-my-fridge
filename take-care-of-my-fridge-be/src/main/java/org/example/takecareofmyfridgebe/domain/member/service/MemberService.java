package org.example.takecareofmyfridgebe.domain.member.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.auth.repository.RefreshSessionRepository;
import org.example.takecareofmyfridgebe.domain.member.dto.ChangeNicknameRequest;
import org.example.takecareofmyfridgebe.domain.member.dto.EmailAvailabilityResponse;
import org.example.takecareofmyfridgebe.domain.member.dto.MyInfoResponse;
import org.example.takecareofmyfridgebe.domain.member.dto.NicknameAvailabilityResponse;
import org.example.takecareofmyfridgebe.domain.member.dto.SignUpRequest;
import org.example.takecareofmyfridgebe.domain.member.dto.SignUpResponse;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final RefreshSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;

    public EmailAvailabilityResponse checkEmailAvailability(String email) {
        return new EmailAvailabilityResponse(!memberRepository.existsByEmail(email));
    }

    public NicknameAvailabilityResponse checkNicknameAvailability(String nickname, Long memberId) {
        boolean taken = memberId == null
                ? memberRepository.existsByNickname(nickname)
                : memberRepository.existsByNicknameAndIdNot(nickname, memberId);
        return new NicknameAvailabilityResponse(!taken);
    }

    public MyInfoResponse getMyInfo(Long memberId) {
        return memberRepository.findById(memberId)
                .map(MyInfoResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Transactional
    public void changeNickname(Long memberId, ChangeNicknameRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (member.getNickname().equals(request.nickname())) {
            return;
        }
        if (memberRepository.existsByNicknameAndIdNot(request.nickname(), memberId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        member.changeNickname(request.nickname());
        try {
            memberRepository.flush();
        } catch (DataIntegrityViolationException e) {
            String detail = String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
            if (detail.contains("uk_members_nickname")) {
                throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
            }
            throw e;
        }
    }

    @Transactional
    public void changePassword(Long memberId, Long currentSessionId, String currentPassword, String newPassword) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        if (!passwordEncoder.matches(currentPassword, member.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }
        member.changePassword(passwordEncoder.encode(newPassword));
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        sessionRepository.findAllByMember_IdAndIdNotAndRevokedAtIsNull(memberId, currentSessionId)
                .forEach(session -> session.revoke(now));
    }

    @Transactional
    public void changeTastes(Long memberId, List<Member.Taste> tastes) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        member.replaceTastes(new HashSet<>(tastes));
    }

    @Transactional
    public SignUpResponse signUp(SignUpRequest request) {
        Set<Member.Taste> tastes = new HashSet<>(request.tastes());
        if (tastes.size() != request.tastes().size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "맛 선호는 중복해서 선택할 수 없습니다.");
        }

        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (memberRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }

        Member member = Member.create(request.email(), passwordEncoder.encode(request.password()),
                request.nickname(), tastes);
        try {
            return SignUpResponse.from(memberRepository.saveAndFlush(member));
        } catch (DataIntegrityViolationException e) {
            String detail = String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
            if (detail.contains("uk_members_email")) {
                throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
            }
            if (detail.contains("uk_members_nickname")) {
                throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
            }
            throw e;
        }
    }
}
