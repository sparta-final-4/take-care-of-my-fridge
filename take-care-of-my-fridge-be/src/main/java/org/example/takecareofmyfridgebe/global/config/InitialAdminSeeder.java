package org.example.takecareofmyfridgebe.global.config;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.member.dto.SignUpRequest;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class InitialAdminSeeder implements ApplicationRunner {

    private final Environment environment;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = environment.getProperty("ADMIN_BOOTSTRAP_EMAIL");
        String password = environment.getProperty("ADMIN_BOOTSTRAP_PASSWORD");
        String nickname = environment.getProperty("ADMIN_BOOTSTRAP_NICKNAME");
        if (email == null && password == null && nickname == null) {
            return;
        }
        if (email == null || email.isBlank() || password == null || password.isBlank()
                || nickname == null || nickname.isBlank()) {
            throw new IllegalStateException("첫 관리자 환경변수 3개를 모두 설정해야 합니다.");
        }
        if (memberRepository.existsByRole(Member.Role.ADMIN)) {
            return;
        }

        String normalizedNickname = nickname.strip();
        if (!validator.validateValue(SignUpRequest.class, "email", email).isEmpty()
                || !validator.validateValue(SignUpRequest.class, "password", password).isEmpty()
                || !validator.validateValue(SignUpRequest.class, "nickname", normalizedNickname).isEmpty()) {
            throw new IllegalStateException("첫 관리자 환경변수가 회원가입 입력 규칙에 맞지 않습니다.");
        }
        if (memberRepository.existsByEmail(email)
                || memberRepository.existsByNickname(normalizedNickname)) {
            throw new IllegalStateException("첫 관리자 이메일 또는 닉네임이 이미 사용 중입니다.");
        }

        memberRepository.saveAndFlush(Member.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .nickname(normalizedNickname)
                .role(Member.Role.ADMIN)
                .build());
    }
}
