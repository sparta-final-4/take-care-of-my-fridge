package org.example.takecareofmyfridgebe.domain.ingredient.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.repository.IngredientRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IngredientMatcher {

    // 대괄호([특가]) 및 소괄호((국산), (500g)) 안의 내용을 제거하기 위한 정규식
    private static final Pattern BRACKET_PATTERN = Pattern.compile("\\[[^\\]]*\\]|\\([^\\)]*\\)");

    // 숫자 바로 뒤의 단위(300g, 대파300g, 계란10구, 2개입 등)를 제거하기 위한 정규식
    // 영어 단위는 영어 단어 중간을 자르지 않도록 뒤에 영어 글자가 오지 않을 때만 제거한다.
    private static final Pattern QUANTITY_UNIT_PATTERN = Pattern.compile(
            "(?i)\\d+(?:\\.\\d+)?\\s*(?:(?:kg|g|mg|ml|l)(?![a-zA-Z])|구|개입|개|봉|팩|캔|모|단|줌|알|조각|마리|포기|줄|장|컵|병|통|박스|세트)"
    );

    // 연속된 공백을 하나의 공백으로 치환하기 위한 정규식
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private final IngredientRepository ingredientRepository;

    /**
     * 사용자가 입력한 재료명을 다듬어 표준 재료(Ingredient)와 매핑한다.
     * 사전에 없는 재료라도 에러가 아니며, 직접 입력용 기본 재료(ING_CUSTOM)로 매핑해 반환한다.
     * 매핑 규칙이 matchAll 한 곳에만 있도록 단건도 matchAll에 위임한다.
     *
     * @param rawName 사용자가 입력한 재료 원문 이름
     * @return 매핑된 표준 재료 엔티티 (사전 일치 재료 또는 ING_CUSTOM)
     * @throws BusinessException DB에 ING_CUSTOM 재료 행 자체가 설정되어 있지 않은 경우
     */
    public Ingredient match(String rawName) {
        return matchAll(List.of(rawName)).get(rawName);
    }

    /**
     * 여러 재료명을 한 번에 다듬어 표준 재료와 매핑한다.
     * 사전 조회는 findByNameIn 한 번으로 처리하고, 사전에 없는 이름이 있으면
     * 직접 입력용 기본 재료(ING_CUSTOM)를 한 번만 조회해 함께 사용한다.
     *
     * @param rawNames 사용자가 입력한 재료 원문 이름 목록
     * @return 원문 이름을 키로 하는 매핑된 표준 재료 맵 (사전 일치 재료 또는 ING_CUSTOM)
     * @throws BusinessException 사전에 없는 이름이 있는데 DB에 ING_CUSTOM 재료 행이 설정되어 있지 않은 경우
     */
    public Map<String, Ingredient> matchAll(List<String> rawNames) {
        Map<String, String> normalizedNames = new HashMap<>();
        for (String rawName : rawNames) {
            normalizedNames.putIfAbsent(rawName, normalize(rawName));
        }

        Set<String> lookupNames = normalizedNames.values().stream()
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toSet());
        // DB 비교는 대소문자를 구분하지 않으므로("spam" 조회 시 "Spam" 반환) 맵 키도 소문자로 맞춘다.
        Map<String, Ingredient> dictionary = lookupNames.isEmpty()
                ? Map.of()
                : ingredientRepository.findByNameIn(lookupNames).stream()
                        .collect(Collectors.toMap(
                                ingredient -> toLookupKey(ingredient.getName()),
                                Function.identity(),
                                (first, second) -> first
                        ));

        // TODO: 사전에서 못 찾은 이름들을 모아 Elasticsearch에 일괄 검색 (노리 + 동의어)

        Map<String, Ingredient> result = new HashMap<>();
        Ingredient customIngredient = null;
        for (Map.Entry<String, String> entry : normalizedNames.entrySet()) {
            Ingredient matched = dictionary.get(toLookupKey(entry.getValue()));
            if (matched == null) {
                if (customIngredient == null) {
                    customIngredient = findCustomIngredient();
                }
                matched = customIngredient;
            }
            result.put(entry.getKey(), matched);
        }
        return result;
    }

    private static String toLookupKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private Ingredient findCustomIngredient() {
        return ingredientRepository.findByIngredientCode(Ingredient.ING_CUSTOM)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOM_INGREDIENT_NOT_CONFIGURED));
    }

    /**
     * 입력된 재료명에서 괄호 안 내용과 숫자+단위를 제거하고 공백을 정리한다.
     * 다듬은 이름은 표준 재료 사전 매핑에만 사용하며, 실제 저장되는 rawName은 원문을 유지한다.
     */
    static String normalize(String rawName) {
        if (rawName == null) {
            return "";
        }
        String withoutBrackets = BRACKET_PATTERN.matcher(rawName).replaceAll(" ");
        String withoutQuantityUnit = QUANTITY_UNIT_PATTERN.matcher(withoutBrackets).replaceAll(" ");
        return WHITESPACE_PATTERN.matcher(withoutQuantityUnit).replaceAll(" ").strip();
    }
}
