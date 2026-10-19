package org.example.takecareofmyfridgebe.domain.ingredient.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.repository.IngredientRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class IngredientMatcher {

    // 대괄호([특가]) 및 소괄호((국산), (500g)) 안의 내용을 제거하기 위한 정규식
    private static final Pattern BRACKET_PATTERN = Pattern.compile("\\[[^\\]]*\\]|\\([^\\)]*\\)");

    // 숫자+단위(300g, 10구, 2개입 등)를 제거하기 위한 정규식
    private static final Pattern QUANTITY_UNIT_PATTERN = Pattern.compile(
            "(?i)\\d+(?:\\.\\d+)?\\s*(?:kg|g|mg|ml|l|구|개입|개|봉|팩|캔|모|단|줌|알|조각|마리|포기|줄|장|컵|병|통|박스|세트)(?=\\s|$)"
    );

    // 연속된 공백을 하나의 공백으로 치환하기 위한 정규식
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private final IngredientRepository ingredientRepository;

    /**
     * 사용자가 입력한 재료명을 다듬어 표준 재료(Ingredient)와 매핑한다.
     * 사전에 없는 재료라도 에러가 아니며, 직접 입력용 기본 재료(ING_CUSTOM)로 매핑해 반환한다.
     *
     * @param rawName 사용자가 입력한 재료 원문 이름
     * @return 매핑된 표준 재료 엔티티 (사전 일치 재료 또는 ING_CUSTOM)
     * @throws BusinessException DB에 ING_CUSTOM 재료 행 자체가 설정되어 있지 않은 경우
     */
    public Ingredient match(String rawName) {
        String normalizedName = normalize(rawName);

        if (!normalizedName.isEmpty()) {
            Optional<Ingredient> exactMatch = ingredientRepository.findByName(normalizedName);
            if (exactMatch.isPresent()) {
                return exactMatch.get();
            }
        }

        // TODO: Elasticsearch 검색 단계 추가 예정 (노리 + 동의어)

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
