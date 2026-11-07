package org.example.takecareofmyfridgebe.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatAllergyGroupProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatAllergyWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatMessageService;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 이 상담에서 사용자가 말한 알레르기·못 먹는 재료를 찾아, 추천에서 뺄 재료 ID로 넓힌다 (AI가 놓쳐도 서버가 확인).
 * <ul>
 *   <li>이번 말과 이 상담의 사용자 말(안전 장치로 걸러진 말은 뺌)을 모두 본다. 그래서 다시 추천·새 추천 모두 같은 상담 안에서는 계속 뺀다</li>
 *   <li>○○이 묶음 말(app.chat.allergy-groups)이면 묶음 재료, 재료 사전에 이름이 그대로 있으면 그 재료를 쓴다. 둘 다 아니면 버린다 ("매운 거 못 먹어"의 "거")</li>
 *   <li>재료는 이름에 그 이름이 들어 있는 재료로 넓힌다 (땅콩 → 땅콩버터·땅콩가루, 새우 → 건새우·새우젓). 한 글자("게", "잣")는 그 글자로 시작하는 재료만 (게살·잣가루).
 *       재료마다 넓히지 않을 이름(땅콩 → 땅콩호박, 굴 → 굴비, 마 → 마늘)은 그 재료를 넓힐 때만 뺀다 ("마늘 알레르기"면 마늘가루는 뺀다)</li>
 *   <li>재료 조회가 실패하면(DB 장애) 경고만 남기고 알레르기 없이 계속한다</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AllergyExclusionFinder {

    private final ChatMessageService chatMessageService;
    private final IngredientService ingredientService;
    private final ChatAllergyWordProperties chatAllergyWordProperties;
    private final ChatAllergyGroupProperties chatAllergyGroupProperties;

    /**
     * 찾은 알레르기.
     *
     * @param names         사용자가 말한 이름 (묶음 말이면 묶음 말, 예: "땅콩", "견과류"). 안내 문장에 쓴다
     * @param ingredientIds 추천에서 뺄 재료 ID (넓힌 것)
     */
    public record Allergens(List<String> names, List<Long> ingredientIds) {
        public static final Allergens NONE = new Allergens(List.of(), List.of());

        public boolean isEmpty() {
            return ingredientIds.isEmpty();
        }
    }

    public Allergens find(Long sessionId, String currentMessage) {
        try {
            List<String> messages = new ArrayList<>(chatMessageService.findUserContents(sessionId));
            messages.add(currentMessage);
            List<List<String>> mentions = messages.stream()
                    .flatMap(message -> AllergyWordChecker.mentions(message, chatAllergyWordProperties).stream())
                    .toList();
            return mentions.isEmpty() ? Allergens.NONE : resolve(mentions);
        } catch (RuntimeException e) {
            log.warn("상담 알레르기 재료 찾기 실패, 알레르기 없이 계속 - sessionId: {}, 원인: {}", sessionId, e.toString());
            return Allergens.NONE;
        }
    }

    private Allergens resolve(List<List<String>> mentions) {
        // 묶음 말이 있는 말은 묶음으로, 나머지는 재료 사전에서 이름을 확인한다 (한 번에 조회)
        List<ChatAllergyGroupProperties.Group> groups = new ArrayList<>();
        List<List<String>> plain = new ArrayList<>();
        for (List<String> candidates : mentions) {
            findGroup(candidates).ifPresentOrElse(groups::add, () -> plain.add(candidates));
        }
        Set<String> lookup = new LinkedHashSet<>();
        plain.forEach(lookup::addAll);
        groups.forEach(group -> lookup.addAll(group.ingredients()));
        Map<String, List<Ingredient>> found = ingredientService.findContainingNames(lookup);

        Set<String> names = new LinkedHashSet<>();
        Set<Long> ids = new LinkedHashSet<>();
        for (List<String> candidates : plain) {
            for (String candidate : candidates) {
                List<Ingredient> ingredients = found.getOrDefault(candidate, List.of());
                if (ingredients.stream().anyMatch(ingredient -> ingredient.getName().equals(candidate))) {
                    names.add(candidate);
                    addSameKind(ids, candidate, ingredients);
                    break;
                }
            }
        }
        for (ChatAllergyGroupProperties.Group group : groups) {
            names.add(group.word());
            group.ingredients().forEach(name -> addSameKind(ids, name, found.getOrDefault(name, List.of())));
        }
        return ids.isEmpty() ? Allergens.NONE : new Allergens(List.copyOf(names), List.copyOf(ids));
    }

    private Optional<ChatAllergyGroupProperties.Group> findGroup(List<String> candidates) {
        return candidates.stream()
                .flatMap(candidate -> chatAllergyGroupProperties.groups().stream()
                        .filter(group -> group.word() != null && group.word().equals(candidate)))
                .findFirst();
    }

    // word를 넓힌 재료 중 그 재료의 넓히지 않을 이름(땅콩 → 땅콩호박, 굴 → 굴비)과 같은 재료는 뺀다
    private void addSameKind(Set<Long> ids, String word, List<Ingredient> ingredients) {
        Set<String> otherKinds = chatAllergyWordProperties.notSameKindOf(word);
        for (Ingredient ingredient : ingredients) {
            if (!otherKinds.contains(compact(ingredient.getName()))) {
                ids.add(ingredient.getId());
            }
        }
    }

    private static String compact(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }
}
