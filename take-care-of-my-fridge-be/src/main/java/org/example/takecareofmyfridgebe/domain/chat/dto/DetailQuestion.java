package org.example.takecareofmyfridgebe.domain.chat.dto;

import java.util.regex.Pattern;

/**
 * 레시피 자세히(RECIPE_DETAIL) 질문 종류. 레시피 자세히 AI에는 사용자 말 대신 이 종류로 만든 질문을 넘긴다.
 * "두 번째 거 어떻게 만들어?"의 "두 번째"를 AI가 단계 번호로 알아듣지 않게 하려는 것이다.
 * OTHER는 사용자 말에서 레시피를 가리키는 말("두 번째 거", "그거", "아까 그")만 지우고 넘긴다.
 */
public enum DetailQuestion {
    HOW_TO("이 레시피 만드는 법을 처음부터 끝까지 알려줘"),
    TIME("이 레시피 조리 시간이 얼마나 걸려?"),
    INGREDIENTS("이 레시피 재료를 알려줘"),
    TIP("이 레시피를 맛있게 만드는 팁을 알려줘"),
    OTHER(null);

    // 가리키는 말: "(아까) (그) 첫/두/세 번째 거(는)", "그거·그것·저거·이거(는)", "아까 그"
    private static final Pattern ORDINAL_POINTER = Pattern.compile(
            "(아까\\s*)?(그\\s*)?(첫|두|세|네|다섯)\\s*번째\\s*(거|것|꺼|레시피|요리)?\\s*(는|은|를|을|이|가|의)?");
    private static final Pattern THAT_POINTER = Pattern.compile("(아까\\s*)?(그거|그것|저거|이거)(는|은|를|을|이|가|의)?");
    private static final Pattern EARLIER_POINTER = Pattern.compile("아까\\s*그\\s+");
    // 가리키는 말을 지우고 남은 게 없을 때 넘길 질문
    private static final String DEFAULT_QUESTION = "이 레시피에 대해 알려줘";

    private final String question;

    DetailQuestion(String question) {
        this.question = question;
    }

    /**
     * 레시피 자세히 AI에 넘길 질문. 종류가 없으면(null) OTHER처럼 사용자 말에서 가리키는 말만 지운다.
     */
    public static String questionFor(DetailQuestion type, String message) {
        if (type != null && type.question != null) {
            return type.question;
        }
        return withoutPointers(message);
    }

    static String withoutPointers(String message) {
        String text = ORDINAL_POINTER.matcher(message == null ? "" : message).replaceAll(" ");
        text = THAT_POINTER.matcher(text).replaceAll(" ");
        text = EARLIER_POINTER.matcher(text).replaceAll(" ");
        text = text.replaceAll("\\s+", " ").strip();
        return text.isEmpty() || text.matches("[?.!~]*") ? DEFAULT_QUESTION : text;
    }
}
