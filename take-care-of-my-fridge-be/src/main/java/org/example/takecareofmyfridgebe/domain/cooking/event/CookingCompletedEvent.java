package org.example.takecareofmyfridgebe.domain.cooking.event;

// 상담과 이은 요리 완료가 커밋됐다는 사실. 상담 도메인이 받아 상담을 끝낸다
public record CookingCompletedEvent(
        Long memberId,
        Long chatSessionId,
        Long cookingLogId
) {
}
