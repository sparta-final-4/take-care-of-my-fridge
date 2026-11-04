package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.KeywordMatch;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.ImminentIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Reason;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 추천 카드에 넣을 이유 한 줄(25자 안팎)을 만든다. 스프링에 의존하지 않는 순수 클래스.
 * 우선순위: 사용자 요청(요리 이름 키워드·원하는 맛·분류) > 선택 재료 > 임박 재료 > 겹친 맛 > 쓰는 냉장고 재료 수.
 * 요리 이름 키워드는 어떻게 맞았는지(KeywordMatch)대로 쓴다. 사용자가 요리 이름(dishText) 없이 특징만 말했으면("차가운 요리")
 * 비교 없이 "냉면 요리예요", "메밀면을 쓰는 요리예요", "면 요리예요"처럼 쓴다.
 * 벡터 검색으로만 찾은 레시피는 "찾으신·비슷한"을 쓰지 않고 선택 재료·임박·맛으로 쓴다.
 * <pre>
 * 찾으신 냉면이에요 / 냉면과 비슷한 막국수예요 / 냉면처럼 메밀면을 쓰는 요리예요 / 냉면과 비슷한 면 요리예요
 * 매콤한 찌개 요리예요
 * 버섯·두부가 주재료예요
 * 내일까지인 고추를 쓸 수 있어요
 * 짭짤하고 감칠맛 나요
 * 냉장고 재료 3가지를 써요
 * </pre>
 */
public final class RecommendationReasonWriter {

    private static final String CATEGORY_PREFIX = "분류: ";
    private static final int MAX_TASTES = 2;

    private RecommendationReasonWriter() {
    }

    /**
     * @param dishText 사용자가 말한 요리 이름 (없으면 null)
     */
    public static String write(RecipeCandidate candidate, String dishText) {
        Reason reason = candidate.reason();
        KeywordMatch keywordMatch = reason.keywordMatch();
        if (keywordMatch != null && keywordMatch.type() == KeywordMatch.Type.VECTOR) {
            return otherReason(reason);
        }
        return keywordReason(keywordMatch, dishText, candidate.category())
                .or(() -> requestReason(reason))
                .orElseGet(() -> otherReason(reason));
    }

    // 선택 재료 > 임박 > 맛 > 쓰는 냉장고 재료 수
    private static String otherReason(Reason reason) {
        return selectedReason(reason)
                .or(() -> selectedReason(reason))
                .or(() -> imminentReason(reason))
                .or(() -> tasteReason(reason.matchedTastes()))
                .orElseGet(() -> reason.usedFridgeIngredients().isEmpty()
                        ? "새로 만들어 볼 만한 요리예요"
                        : "냉장고 재료 " + reason.usedFridgeIngredients().size() + "가지를 써요");
    }

    // 요리 이름 키워드가 어떻게 맞았는지대로 쓴다. 사용자가 말한 이름이 없으면 분류 이름("면 요리")으로
    private static Optional<String> keywordReason(KeywordMatch match, String dishText, String category) {
        if (match == null) {
            return Optional.empty();
        }
        String kind = kindOf(category);
        if (dishText == null) {
            // 사용자가 요리 이름을 말하지 않았으면("차가운 요리") 비교할 이름 없이 맞은 것만 쓴다
            return Optional.of(switch (match.type()) {
                case NAME, SPLIT, VECTOR -> kind + "예요";
                case EXPANDED -> match.keyword() + " 요리예요";
                case INGREDIENT -> match.ingredientName() + objectParticle(match.ingredientName()) + " 쓰는 요리예요";
            });
        }
        String dish = dishText;
        return Optional.of(switch (match.type()) {
            case NAME -> "찾으신 " + dish + copula(dish);
            case EXPANDED -> dish + withParticle(dish) + " 비슷한 " + match.keyword() + copula(match.keyword());
            case INGREDIENT -> dish + "처럼 " + match.ingredientName() + objectParticle(match.ingredientName()) + " 쓰는 요리예요";
            case SPLIT, VECTOR -> dish + withParticle(dish) + " 비슷한 " + kind + "예요";
        });
    }

    // 분류 이름을 요리 종류 말로 ("면류" → "면 요리", 없으면 "요리")
    private static String kindOf(String category) {
        if (category == null || category.isBlank()) {
            return "요리";
        }
        String base = category.endsWith("류") ? category.substring(0, category.length() - 1) : category;
        return base + " 요리";
    }

    // 원하는 맛·분류로
    private static Optional<String> requestReason(Reason reason) {
        Optional<String> category = findRequest(reason, CATEGORY_PREFIX);
        List<Member.Taste> tastes = reason.matchedWantedTastes();
        if (tastes.isEmpty() && category.isEmpty()) {
            return Optional.empty();
        }
        String taste = tastes.isEmpty() ? "" : adjective(tastes.get(0)) + " ";
        return Optional.of(taste + category.orElse("") + (category.isPresent() ? " 요리예요" : "요리예요"));
    }

    private static Optional<String> findRequest(Reason reason, String prefix) {
        return reason.matchedRequests().stream()
                .filter(request -> request.startsWith(prefix))
                .map(request -> request.substring(prefix.length()))
                .findFirst();
    }

    private static Optional<String> selectedReason(Reason reason) {
        List<String> names = reason.usedSelectedIngredients();
        if (names.isEmpty()) {
            return Optional.empty();
        }
        String joined = String.join("·", names.stream().limit(2).toList());
        return Optional.of(joined + subjectParticle(joined) + " 주재료예요");
    }

    // 가장 급한 임박 재료 하나
    private static Optional<String> imminentReason(Reason reason) {
        return reason.imminentIngredients().stream()
                .min(Comparator.comparingLong(ImminentIngredient::dDay))
                .map(imminent -> RecommendationPromptBuilder.dDayLabel(imminent.dDay()) + " " + imminent.name()
                        + objectParticle(imminent.name()) + " 쓸 수 있어요");
    }

    // "짭짤하고 감칠맛 나요"
    private static Optional<String> tasteReason(List<Member.Taste> tastes) {
        if (tastes.isEmpty()) {
            return Optional.empty();
        }
        List<Member.Taste> shown = tastes.stream().limit(MAX_TASTES).toList();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < shown.size() - 1; i++) {
            text.append(connective(shown.get(i))).append(' ');
        }
        return Optional.of(text.append(ending(shown.get(shown.size() - 1))).toString());
    }

    private static String adjective(Member.Taste taste) {
        return switch (taste) {
            case SALTY -> "짭짤한";
            case SWEET -> "달콤한";
            case SPICY -> "매콤한";
            case UMAMI -> "감칠맛 나는";
            case MILD -> "담백한";
        };
    }

    private static String connective(Member.Taste taste) {
        return switch (taste) {
            case SALTY -> "짭짤하고";
            case SWEET -> "달콤하고";
            case SPICY -> "매콤하고";
            case UMAMI -> "감칠맛 나고";
            case MILD -> "담백하고";
        };
    }

    private static String ending(Member.Taste taste) {
        return switch (taste) {
            case SALTY -> "짭짤해요";
            case SWEET -> "달콤해요";
            case SPICY -> "매콤해요";
            case UMAMI -> "감칠맛 나요";
            case MILD -> "담백해요";
        };
    }

    // "냉면이에요" / "막국수예요"
    private static String copula(String word) {
        return hasFinalConsonant(word) ? "이에요" : "예요";
    }

    private static String withParticle(String word) {
        return hasFinalConsonant(word) ? "과" : "와";
    }

    private static String subjectParticle(String word) {
        return hasFinalConsonant(word) ? "이" : "가";
    }

    private static String objectParticle(String word) {
        return hasFinalConsonant(word) ? "을" : "를";
    }

    // 마지막 글자 받침 여부 (한글이 아니면 받침 없음으로 본다)
    private static boolean hasFinalConsonant(String word) {
        char last = word.charAt(word.length() - 1);
        return last >= '가' && last <= '힣' && (last - '가') % 28 != 0;
    }
}
