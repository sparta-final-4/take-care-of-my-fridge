package org.example.takecareofmyfridgebe.domain.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;

import java.util.List;
import java.util.Set;

public record SignUpRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "올바른 이메일 형식으로 입력해 주세요.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(min = 8, max = 30, message = "비밀번호는 8자 이상 30자 이하여야 합니다.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9])[\\x21-\\x7E]+$",
                message = "비밀번호는 영문, 숫자, 특수문자를 모두 포함해야 하며 공백·한글은 사용할 수 없습니다.")
        String password,

        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(max = 30, message = "닉네임은 30자 이하여야 합니다.")
        String nickname,

        @NotNull(message = "맛 선호 목록을 보내 주세요.")
        List<Member.@NotNull(message = "맛 선호에 빈 값은 사용할 수 없습니다.") Taste> tastes
) {

    public SignUpRequest {
        nickname = nickname == null ? null : nickname.strip();
    }

    public Member toEntity(String passwordHash, Set<Member.Taste> uniqueTastes) {
        return Member.builder()
                .email(email)
                .passwordHash(passwordHash)
                .nickname(nickname)
                .tastes(uniqueTastes)
                .build();
    }
}
