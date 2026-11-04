package org.example.takecareofmyfridgebe.domain.chat.dto;

import java.util.List;

/**
 * 추천 답을 만들 재료: 카드 이벤트, AI에 넘길 후보 정보(줄 글), 서버가 소개 앞에 직접 붙일 안내 문장.
 *
 * @param event         recommendations 이벤트 내용
 * @param candidateInfo 설명 프롬프트에 넣을 사용자 요청·추천 후보 (중괄호 없는 줄 글)
 * @param notices       소개 맨 앞에 서버가 붙일 안내 문장 ("말씀하신 두부는 지금 냉장고에 없어서 준비가 필요해요"). 없으면 빈 목록
 */
public record RecommendationReply(
        ChatRecommendationsEventResponse event,
        String candidateInfo,
        List<String> notices
) {

    public RecommendationReply {
        notices = notices == null ? List.of() : List.copyOf(notices);
    }

    public RecommendationReply(ChatRecommendationsEventResponse event, String candidateInfo) {
        this(event, candidateInfo, List.of());
    }

    /**
     * 화면·저장용 안내 글. 문장마다 마침표를 붙여 한 칸씩 띄운다. 없으면 빈 문자열
     */
    public String noticeText() {
        return String.join(" ", notices.stream()
                .map(String::strip)
                .map(line -> line.matches(".*[.!?]$") ? line : line + ".")
                .toList());
    }
}
