package org.example.takecareofmyfridgebe.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NicknameAvailabilityRequest(
        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(max = 30, message = "닉네임은 30자 이하여야 합니다.")
        String nickname
) {

    public NicknameAvailabilityRequest {
        nickname = nickname == null ? null : nickname.strip();
    }
}
