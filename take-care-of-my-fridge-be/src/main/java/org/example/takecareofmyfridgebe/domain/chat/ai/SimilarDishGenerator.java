package org.example.takecareofmyfridgebe.domain.chat.ai;

import java.util.List;

public interface SimilarDishGenerator {

    /**
     * 요리 이름과 비슷한 요리 이름을 AI에 묻는다. 피할 맛·시간 같은 조건은 넘기지 않는다 (서버가 검색한 뒤 거른다).
     * 어떤 구현을 쓸지는 상담 답 생성기와 같은 설정(app.chat.reply-generator: stub / openai)으로 고른다.
     *
     * @param memberId 회원 ID (로그용)
     * @param dishText 사용자가 말한 요리 이름
     * @return AI가 낸 비슷한 요리 이름 (정리 전). 없으면 빈 목록
     * @throws RuntimeException AI 호출 실패, 시간 초과, 응답 JSON이 깨진 경우
     */
    List<String> generate(Long memberId, String dishText);
}
