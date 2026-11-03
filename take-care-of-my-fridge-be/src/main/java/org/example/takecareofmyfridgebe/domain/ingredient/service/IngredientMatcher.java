package org.example.takecareofmyfridgebe.domain.ingredient.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.ingredient.config.IngredientMatchProperties;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.repository.IngredientRepository;
import org.example.takecareofmyfridgebe.domain.search.dto.IngredientCandidate;
import org.example.takecareofmyfridgebe.domain.search.service.IngredientNameSearchService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 사용자가 입력한 재료명을 표준 재료(ingredients)와 매핑한다. 못 찾으면 직접 입력용 기본 재료(ING_CUSTOM, "기타")로 둔다.
 * <br>
 * 매칭 순서 (match·matchAll 같음)
 * 1) 괄호·수량·단위를 지워 이름을 다듬는다 ("[특가] 국산 흙대파 300g" → "국산 흙대파")
 * 2) 다듬은 이름과 같은 이름의 재료를 DB에서 한 번에 찾는다
 * 3) 못 찾은 이름만 재료 후보 검색(Elasticsearch)으로 후보를 받아 아래 두 규칙으로 하나를 고르고, 고른 이름들을 DB에서 한 번에 찾는다
 *    a. 1등 점수가 정확 일치 점수(1.0, 이름·별칭이 같음)면 채택 ("계란" → 달걀)
 *    b. 아니면 입력 이름(공백 제거)이 후보 이름으로 끝나는 후보 중 점수가 가장 높은 것을 채택. 후보 이름은 두 글자 이상만
 *       ("서울우유" → 우유, "국산 흙대파" → 대파, "꿀참외" → 참외)
 * 4) 그래도 없으면 ING_CUSTOM
 * <br>
 * 후보 점수 하나로는 기준을 잡을 수 없어 규칙 두 개를 쓴다. 점수가 높아도 이름 앞이나 가운데가 같은 후보는
 * 다른 음식인 경우가 많다("마늘빵" → 마늘 0.60, "콜라" → 루콜라 0.82, "새우깡" → 새우). 한국어 재료명은 수식어가 앞에 붙고
 * 재료가 끝에 오므로 끝부분이 같은 후보만 고른다. 한 글자 후보("파", "무")는 끝이 겹치는 이름이 너무 많아 끝부분 규칙에서 뺀다.
 * 알려진 한계: 딸기우유·바나나우유처럼 맛이 붙은 이름은 끝부분 규칙 때문에 기본 재료(우유)로 잡힌다.
 * <br>
 * 후보 검색이 꺼져 있거나 장애면 그 이름은 ING_CUSTOM으로 두고 등록은 계속한다.
 */
@Slf4j
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

    // DEBUG 로그에 남길 후보 수
    private static final int DEBUG_CANDIDATE_LIMIT = 3;

    private final IngredientRepository ingredientRepository;
    private final IngredientNameSearchService ingredientNameSearchService;
    private final IngredientMatchProperties matchProperties;

    /**
     * 사용자가 입력한 재료명을 다듬어 표준 재료(Ingredient)와 매핑한다.
     * 사전에 없는 재료라도 에러가 아니며, 직접 입력용 기본 재료(ING_CUSTOM)로 매핑해 반환한다.
     * 매핑 규칙이 matchAll 한 곳에만 있도록 단건도 matchAll에 위임한다.
     *
     * @param rawName 사용자가 입력한 재료 원문 이름
     * @return 매핑된 표준 재료 엔티티 (사전·후보 일치 재료 또는 ING_CUSTOM)
     * @throws BusinessException DB에 ING_CUSTOM 재료 행 자체가 설정되어 있지 않은 경우
     */
    public Ingredient match(String rawName) {
        return matchAll(List.of(rawName)).get(rawName);
    }

    /**
     * 여러 재료명을 한 번에 다듬어 표준 재료와 매핑한다. 순서는 클래스 주석과 같다.
     * 재료 조회 쿼리는 이름 일괄 조회 1번 + 후보 이름 일괄 조회(채택한 후보가 있을 때) 1번 + ING_CUSTOM(못 찾은 이름이 있을 때) 1번이다.
     * 후보 검색은 DB에서 못 찾은 이름마다 한 번씩 한다.
     *
     * @param rawNames 사용자가 입력한 재료 원문 이름 목록
     * @return 원문 이름을 키로 하는 매핑된 표준 재료 맵 (사전·후보 일치 재료 또는 ING_CUSTOM)
     * @throws BusinessException 못 찾은 이름이 있는데 DB에 ING_CUSTOM 재료 행이 설정되어 있지 않은 경우
     */
    public Map<String, Ingredient> matchAll(List<String> rawNames) {
        // 1. 이름 다듬기
        Map<String, String> normalizedNames = new HashMap<>();
        for (String rawName : rawNames) {
            normalizedNames.putIfAbsent(rawName, normalize(rawName));
        }

        // 2. 같은 이름 일괄 조회
        Set<String> lookupNames = normalizedNames.values().stream()
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toSet());
        Map<String, Ingredient> dictionary = findByNames(lookupNames);

        // 3. 못 찾은 이름만 후보 검색 → 채택한 후보 이름 일괄 조회
        Map<String, String> unmatchedByKey = new LinkedHashMap<>();
        for (String name : lookupNames) {
            if (!dictionary.containsKey(toLookupKey(name))) {
                unmatchedByKey.putIfAbsent(toLookupKey(name), name);
            }
        }
        if (!unmatchedByKey.isEmpty()) {
            log.debug("DB에 같은 이름이 없어 후보 검색: names={}", unmatchedByKey.values());
        }
        Map<String, String> adoptedByKey = new HashMap<>();
        unmatchedByKey.forEach((key, name) -> pickCandidate(name).ifPresent(adopted -> adoptedByKey.put(key, adopted)));
        Map<String, Ingredient> candidateDictionary = findByNames(new HashSet<>(adoptedByKey.values()));
        logCandidateLookup(adoptedByKey, unmatchedByKey, candidateDictionary);

        // 4. 그래도 없으면 ING_CUSTOM
        Map<String, Ingredient> result = new HashMap<>();
        Ingredient customIngredient = null;
        for (Map.Entry<String, String> entry : normalizedNames.entrySet()) {
            String key = toLookupKey(entry.getValue());
            Ingredient matched = dictionary.get(key);
            if (matched == null && adoptedByKey.containsKey(key)) {
                matched = candidateDictionary.get(toLookupKey(adoptedByKey.get(key)));
            }
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

    /**
     * 후보 중 채택할 표준 재료명. 1등이 정확 일치 점수면 1등, 아니면 입력 이름이 후보 이름으로 끝나는 후보 중 최고점.
     * 후보 검색 장애면 채택하지 않는다(ING_CUSTOM으로 등록은 계속).
     */
    private Optional<String> pickCandidate(String name) {
        List<IngredientCandidate> candidates;
        try {
            candidates = ingredientNameSearchService.findCandidates(name);
        } catch (RuntimeException e) {
            log.warn("재료 후보 검색 실패, 기타로 등록: name={}", name, e);
            return Optional.empty();
        }
        if (candidates == null || candidates.isEmpty()) {
            log.debug("후보 채택 안 함: name={}, 이유=후보 없음", name);
            return Optional.empty();
        }

        Comparator<IngredientCandidate> byScore = Comparator.comparingDouble(IngredientCandidate::score);
        if (log.isDebugEnabled()) {
            log.debug("후보 검색 결과: name={}, top3={}", name, candidates.stream()
                    .sorted(byScore.reversed())
                    .limit(DEBUG_CANDIDATE_LIMIT)
                    .map(candidate -> "%s(%.2f)".formatted(candidate.value(), candidate.score()))
                    .toList());
        }
        IngredientCandidate top = candidates.stream().max(byScore).orElseThrow();
        if (top.score() >= matchProperties.exactMatchScore()) {
            log.debug("후보 채택: name={}, adopted={}, 규칙=정확 일치, score={}", name, top.value(), top.score());
            return Optional.ofNullable(top.value());
        }

        String compactName = compact(name);
        Optional<IngredientCandidate> suffixMatched = candidates.stream()
                .filter(candidate -> candidate.value() != null)
                .filter(candidate -> {
                    String compactCandidate = compact(candidate.value());
                    return compactCandidate.length() >= matchProperties.suffixMinLength()
                            && compactName.endsWith(compactCandidate);
                })
                .max(byScore);
        if (suffixMatched.isPresent()) {
            log.debug("후보 채택: name={}, adopted={}, 규칙=끝부분, score={}",
                    name, suffixMatched.get().value(), suffixMatched.get().score());
        } else {
            log.debug("후보 채택 안 함: name={}, 이유=1등 점수 {} < {}이고 끝이 같은 {}글자 이상 후보 없음 (비교 이름={})",
                    name, top.score(), matchProperties.exactMatchScore(), matchProperties.suffixMinLength(), compactName);
        }
        return suffixMatched.map(IngredientCandidate::value);
    }

    // 채택한 후보 이름을 DB에서 다시 찾은 결과. 못 찾으면 그 이름은 ING_CUSTOM이 된다
    private void logCandidateLookup(
            Map<String, String> adoptedByKey,
            Map<String, String> unmatchedByKey,
            Map<String, Ingredient> candidateDictionary
    ) {
        if (!log.isDebugEnabled() || adoptedByKey.isEmpty()) {
            return;
        }
        adoptedByKey.forEach((key, adopted) -> {
            Ingredient found = candidateDictionary.get(toLookupKey(adopted));
            if (found == null) {
                log.debug("채택 이름 DB 재조회 실패, 기타로 둠: name={}, adopted={}", unmatchedByKey.get(key), adopted);
            } else {
                log.debug("채택 이름 DB 재조회 성공: name={}, adopted={}, ingredientId={}, ingredientCode={}",
                        unmatchedByKey.get(key), adopted, found.getId(), found.getIngredientCode());
            }
        });
    }

    // DB 비교는 대소문자를 구분하지 않으므로("spam" 조회 시 "Spam" 반환) 맵 키도 소문자로 맞춘다.
    private Map<String, Ingredient> findByNames(Collection<String> names) {
        if (names.isEmpty()) {
            return Map.of();
        }
        return ingredientRepository.findByNameIn(names).stream()
                .collect(Collectors.toMap(
                        ingredient -> toLookupKey(ingredient.getName()),
                        Function.identity(),
                        (first, second) -> first
                ));
    }

    private static String toLookupKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    // 끝부분 비교용: 공백을 빼고 소문자로 ("국산 흙대파" → "국산흙대파")
    private static String compact(String name) {
        return WHITESPACE_PATTERN.matcher(name).replaceAll("").toLowerCase(Locale.ROOT);
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
