package org.example.takecareofmyfridgebe.domain.chat.reply;

import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidates.EmptyReason;

import java.util.List;

/**
 * 요리 이름을 말했는데 레시피 카드가 0장일 때 보내는 답 글. 스프링에 의존하지 않는 순수 클래스. 답 생성 AI 없이 이 글을 그대로 보낸다.
 * <pre>
 * (못 찾음) 김피탕 레시피는 아직 없어요. 다른 요리 이름이나 갖고 있는 재료로 물어봐 주세요.
 * (뺄 재료) 새우볶음밥에는 새우, 버터가 들어 있어서 추천에서 뺐어요. 다른 요리를 물어봐 주세요.
 * (뺄 재료를 모름) 새우볶음밥에는 못 드시는 재료가 들어 있어서 추천에서 뺐어요. 다른 요리를 물어봐 주세요.
 * </pre>
 * 요리 이름은 사용자가 쓴 그대로 넣고, 이/가는 마지막 재료 이름의 받침으로 고른다.
 * 이유가 없는 0장(시간 등 다른 조건)은 ChatStreamFacade의 정해진 답을 그대로 쓴다.
 */
public final class NoRecipeReplyWriter {

    private static final String INGREDIENT_DELIMITER = ", ";

    private NoRecipeReplyWriter() {
    }

    public static String write(EmptyReason reason) {
        String dish = reason.dish();
        return switch (reason.type()) {
            case NOT_FOUND -> dish + " 레시피는 아직 없어요. 다른 요리 이름이나 갖고 있는 재료로 물어봐 주세요.";
            case EXCLUDED -> dish + "에는 " + excludedPhrase(reason.excludedIngredients())
                    + " 들어 있어서 추천에서 뺐어요. 다른 요리를 물어봐 주세요.";
        };
    }

    // "새우, 버터가" / 재료를 모르면 "못 드시는 재료가"
    private static String excludedPhrase(List<String> ingredients) {
        if (ingredients.isEmpty()) {
            return "못 드시는 재료가";
        }
        String last = ingredients.get(ingredients.size() - 1);
        return String.join(INGREDIENT_DELIMITER, ingredients) + subjectParticle(last);
    }

    private static String subjectParticle(String word) {
        return hasFinalConsonant(word) ? "이" : "가";
    }

    private static boolean hasFinalConsonant(String word) {
        if (word == null || word.isEmpty()) {
            return false;
        }
        char last = word.charAt(word.length() - 1);
        return last >= '가' && last <= '힣' && (last - '가') % 28 != 0;
    }
}
