package org.example.takecareofmyfridgebe.domain.chat.dto;

/**
 * 요리 이름 키워드로 찾은 레시피가 어떻게 맞았는지. 카드 이유와 정렬 1단계(키워드 일치)에 쓴다.
 *
 * @param type           맞은 방식
 * @param keyword        이름에 통째로 있는 키워드 (EXPANDED일 때)
 * @param ingredientName 키워드와 겹치는 레시피 재료 이름 (INGREDIENT일 때)
 */
public record KeywordMatch(
        Type type,
        String keyword,
        String ingredientName
) {

    public enum Type {
        // 사용자가 말한 요리 이름(dishText)이 레시피 이름에 통째로 있음
        NAME("이름"),
        // AI가 늘린 키워드가 레시피 이름에 통째로 있음
        EXPANDED("늘린 이름"),
        // 키워드 속 재료(메밀면 등)가 레시피 재료에 있음
        INGREDIENT("재료"),
        // 검색(단어 단위)으로 걸렸지만 이름·재료에 통째로는 없음 (예: 비빔국수 → 비빔쌀국수)
        SPLIT("쪼개짐"),
        // 키워드 검색이 0건이라 벡터 검색으로만 찾음
        VECTOR("벡터");

        private final String label;

        Type(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    // 이름·재료로 맞았는지. 정렬 1단계 키워드 일치, 1차 후보, "못 찾았어요" 안내는 이것만 센다
    public boolean strong() {
        return type == Type.NAME || type == Type.EXPANDED || type == Type.INGREDIENT;
    }

    public static KeywordMatch of(Type type) {
        return new KeywordMatch(type, null, null);
    }
}
