package org.example.takecareofmyfridgebe.domain.chat.ai;

import org.example.takecareofmyfridgebe.domain.chat.dto.DishListKind;

import java.util.List;

public interface SimilarDishGenerator {

    /**
     * 받은 글에 맞는 요리 이름 목록을 AI에 묻는다. 피할 맛·시간 같은 조건은 넘기지 않는다 (서버가 검색한 뒤 거른다).
     * 어떤 구현을 쓸지는 상담 답 생성기와 같은 설정(app.chat.reply-generator: stub / openai)으로 고른다.
     *
     * @param memberId 회원 ID (로그용)
     * @param kind     목록 종류. 비슷한 요리면 받은 글은 요리 이름, 넓은 요청 요리면 요청 표현("술안주")
     * @param text     받은 글
     * @return AI가 낸 요리 이름 (정리 전). 없으면 빈 목록
     * @throws RuntimeException AI 호출 실패, 시간 초과, 응답 JSON이 깨진 경우
     */
    List<String> generate(Long memberId, DishListKind kind, String text);

    // 요리 이름과 비슷한 요리
    default List<String> generate(Long memberId, String dishText) {
        return generate(memberId, DishListKind.SIMILAR, dishText);
    }
}
