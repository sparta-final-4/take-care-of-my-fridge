package org.example.takecareofmyfridgebe.domain.member.dto;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;

public record SignUpResponse(Long memberId) {

    public static SignUpResponse from(Member member) {
        return new SignUpResponse(member.getId());
    }
}
