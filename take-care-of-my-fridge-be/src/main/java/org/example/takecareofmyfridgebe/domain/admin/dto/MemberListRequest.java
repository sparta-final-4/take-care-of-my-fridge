package org.example.takecareofmyfridgebe.domain.admin.dto;

import jakarta.validation.constraints.Min;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;

public record MemberListRequest(
        String keyword,
        Member.Role role,
        @Min(value = 1, message = "페이지는 1 이상이어야 합니다.") Integer page,
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.") Integer size
) {
    public MemberListRequest {
        page = page == null ? 1 : page;
        size = size == null ? 20 : size;
    }
}
