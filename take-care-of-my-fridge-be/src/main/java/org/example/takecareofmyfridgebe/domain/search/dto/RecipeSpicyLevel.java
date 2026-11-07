package org.example.takecareofmyfridgebe.domain.search.dto;

/**
 * 레시피 인덱스 spicy_level 값. 색인할 때 재료로 가른다 (app.chat.candidate 매운 재료 목록).
 * <ul>
 *   <li>HOT: 확실히 매운 재료(청양고추·고춧가루·고추장·김치류 등)가 들어 있거나, 레시피 맛 데이터가 매운맛이다</li>
 *   <li>MILD: 확실히 매운 재료는 없고 매울 수 있는 재료(홍고추·청고추, 정확한 이름 고추·붉은고추·고추냉이)만 들어 있다</li>
 *   <li>NONE: 둘 다 없다</li>
 * </ul>
 * 매운 재료 목록을 바꾸면 레시피 인덱스를 다시 색인해야 반영된다.
 */
public enum RecipeSpicyLevel {
    NONE, MILD, HOT
}
