package org.example.takecareofmyfridgebe.domain.chat.dto;

/**
 * AI에게 받는 요리 이름 목록의 종류. 종류마다 프롬프트(OpenAiSimilarDishGenerator)와 캐시 키 접두어(SimilarDishStore)만 다르고,
 * 캐시·정리·실패 처리는 같다(SimilarDishFinder).
 */
public enum DishListKind {

    // 요리 이름과 비슷한 요리 ("김피탕" → 탕수육, 깐풍기). prompts/chat-similar-dishes.txt
    // v4: 값에 정식 이름을 더해 목록이 아니라 객체로 둔다 (SimilarDishes)
    SIMILAR("chat:similar-dishes:v4:", "비슷한 요리"),
    // 넓은 요청에 맞는 요리 ("술안주" → 오징어볶음, 계란찜). prompts/chat-broad-dishes.txt
    // v2: 값 모양을 비슷한 요리와 같은 객체로 바꿨다
    BROAD("chat:broad-dishes:v2:", "넓은 요청 요리");

    private final String keyPrefix;
    private final String label;

    DishListKind(String keyPrefix, String label) {
        this.keyPrefix = keyPrefix;
        this.label = label;
    }

    public String keyPrefix() {
        return keyPrefix;
    }

    // 로그에 쓰는 이름
    public String label() {
        return label;
    }
}
