package org.example.takecareofmyfridgebe.domain.ingredient.service;

import org.example.takecareofmyfridgebe.domain.ingredient.config.IngredientMatchProperties;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.ingredient.repository.IngredientRepository;
import org.example.takecareofmyfridgebe.domain.search.dto.IngredientCandidate;
import org.example.takecareofmyfridgebe.domain.search.service.IngredientNameSearchService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * IngredientMatcher 단위 테스트.
 * 재료명 다듬기(normalize), 사전 일치 매핑, 미등록 재료의 ING_CUSTOM 폴백,
 * 그리고 ING_CUSTOM 미설정 시 예외 발생을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class IngredientMatcherTest {

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private IngredientNameSearchService ingredientNameSearchService;

    private IngredientMatcher ingredientMatcher;

    @BeforeEach
    void setUp() {
        ingredientMatcher = new IngredientMatcher(
                ingredientRepository, ingredientNameSearchService, new IngredientMatchProperties(1.0, 2));
    }

    /**
     * 괄호 안 문구([특가], (500g))와 숫자+단위(10구, 300g, 2개입)가 제거되고
     * 앞뒤·연속 공백이 깔끔하게 정리되는지 검증한다.
     */
    @Test
    @DisplayName("이름을 다듬으면 괄호 문구와 수량·단위를 지우고 공백을 정리한다")
    void removesBracketsAndQuantityUnits_whenNormalizing() {
        assertThat(IngredientMatcher.normalize("[특가] 무항생제 신선란 10구")).isEqualTo("무항생제 신선란");
        assertThat(IngredientMatcher.normalize("국산 두부 300g")).isEqualTo("국산 두부");
        assertThat(IngredientMatcher.normalize("비비고 썰은배추김치(500g)")).isEqualTo("비비고 썰은배추김치");
        assertThat(IngredientMatcher.normalize("(국산) 양파 2개입")).isEqualTo("양파");
    }

    /**
     * 숫자+단위를 공백 없이 붙여 써도 제거되고,
     * 단위가 앞·뒤 어디에 있어도 제거되며("2개입 계란", "배추 1포기"),
     * 숫자 바로 뒤가 아닌 단위 글자("단호박"의 "단")는 그대로 남는지 검증한다.
     */
    @Test
    @DisplayName("숫자에 붙은 단위는 앞뒤 어디에 있어도 지우고, 숫자 바로 뒤가 아닌 단위 글자는 남긴다")
    void removesUnitsOnlyRightAfterNumber() {
        assertThat(IngredientMatcher.normalize("계란10구")).isEqualTo("계란");
        assertThat(IngredientMatcher.normalize("대파300g")).isEqualTo("대파");
        assertThat(IngredientMatcher.normalize("국산 두부 300g")).isEqualTo("국산 두부");
        assertThat(IngredientMatcher.normalize("단호박")).isEqualTo("단호박");
        assertThat(IngredientMatcher.normalize("2개입 계란")).isEqualTo("계란");
        assertThat(IngredientMatcher.normalize("배추 1포기")).isEqualTo("배추");
    }

    /**
     * 20개를 한 번에 매핑할 때 재료 조회 쿼리가 최대 2번
     * (사전 일괄 조회 1번 + ING_CUSTOM 조회 1번)만 실행되고,
     * 사전에 없는 이름들은 같은 ING_CUSTOM 엔티티를 공유하는지 검증한다.
     */
    @Test
    @DisplayName("20개 이름을 한 번에 매칭하면 재료 조회는 두 번까지만 하고 못 찾은 이름은 같은 기타 재료를 쓴다")
    void queriesAtMostTwice_whenMatchingTwentyNames() {
        // Given: 사전에 있는 재료 10개 + 사전에 없는 재료 10개
        List<String> rawNames = new ArrayList<>();
        List<Ingredient> dictionary = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rawNames.add("재료" + i + " 300g");
            dictionary.add(Ingredient.builder()
                    .ingredientCode("ING_" + i)
                    .name("재료" + i)
                    .baseUnit("개")
                    .defaultStorage(StorageType.REFRIGERATED)
                    .defaultShelfDays(5)
                    .basicSeasoning(false)
                    .build());
        }
        for (int i = 0; i < 10; i++) {
            rawNames.add("엄마표 반찬" + i);
        }
        Ingredient customIngredient = Ingredient.builder()
                .ingredientCode(Ingredient.ING_CUSTOM)
                .name("기타")
                .baseUnit("개")
                .defaultStorage(StorageType.REFRIGERATED)
                .defaultShelfDays(7)
                .basicSeasoning(false)
                .build();
        when(ingredientRepository.findByNameIn(anyCollection())).thenReturn(dictionary);
        when(ingredientRepository.findByIngredientCode(Ingredient.ING_CUSTOM))
                .thenReturn(Optional.of(customIngredient));

        // When
        Map<String, Ingredient> matched = ingredientMatcher.matchAll(rawNames);

        // Then: 20개 모두 매핑되고, 사전 재료는 각자 매핑, 나머지는 같은 ING_CUSTOM 공유
        assertThat(matched.size()).isEqualTo(20);
        for (int i = 0; i < 10; i++) {
            assertThat(matched.get("재료" + i + " 300g")).isSameAs(dictionary.get(i));
            assertThat(matched.get("엄마표 반찬" + i)).isSameAs(customIngredient);
        }

        // Then: 재료 조회 쿼리는 일괄 조회 1번 + ING_CUSTOM 조회 1번, 그 외 조회 없음
        verify(ingredientRepository, times(1)).findByNameIn(anyCollection());
        verify(ingredientRepository, times(1)).findByIngredientCode(Ingredient.ING_CUSTOM);
        verifyNoMoreInteractions(ingredientRepository);
    }

    /**
     * DB가 대소문자 구분 없이 사전 재료("Spam")를 돌려줄 때
     * 입력 대소문자("SPAM 200g", "spam")와 관계없이 사전 재료로 매핑되는지 검증한다.
     */
    @Test
    @DisplayName("입력 대소문자와 관계없이 사전 재료로 매칭한다")
    void matchesDictionaryIgnoringCase() {
        // Given: 사전에는 "Spam"으로 저장되어 있음
        Ingredient spam = Ingredient.builder()
                .ingredientCode("ING_SPAM")
                .name("Spam")
                .baseUnit("캔")
                .defaultStorage(StorageType.ROOM_TEMP)
                .defaultShelfDays(365)
                .basicSeasoning(false)
                .build();
        when(ingredientRepository.findByNameIn(anyCollection())).thenReturn(List.of(spam));

        // When
        Map<String, Ingredient> matched = ingredientMatcher.matchAll(List.of("SPAM 200g", "spam"));

        // Then: 둘 다 사전 재료로 매핑되고 ING_CUSTOM은 조회하지 않음
        assertThat(matched.get("SPAM 200g")).isSameAs(spam);
        assertThat(matched.get("spam")).isSameAs(spam);
        verify(ingredientRepository, never()).findByIngredientCode(Ingredient.ING_CUSTOM);
    }

    /**
     * 모든 이름이 사전에 있으면 ING_CUSTOM을 조회하지 않는지 검증한다.
     */
    @Test
    @DisplayName("모든 이름을 사전에서 찾으면 기타 재료를 조회하지 않는다")
    void skipsCustomLookup_whenAllNamesAreFound() {
        // Given
        Ingredient tofu = Ingredient.builder()
                .ingredientCode("ING_TOFU")
                .name("국산 두부")
                .baseUnit("모")
                .defaultStorage(StorageType.REFRIGERATED)
                .defaultShelfDays(5)
                .basicSeasoning(false)
                .build();
        when(ingredientRepository.findByNameIn(anyCollection())).thenReturn(List.of(tofu));

        // When
        Map<String, Ingredient> matched = ingredientMatcher.matchAll(List.of("국산 두부 300g"));

        // Then
        assertThat(matched.get("국산 두부 300g")).isSameAs(tofu);
        verify(ingredientRepository, never()).findByIngredientCode(Ingredient.ING_CUSTOM);
    }

    /**
     * 사전에 없는 이름이 있는데 DB에 ING_CUSTOM 행이 없으면
     * CUSTOM_INGREDIENT_NOT_CONFIGURED 예외를 던지는지 검증한다.
     */
    @Test
    @DisplayName("여러 이름을 매칭할 때 기타 재료가 필요한데 DB에 없으면 CUSTOM_INGREDIENT_NOT_CONFIGURED 예외를 던진다")
    void throwsCustomNotConfigured_whenMatchAllNeedsMissingCustom() {
        // Given
        when(ingredientRepository.findByNameIn(anyCollection())).thenReturn(List.of());
        when(ingredientRepository.findByIngredientCode(Ingredient.ING_CUSTOM))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> ingredientMatcher.matchAll(List.of("엄마표 겉절이")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CUSTOM_INGREDIENT_NOT_CONFIGURED);
    }

    /**
     * 다듬은 재료명이 표준 재료 사전(ingredients.name)에 있으면
     * ING_CUSTOM을 조회하지 않고 해당 표준 재료를 그대로 반환하는지 검증한다.
     */
    @Test
    @DisplayName("다듬은 이름이 사전에 있으면 기타 재료를 조회하지 않고 그 재료를 반환한다")
    void returnsDictionaryIngredient_whenNameIsFound() {
        // Given: 다듬은 이름 "국산 두부"가 사전에 등록되어 있음
        Ingredient tofu = Ingredient.builder()
                .ingredientCode("ING_TOFU")
                .name("국산 두부")
                .baseUnit("모")
                .defaultStorage(StorageType.REFRIGERATED)
                .defaultShelfDays(5)
                .basicSeasoning(false)
                .build();
        when(ingredientRepository.findByNameIn(Set.of("국산 두부"))).thenReturn(List.of(tofu));

        // When: 원문 "국산 두부 300g"으로 매핑 요청
        Ingredient matched = ingredientMatcher.match("국산 두부 300g");

        // Then: 사전의 두부 엔티티가 반환되고 ING_CUSTOM 조회는 호출되지 않음
        assertThat(matched).isSameAs(tofu);
        verify(ingredientRepository, never()).findByIngredientCode(Ingredient.ING_CUSTOM);
    }

    /**
     * 사전에 없는 재료명("엄마표 겉절이")을 입력하더라도 에러가 발생하지 않고
     * 직접 입력용 기본 재료(ING_CUSTOM)로 정상 매핑되는지 검증한다.
     */
    @Test
    @DisplayName("사전에도 없고 후보도 없는 이름은 예외 없이 기타 재료로 매칭한다")
    void returnsCustomIngredient_whenNoCandidate() {
        // Given: 사전에 "엄마표 겉절이"가 없고, DB에 ING_CUSTOM 재료가 존재함
        Ingredient customIngredient = Ingredient.builder()
                .ingredientCode(Ingredient.ING_CUSTOM)
                .name("기타")
                .baseUnit("개")
                .defaultStorage(StorageType.REFRIGERATED)
                .defaultShelfDays(7)
                .basicSeasoning(false)
                .build();
        when(ingredientRepository.findByNameIn(Set.of("엄마표 겉절이"))).thenReturn(List.of());
        when(ingredientRepository.findByIngredientCode(Ingredient.ING_CUSTOM))
                .thenReturn(Optional.of(customIngredient));

        // When
        Ingredient matched = ingredientMatcher.match("엄마표 겉절이");

        // Then: 예외 없이 ING_CUSTOM 엔티티 반환
        assertThat(matched).isSameAs(customIngredient);
        assertThat(matched.getIngredientCode()).isEqualTo(Ingredient.ING_CUSTOM);
    }

    /**
     * 사전에 없는 재료명을 입력했는데 DB에 ING_CUSTOM 초기 데이터 행조차 없으면
     * CUSTOM_INGREDIENT_NOT_CONFIGURED(500) 비즈니스 예외를 던지는지 검증한다.
     */
    @Test
    @DisplayName("이름 하나를 매칭할 때 기타 재료가 필요한데 DB에 없으면 CUSTOM_INGREDIENT_NOT_CONFIGURED 예외를 던진다")
    void throwsCustomNotConfigured_whenMatchNeedsMissingCustom() {
        // Given: 사전에도 없고 ING_CUSTOM 행도 DB에 없음
        when(ingredientRepository.findByNameIn(Set.of("엄마표 겉절이"))).thenReturn(List.of());
        when(ingredientRepository.findByIngredientCode(Ingredient.ING_CUSTOM))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> ingredientMatcher.match("엄마표 겉절이"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CUSTOM_INGREDIENT_NOT_CONFIGURED);
    }

    // ===== 후보 검색(findCandidates) 연결: 점수는 실제 인덱스에서 측정한 값을 그대로 넣는다 =====

    // DB에 있는 표준 재료. 입력 원문 이름은 없고 후보 이름만 있다
    private static final List<String> DB_NAMES =
            List.of("달걀", "우유", "대파", "참외", "꿀", "마늘", "루콜라", "골파", "양파", "쪽파");

    private static Stream<Arguments> measuredCandidates() {
        return Stream.of(
                Arguments.of("계란", List.of(new IngredientCandidate("달걀", 1.0)), "달걀"),
                Arguments.of("서울우유", List.of(new IngredientCandidate("우유", 0.57)), "우유"),
                Arguments.of("국산 흙대파", List.of(new IngredientCandidate("대파", 0.48)), "대파"),
                Arguments.of("꿀참외", List.of(
                        new IngredientCandidate("꿀", 0.61), new IngredientCandidate("참외", 0.50)), "참외"),
                Arguments.of("마늘빵", List.of(new IngredientCandidate("마늘", 0.60)), "기타"),
                Arguments.of("콜라", List.of(new IngredientCandidate("루콜라", 0.82)), "기타"),
                Arguments.of("새우깡", List.of(), "기타"),
                Arguments.of("파", List.of(
                        new IngredientCandidate("골파", 0.82),
                        new IngredientCandidate("양파", 0.82),
                        new IngredientCandidate("쪽파", 0.82)), "기타")
        );
    }

    @ParameterizedTest(name = "{0} → {2}")
    @MethodSource("measuredCandidates")
    @DisplayName("DB에 없는 이름은 1등이 정확 일치(1.0)거나, 입력 끝이 두 글자 이상 후보 이름과 같을 때만 그 후보로 매핑한다")
    void adoptsCandidate_whenExactScoreOrSuffixMatches(String rawName, List<IngredientCandidate> candidates, String expected) {
        givenDatabase();
        when(ingredientNameSearchService.findCandidates(rawName)).thenReturn(candidates);

        Ingredient matched = ingredientMatcher.match(rawName);

        assertThat(matched.getName()).isEqualTo(expected);
    }

    @Test
    @DisplayName("DB에 같은 이름이 있으면 후보 검색을 부르지 않는다")
    void skipsCandidateSearch_whenFoundInDatabase() {
        givenDatabase();

        Map<String, Ingredient> matched = ingredientMatcher.matchAll(List.of("우유 1L", "[특가] 대파"));

        assertThat(matched.get("우유 1L").getName()).isEqualTo("우유");
        assertThat(matched.get("[특가] 대파").getName()).isEqualTo("대파");
        verify(ingredientNameSearchService, never()).findCandidates(anyString());
        verify(ingredientRepository, times(1)).findByNameIn(anyCollection());
    }

    @Test
    @DisplayName("여러 이름이면 못 찾은 이름만 후보 검색하고, 채택한 후보 이름들은 DB에서 한 번에 다시 찾는다")
    void searchesOnlyUnmatchedAndRequeriesAdoptedOnce() {
        givenDatabase();
        when(ingredientNameSearchService.findCandidates("서울우유"))
                .thenReturn(List.of(new IngredientCandidate("우유", 0.57)));
        when(ingredientNameSearchService.findCandidates("국산 흙대파"))
                .thenReturn(List.of(new IngredientCandidate("대파", 0.48)));
        when(ingredientNameSearchService.findCandidates("새우깡")).thenReturn(List.of());

        Map<String, Ingredient> matched = ingredientMatcher.matchAll(
                List.of("우유", "서울우유 900ml", "국산 흙대파 1단", "새우깡"));

        assertThat(matched.get("우유").getName()).isEqualTo("우유");
        assertThat(matched.get("서울우유 900ml").getName()).isEqualTo("우유");
        assertThat(matched.get("국산 흙대파 1단").getName()).isEqualTo("대파");
        assertThat(matched.get("새우깡").getName()).isEqualTo("기타");
        verify(ingredientNameSearchService, times(3)).findCandidates(anyString());
        verify(ingredientNameSearchService, never()).findCandidates("우유");
        verify(ingredientRepository).findByNameIn(Set.of("우유", "서울우유", "국산 흙대파", "새우깡"));
        verify(ingredientRepository).findByNameIn(Set.of("우유", "대파"));
        verify(ingredientRepository, times(2)).findByNameIn(anyCollection());
    }

    @Test
    @DisplayName("후보 검색이 예외를 던져도 밖으로 내보내지 않고 그 이름은 기타로 등록한다")
    void fallsBackToCustom_whenCandidateSearchFails() {
        givenDatabase();
        when(ingredientNameSearchService.findCandidates(any())).thenThrow(new IllegalStateException("es down"));

        // 예외가 밖으로 나오면 이 줄에서 테스트가 실패한다
        Map<String, Ingredient> matched = ingredientMatcher.matchAll(List.of("서울우유", "우유"));

        assertThat(matched.get("서울우유").getName()).isEqualTo("기타");
        assertThat(matched.get("우유").getName()).isEqualTo("우유");
    }

    // DB: 이름이 같은 표준 재료만 돌려주고, ING_CUSTOM은 "기타"
    private void givenDatabase() {
        when(ingredientRepository.findByNameIn(anyCollection())).thenAnswer(invocation -> {
            Collection<String> names = invocation.getArgument(0);
            return DB_NAMES.stream().filter(names::contains).map(name -> ingredient("ING_" + name, name)).toList();
        });
        lenient().when(ingredientRepository.findByIngredientCode(Ingredient.ING_CUSTOM))
                .thenReturn(Optional.of(ingredient(Ingredient.ING_CUSTOM, "기타")));
    }

    private static Ingredient ingredient(String code, String name) {
        return Ingredient.builder()
                .ingredientCode(code)
                .name(name)
                .baseUnit("개")
                .defaultStorage(StorageType.REFRIGERATED)
                .defaultShelfDays(7)
                .basicSeasoning(false)
                .build();
    }
}
