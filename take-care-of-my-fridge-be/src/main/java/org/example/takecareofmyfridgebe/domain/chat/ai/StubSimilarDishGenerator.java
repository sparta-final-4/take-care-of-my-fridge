package org.example.takecareofmyfridgebe.domain.chat.ai;

import org.example.takecareofmyfridgebe.domain.chat.dto.DishListKind;
import org.example.takecareofmyfridgebe.domain.chat.dto.SimilarDishes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;


// OpenAI 키 없이 돌릴 때 쓰는 가짜 요리 이름 목록. 늘 빈 목록 (비슷한 요리 없이 이름 조각·벡터 보충으로, 넓은 요청은 냉장고 후보로 찾는다)
@Component
@ConditionalOnProperty(prefix = "app.chat", name = "reply-generator", havingValue = "stub", matchIfMissing = true)
public class StubSimilarDishGenerator implements SimilarDishGenerator {

    @Override
    public SimilarDishes generate(Long memberId, DishListKind kind, String text) {
        return SimilarDishes.NONE;
    }
}
