package org.example.takecareofmyfridgebe.domain.chat.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 추천 답을 만들 재료: 카드 이벤트, AI에 넘길 후보 정보(줄 글), 서버가 소개 앞에 직접 붙일 안내 문장.
 *
 * @param event         recommendations 이벤트 내용
 * @param candidateInfo 설명 프롬프트에 넣을 사용자 요청·추천 후보 (중괄호 없는 줄 글)
 * @param notices       소개 맨 앞에 서버가 붙일 안내 문장 ("말씀하신 두부는 지금 냉장고에 없어서 준비가 필요해요"). 없으면 빈 목록
 * @param fixedReply    AI를 부르지 않고 서버가 보낼 답 전체 (밀키트만 보여 주는 답). AI가 설명하면 null
 */
public record RecommendationReply(
        ChatRecommendationsEventResponse event,
        String candidateInfo,
        List<String> notices,
        String fixedReply
) {

    public RecommendationReply {
        notices = notices == null ? List.of() : List.copyOf(notices);
    }

    public RecommendationReply(ChatRecommendationsEventResponse event, String candidateInfo, List<String> notices) {
        this(event, candidateInfo, notices, null);
    }

    public RecommendationReply(ChatRecommendationsEventResponse event, String candidateInfo) {
        this(event, candidateInfo, List.of());
    }

    // 밀키트만 보여 주는 답: 레시피 카드가 없어 AI 설명 없이 서버 글만 보낸다
    public static RecommendationReply fixed(ChatRecommendationsEventResponse event, String reply) {
        return new RecommendationReply(event, null, List.of(), reply);
    }

    // 같은 답에 밀키트 카드를 붙인다
    public RecommendationReply withEvent(ChatRecommendationsEventResponse changed) {
        return new RecommendationReply(changed, candidateInfo, notices, fixedReply);
    }

    // 안내 문장을 맨 앞에 더한다
    public RecommendationReply withNoticeFirst(String notice) {
        List<String> merged = new ArrayList<>();
        merged.add(notice);
        merged.addAll(notices);
        return new RecommendationReply(event, candidateInfo, merged, fixedReply);
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
