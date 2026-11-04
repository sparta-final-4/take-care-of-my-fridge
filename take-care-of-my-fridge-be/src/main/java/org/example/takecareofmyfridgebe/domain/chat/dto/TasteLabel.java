package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;

/**
 * 맛의 한글 이름. 프론트 회원 맛 선택(domains/member/labels.ts)과 같은 이름을 쓴다.
 */
public final class TasteLabel {

    private TasteLabel() {
    }

    public static String of(Member.Taste taste) {
        return switch (taste) {
            case SALTY -> "짠맛";
            case SWEET -> "단맛";
            case SPICY -> "매운맛";
            case UMAMI -> "감칠맛";
            case MILD -> "담백한 맛";
        };
    }
}
