package org.example.takecareofmyfridgebe.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoginCredentialService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public Member authenticate(String email, String password) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));
        if (!passwordEncoder.matches(password, member.getPasswordHash())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        if (member.getStatus() == Member.Status.SUSPENDED) {
            throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
        }
        return member;
    }
}
