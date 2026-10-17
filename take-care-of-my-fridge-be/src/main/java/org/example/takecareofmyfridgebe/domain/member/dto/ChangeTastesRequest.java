package org.example.takecareofmyfridgebe.domain.member.dto;

import jakarta.validation.constraints.NotNull;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;

import java.util.List;

public record ChangeTastesRequest(
        @NotNull(message = "맛 선호 목록을 보내 주세요.")
        List<Member.@NotNull(message = "맛 선호에 빈 값은 사용할 수 없습니다.") Taste> tastes
) {
}
