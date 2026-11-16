package org.example.takecareofmyfridgebe.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.ai.SimilarDishGenerator;
import org.example.takecareofmyfridgebe.domain.chat.dto.DishListKind;
import org.example.takecareofmyfridgebe.domain.chat.service.SimilarDishStore;
import org.example.takecareofmyfridgebe.domain.chat.text.ChatTexts;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * AI에게 요리 이름 목록을 받는다. 캐시(SimilarDishStore)가 있으면 그 값을 쓰고 AI를 부르지 않는다.
 * 없으면 AI(SimilarDishGenerator)를 한 번 부르고, 정리한 값이 있으면 캐시에 둔다(빈 목록은 두지 않는다).
 * AI 호출이 실패하면(오류·시간 초과·JSON 깨짐) WARN만 남기고 빈 목록으로 진행한다.
 * <ul>
 *   <li>비슷한 요리 ({@link #find}): 요리 이름 그대로 맞은 레시피가 없을 때 RecipeCandidateFacade가 부른다 (이름 조각·벡터 보충은 그대로 돈다)</li>
 *   <li>넓은 요청 요리 ({@link #findBroad}): Jev on 모드에서 요청 종류가 넓은 요청일 때 JevIntentResolver가 부른다</li>
 * </ul>
 * Redis(캐시)와 AI 두 곳을 조율해서 facade 층에 둔다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SimilarDishFinder {

    // 검색에 쓸 요리 이름 최대 개수 (비슷한 요리 프롬프트는 3~5개, 넓은 요청 프롬프트는 5개를 달라고 한다)
    static final int MAX_SIMILAR_DISHES = 5;

    private final SimilarDishStore similarDishStore;
    private final SimilarDishGenerator similarDishGenerator;

    /**
     * @param dishText 사용자가 말한 요리 이름
     * @return 비슷한 요리 이름 (캐시 또는 AI). 없거나 AI가 실패하면 빈 목록
     */
    public List<String> find(Long memberId, String dishText) {
        return find(memberId, DishListKind.SIMILAR, dishText);
    }

    /**
     * @param request 넓은 요청 표현 (OpenAI preferenceText, 없으면 메시지). 캐시 키가 된다
     * @return 요청에 맞는 요리 이름 (캐시 또는 AI). 없거나 AI가 실패하면 빈 목록
     */
    public List<String> findBroad(Long memberId, String request) {
        return find(memberId, DishListKind.BROAD, request);
    }

    private List<String> find(Long memberId, DishListKind kind, String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Optional<List<String>> cached = similarDishStore.find(memberId, kind, text);
        if (cached.isPresent()) {
            log.info("상담 {} 캐시 사용 - memberId: {}, 글: {}, 요리: {}", kind.label(), memberId, text, cached.get());
            return cached.get();
        }
        List<String> generated;
        try {
            generated = similarDishGenerator.generate(memberId, kind, text);
        } catch (RuntimeException e) {
            log.warn("상담 {} AI 실패, 빈 목록으로 - memberId: {}, 글: {}, 원인: {}", kind.label(), memberId, text, e.toString());
            return List.of();
        }
        List<String> dishes = clean(text, generated);
        log.info("상담 {} AI - memberId: {}, 글: {}, AI: {}, 쓴 값: {}", kind.label(), memberId, text, generated, dishes);
        similarDishStore.save(memberId, kind, text, dishes);
        return dishes;
    }

    // 앞뒤 공백·빈 이름을 빼고, 받은 글 자신과 띄어쓰기만 다른 중복을 빼서 최대 5개
    static List<String> clean(String dishText, List<String> dishes) {
        if (dishes == null) {
            return List.of();
        }
        Set<String> seen = new HashSet<>();
        seen.add(ChatTexts.removeWhitespace(dishText));
        return dishes.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .filter(name -> seen.add(ChatTexts.removeWhitespace(name)))
                .limit(MAX_SIMILAR_DISHES)
                .toList();
    }
}
