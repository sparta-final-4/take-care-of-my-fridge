package org.example.takecareofmyfridgebe.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatCandidateProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.recommend.SpicyRecipeDetector;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.dto.RecipeTasteLevel;
import org.example.takecareofmyfridgebe.domain.search.service.RecipeCandidateSearchService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 레시피 맛·매운 정도를 읽는다. 상담 후보(RecipeCandidateFacade)와 밀키트 연결 레시피(RecipeCardDescriber)가 같은 기준으로 쓴다.
 * 여러 도메인(냉장고·레시피·상품·검색)을 조회해서 facade 층에 둔다.
 * <p>
 * 이미 아는 값(ES 검색 결과, 색인 때 정한 tastes·spicy_level)을 먼저 쓰고, 모르는 레시피는 ES에서 id로 한 번에 읽는다.
 * 그래도 모르는 레시피(ES를 쓰지 않음·조회 실패·인덱스에 없음)만 DB 맛 데이터·재료 이름으로 계산한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecipeTasteReader {

    private final RecipeService recipeService;
    private final RecipeCandidateSearchService recipeCandidateSearchService;
    private final RecipeSearchProperties recipeSearchProperties;
    private final ChatCandidateProperties chatCandidateProperties;

    /**
     * 상담 후보의 맛·매운 정도. 매운맛이 원하는 맛·피할 맛·회원 선호 맛 어디에도 없으면 DB로 매운 정도를 계산하지 않는다.
     *
     * @param known             ES 후보 검색 결과의 레시피 ID별 맛·매운 정도 (DB 경로·벡터 보충은 비어 있다)
     * @param readUnknownFromEs 모르는 레시피를 ES에서 id로 읽을지 (ES 경로일 때만 true)
     */
    TasteDetails readForCandidates(Collection<Long> recipeIds, Map<Long, RecipeTasteLevel> known, boolean readUnknownFromEs,
                                   RecipeQuery query, List<Member.Taste> memberTastes) {
        return tasteDetails(recipeIds, known, readUnknownFromEs, spicyMatters(query, memberTastes), query.memberId());
    }

    /**
     * 밀키트 연결 레시피의 맛·매운 정도. ES를 쓰는 설정이면 ES에서 id로 읽는다.
     * 피할 맛·원하는 맛을 여기서 모르므로 DB로 계산할 때도 매운 정도를 늘 계산한다 (밀키트 연결 레시피는 몇 건뿐이다).
     */
    TasteDetails readForMealkits(Collection<Long> recipeIds, Long memberId) {
        return tasteDetails(recipeIds, Map.of(), recipeSearchProperties.useElasticSearch(), true, memberId);
    }

    /**
     * 레시피 맛·매운 정도. 이미 아는 값(ES 검색 결과)을 쓰고, readEs면 모르는 레시피를 ES에서 id로 한 번에 읽는다.
     * 그래도 모르는 레시피(ES를 쓰지 않음·조회 실패·인덱스에 없음)만 DB로 계산한다.
     */
    private TasteDetails tasteDetails(Collection<Long> recipeIds, Map<Long, RecipeTasteLevel> known, boolean readEs,
                                      boolean spicyMatters, Long memberId) {
        Map<Long, RecipeTasteLevel> levels = new HashMap<>();
        List<Long> unknown = new ArrayList<>();
        for (Long id : recipeIds) {
            RecipeTasteLevel level = known.get(id);
            if (level == null) {
                unknown.add(id);
            } else {
                levels.put(id, level);
            }
        }
        if (!unknown.isEmpty() && readEs) {
            Map<Long, RecipeTasteLevel> read = readTasteLevels(unknown, memberId);
            levels.putAll(read);
            unknown = unknown.stream().filter(id -> !read.containsKey(id)).toList();
        }
        TasteDetails tastes = TasteDetails.of(levels);
        return unknown.isEmpty() ? tastes : tastes.plus(tasteDetailsFromDb(unknown, spicyMatters));
    }

    /**
     * 레시피별 맛과 매운 레시피 두 단계.
     *
     * @param tastes 레시피 ID별 맛 (확실히 매운 레시피면 SPICY가 들어 있다)
     */
    record TasteDetails(Map<Long, Set<Member.Taste>> tastes, SpicyRecipes spicy) {

        // ES 값: HOT = 확실히 매움, MILD 이상 = 매울 수 있음. 색인할 때 HOT이면 맛에 SPICY를 더해 두었다
        static TasteDetails of(Map<Long, RecipeTasteLevel> levels) {
            Map<Long, Set<Member.Taste>> tastes = new HashMap<>();
            Set<Long> definite = new HashSet<>();
            Set<Long> maybe = new HashSet<>();
            levels.forEach((id, level) -> {
                if (!level.tastes().isEmpty()) {
                    tastes.put(id, level.tastes());
                }
                if (level.definitelySpicy()) {
                    definite.add(id);
                }
                if (level.maybeSpicy()) {
                    maybe.add(id);
                }
            });
            return new TasteDetails(tastes, new SpicyRecipes(Set.copyOf(definite), Set.copyOf(maybe)));
        }

        TasteDetails plus(TasteDetails other) {
            Map<Long, Set<Member.Taste>> merged = new HashMap<>(tastes);
            merged.putAll(other.tastes());
            return new TasteDetails(merged, spicy.plus(other.spicy()));
        }
    }

    // ES에서 레시피 id로 맛·매운 정도를 읽는다. 실패하면 빈 값을 돌려줘 호출하는 쪽이 DB로 계산하게 한다
    private Map<Long, RecipeTasteLevel> readTasteLevels(Collection<Long> recipeIds, Long memberId) {
        try {
            return recipeCandidateSearchService.findTasteLevels(recipeIds);
        } catch (IOException | RuntimeException e) {
            log.warn("레시피 맛·매운 정도 ES 조회 실패, DB로 계산 - memberId: {}, 레시피 수: {}, 원인: {}",
                    memberId, recipeIds.size(), e.toString());
            return Map.of();
        }
    }

    // DB 대체 경로와 ES에서 읽지 못한 레시피: 맛 데이터를 읽고, 매운맛이 중요하면 재료 이름으로 매운 정도를 계산해 확실히 매우면 SPICY를 더한다
    private TasteDetails tasteDetailsFromDb(Collection<Long> recipeIds, boolean spicyMatters) {
        SpicyRecipes spicy = spicyMatters ? spicyRecipes(recipeIds) : SpicyRecipes.NONE;
        return new TasteDetails(withDefinitelySpicy(recipeService.findTastesByRecipeIds(recipeIds), spicy), spicy);
    }

    /**
     * 재료로 가른 매운 레시피 두 단계.
     *
     * @param definite 확실히 매운 레시피 (청양고추·고춧가루·고추장·김치류 등). 원하는 맛 매운맛과 맞은 것으로 센다
     * @param maybe    매울 수 있는 레시피 (확실히 매운 것 + 홍고추·청고추, 정확한 이름 고추·붉은고추·고추냉이). 피할 맛 매운맛이면 이것까지 뺀다
     */
    record SpicyRecipes(Set<Long> definite, Set<Long> maybe) {
        static final SpicyRecipes NONE = new SpicyRecipes(Set.of(), Set.of());

        SpicyRecipes plus(SpicyRecipes other) {
            return new SpicyRecipes(
                    Stream.concat(definite.stream(), other.definite().stream()).collect(Collectors.toUnmodifiableSet()),
                    Stream.concat(maybe.stream(), other.maybe().stream()).collect(Collectors.toUnmodifiableSet()));
        }
    }

    // 원하는 맛·피할 맛·회원 선호 맛 어디에 매운맛이 있는지. 없으면 DB로 매운 정도를 계산하지 않는다
    private static boolean spicyMatters(RecipeQuery query, List<Member.Taste> memberTastes) {
        return query.wantedTastes().contains(Member.Taste.SPICY)
                || query.avoidTastes().contains(Member.Taste.SPICY)
                || memberTastes.contains(Member.Taste.SPICY);
    }

    // 재료 이름으로 매운 정도를 계산한다 (재료 이름 조회 1번). 레시피 색인(RecipeDocumentReader)과 같은 기준이다
    private SpicyRecipes spicyRecipes(Collection<Long> recipeIds) {
        if (recipeIds.isEmpty()) {
            return SpicyRecipes.NONE;
        }
        Map<Long, List<String>> texts = recipeService.findIngredientTexts(recipeIds);
        List<String> definiteWords = chatCandidateProperties.spicyIngredients();
        List<String> maybeWords = Stream.concat(definiteWords.stream(), chatCandidateProperties.mildSpicyIngredients().stream()).toList();
        return new SpicyRecipes(
                SpicyRecipeDetector.spicyRecipeIds(texts, definiteWords, chatCandidateProperties.spicyExclusions()),
                SpicyRecipeDetector.spicyRecipeIds(texts, maybeWords, chatCandidateProperties.mildSpicyExactIngredients(),
                        chatCandidateProperties.spicyExclusions()));
    }

    // 맛 데이터에 매운맛이 없어도 확실히 매운 재료가 들어 있으면 매운맛을 더한다 (매울 수 있는 것은 더하지 않는다)
    private static Map<Long, Set<Member.Taste>> withDefinitelySpicy(Map<Long, Set<Member.Taste>> recipeTastes, SpicyRecipes spicy) {
        if (spicy.definite().isEmpty()) {
            return recipeTastes;
        }
        Map<Long, Set<Member.Taste>> merged = new HashMap<>(recipeTastes);
        for (Long id : spicy.definite()) {
            Set<Member.Taste> tastes = new HashSet<>(merged.getOrDefault(id, Set.of()));
            tastes.add(Member.Taste.SPICY);
            merged.put(id, tastes);
        }
        return merged;
    }
}
