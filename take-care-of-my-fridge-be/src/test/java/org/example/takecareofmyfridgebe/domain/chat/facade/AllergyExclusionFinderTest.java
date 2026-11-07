package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatAllergyGroupProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatAllergyGroupProperties.Group;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatMessageService;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AllergyExclusionFinderTest {

    private static final Long SESSION_ID = 7L;

    // application.yml app.chat.allergy-groups와 같은 묶음
    static final ChatAllergyGroupProperties GROUPS = new ChatAllergyGroupProperties(List.of(
            new Group("견과류", List.of("호두", "아몬드", "잣", "캐슈넛", "피스타치오", "땅콩")),
            new Group("갑각류", List.of("새우", "게", "꽃게", "게살", "게맛살", "가재새우")),
            new Group("유제품", List.of("우유", "치즈", "버터", "생크림", "요거트")),
            new Group("달걀", List.of("달걀", "메추리알", "마요네즈")),
            new Group("계란", List.of("달걀", "메추리알", "마요네즈")),
            new Group("밀가루", List.of("밀가루", "부침가루", "튀김가루", "빵가루"))));

    // 재료 사전 (이름 → 재료). dev DB의 실제 이름 일부
    private static final Map<String, Ingredient> DICTIONARY = new HashMap<>();

    private ChatMessageService chatMessageService;
    private IngredientService ingredientService;
    private AllergyExclusionFinder finder;

    @BeforeEach
    void setUp() {
        long id = 1;
        for (String name : List.of("땅콩", "땅콩가루", "땅콩기름", "땅콩버터", "땅콩잼", "땅콩호박", "새우", "건새우", "새우젓", "새우살",
                "가재새우", "게", "꽃게", "게살", "게맛살", "바게트", "스파게티", "멍게살", "호두", "아몬드", "아몬드가루", "잣", "잣가루",
                "캐슈넛", "피스타치오", "달걀", "달걀노른자", "메추리알", "마요네즈", "오이", "우유")) {
            DICTIONARY.put(name, ingredient(id++, name));
        }
        chatMessageService = mock(ChatMessageService.class);
        ingredientService = mock(IngredientService.class);
        when(chatMessageService.findUserContents(anyLong())).thenReturn(List.of());
        // IngredientService.findContainingNames와 같은 규칙: 두 글자 이상이면 이름에 들어 있는 재료, 한 글자면 그 글자로 시작하는 재료
        when(ingredientService.findContainingNames(any())).thenAnswer(invocation -> {
            Collection<String> names = invocation.getArgument(0);
            Map<String, List<Ingredient>> result = new HashMap<>();
            for (String name : names) {
                result.put(name, DICTIONARY.values().stream()
                        .filter(ingredient -> name.length() < 2 ? ingredient.getName().startsWith(name) : ingredient.getName().contains(name))
                        .toList());
            }
            return result;
        });
        finder = new AllergyExclusionFinder(chatMessageService, ingredientService, AllergyWordCheckerTest.WORDS, GROUPS);
    }

    @Test
    @DisplayName("땅콩 알레르기면 땅콩·땅콩버터·땅콩가루를 빼고, 다른 재료인 땅콩호박은 빼지 않는다")
    void excludesPeanutFamily_withoutPeanutSquash() {
        AllergyExclusionFinder.Allergens allergens = finder.find(SESSION_ID, "땅콩 알레르기 있어, 뭐 먹지");

        assertThat(allergens.names()).containsExactly("땅콩");
        assertThat(namesOf(allergens)).containsExactlyInAnyOrder("땅콩", "땅콩가루", "땅콩기름", "땅콩버터", "땅콩잼");
    }

    @Test
    @DisplayName("견과류 알레르기면 묶음 재료(호두·아몬드·잣·캐슈넛·피스타치오·땅콩)와 그 계열을 모두 뺀다")
    void excludesWholeGroup_whenGroupWord() {
        AllergyExclusionFinder.Allergens allergens = finder.find(SESSION_ID, "견과류 알레르기가 있어요");

        assertThat(allergens.names()).containsExactly("견과류");
        assertThat(namesOf(allergens)).contains("호두", "아몬드", "아몬드가루", "잣", "잣가루", "캐슈넛", "피스타치오", "땅콩", "땅콩버터")
                .doesNotContain("땅콩호박");
    }

    @Test
    @DisplayName("새우 못 먹어면 새우젓·건새우까지 뺀다")
    void excludesShrimpFamily_whenCannotEat() {
        AllergyExclusionFinder.Allergens allergens = finder.find(SESSION_ID, "새우를 못 먹어");

        assertThat(allergens.names()).containsExactly("새우");
        assertThat(namesOf(allergens)).contains("새우", "건새우", "새우젓", "새우살", "가재새우");
    }

    @Test
    @DisplayName("한 글자 게는 게로 시작하는 재료(게살·게맛살)만 보고, 바게트·스파게티·멍게살은 빼지 않는다 (갑각류 묶음도 같다)")
    void doesNotExcludeBaguetteOrSpaghetti_whenCrab() {
        assertThat(namesOf(finder.find(SESSION_ID, "게 알레르기"))).containsExactlyInAnyOrder("게", "게살", "게맛살");
        assertThat(namesOf(finder.find(SESSION_ID, "갑각류 알레르기")))
                .contains("새우", "새우젓", "게", "꽃게", "게살", "게맛살")
                .doesNotContain("바게트", "스파게티", "멍게살");
    }

    @Test
    @DisplayName("계란은 재료 사전에서 달걀로 합쳐져 있어 달걀 묶음으로 뺀다")
    void excludesEggGroup_whenGyeran() {
        AllergyExclusionFinder.Allergens allergens = finder.find(SESSION_ID, "계란 알레르기");

        assertThat(allergens.names()).containsExactly("계란");
        assertThat(namesOf(allergens)).contains("달걀", "달걀노른자", "메추리알", "마요네즈");
    }

    @Test
    @DisplayName("이 상담의 앞선 사용자 말(걸러지지 않은 말)에서 말한 알레르기도 찾는다")
    void findsAllergyInEarlierMessages() {
        when(chatMessageService.findUserContents(SESSION_ID)).thenReturn(List.of("땅콩 알레르기 있어", "매운 걸로"));

        AllergyExclusionFinder.Allergens allergens = finder.find(SESSION_ID, "순한 걸로");

        assertThat(allergens.names()).containsExactly("땅콩");
    }

    @Test
    @DisplayName("오이 못 먹어는 조사로 떼지 않고 오이 그대로 찾는다")
    void keepsWordEndingLikeParticle() {
        assertThat(finder.find(SESSION_ID, "오이 못 먹어").names()).containsExactly("오이");
    }

    @Test
    @DisplayName("재료가 아닌 낱말(매운 거 못 먹어의 거)이나 표시 말이 없는 말(약불로 끓여)은 알레르기로 보지 않는다")
    void returnsNone_whenNotIngredient() {
        assertThat(finder.find(SESSION_ID, "매운 거 못 먹어").isEmpty()).isTrue();
        assertThat(finder.find(SESSION_ID, "약불로 끓여").isEmpty()).isTrue();
        verify(ingredientService, never()).findContainingNames(List.of());
    }

    @Test
    @DisplayName("재료 조회가 실패하면 알레르기 없이 계속한다")
    void returnsNone_whenLookupFails() {
        doThrow(new RuntimeException("db down")).when(ingredientService).findContainingNames(any());

        assertThat(finder.find(SESSION_ID, "땅콩 알레르기").isEmpty()).isTrue();
    }

    private static List<String> namesOf(AllergyExclusionFinder.Allergens allergens) {
        return allergens.ingredientIds().stream()
                .map(id -> DICTIONARY.values().stream().filter(ingredient -> ingredient.getId().equals(id)).findFirst().orElseThrow().getName())
                .toList();
    }

    private static Ingredient ingredient(long id, String name) {
        Ingredient ingredient = Ingredient.builder()
                .ingredientCode("ING_" + id)
                .name(name)
                .baseUnit("g")
                .defaultStorage(StorageType.REFRIGERATED)
                .defaultShelfDays(7)
                .build();
        ReflectionTestUtils.setField(ingredient, "id", id);
        return ingredient;
    }
}
