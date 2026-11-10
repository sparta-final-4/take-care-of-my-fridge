package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatCategoryWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.facade.CategoryWordChecker.CategoryStance;
import org.example.takecareofmyfridgebe.domain.chat.facade.CategoryWordChecker.Stance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryWordCheckerTest {

    private static final List<String> CATEGORIES = List.of(
            "밥", "구이", "국", "면류", "볶음", "샐러드", "무침", "찜", "튀김", "김치", "디저트", "전", "찌개", "조림", "전골", "떡", "만두");

    static final List<ChatCategoryWordProperties.Group> GROUPS = List.of(
            new ChatCategoryWordProperties.Group("국물", List.of("국", "찌개", "전골", "수프", "스튜")),
            new ChatCategoryWordProperties.Group("스프", List.of("수프")),
            new ChatCategoryWordProperties.Group("면", List.of("면류")),
            new ChatCategoryWordProperties.Group("안주", List.of("구이", "전", "튀김")));

    private static List<CategoryStance> check(String message) {
        return CategoryWordChecker.check(message, CATEGORIES, TasteWordCheckerTest.WORDS);
    }

    @Test
    @DisplayName("\"디저트 추천해줘\", \"다른 디저트 추천해줘\"는 디저트를 원한다")
    void returnsWant_whenCategoryRequested() {
        assertThat(check("디저트 추천해줘")).containsExactly(new CategoryStance("디저트", Stance.WANT));
        assertThat(check("다른 디저트 추천해줘")).containsExactly(new CategoryStance("디저트", Stance.WANT));
        assertThat(check("디저트!")).containsExactly(new CategoryStance("디저트", Stance.WANT));
    }

    @Test
    @DisplayName("분류 이름 뒤에 조사가 붙어도 원하는 분류로 본다 (\"국도 같이\", \"디저트는 없어?\")")
    void returnsWant_whenParticleAttached() {
        assertThat(check("국도 같이 추천해줘")).containsExactly(new CategoryStance("국", Stance.WANT));
        assertThat(check("다른 디저트는 없어?")).containsExactly(new CategoryStance("디저트", Stance.WANT));
    }

    @Test
    @DisplayName("\"디저트 말고\"처럼 부정어가 붙으면 피하고, \"디저트 말고 국\"은 디저트를 피하고 국을 원한다")
    void returnsAvoid_whenNegated() {
        assertThat(check("디저트 말고")).containsExactly(new CategoryStance("디저트", Stance.AVOID));
        assertThat(check("디저트말고 다른 거")).containsExactly(new CategoryStance("디저트", Stance.AVOID));
        assertThat(check("디저트 말고 국")).containsExactly(
                new CategoryStance("디저트", Stance.AVOID), new CategoryStance("국", Stance.WANT));
    }

    @Test
    @DisplayName("분류 이름이 다른 말 안에 들어 있으면 분류로 보지 않는다 (국수·밥솥·전자레인지·김치찌개·찜닭)")
    void returnsEmpty_whenNameInsideOtherWord() {
        assertThat(check("국수 먹고 싶어")).isEmpty();
        assertThat(check("밥솥으로 할 수 있는 요리")).isEmpty();
        assertThat(check("전자레인지로 만들 수 있는 거")).isEmpty();
        assertThat(check("김치찌개 먹고 싶어")).isEmpty();
        assertThat(check("찜닭 먹고 싶어")).isEmpty();
    }

    @Test
    @DisplayName("재료로 말한 분류 이름(\"김치로\", \"김치 넣은\", \"떡 썰어서\")은 분류로 보지 않는다")
    void returnsEmpty_whenUsedAsIngredient() {
        assertThat(check("김치로 만들 수 있는 요리")).isEmpty();
        assertThat(check("김치 넣은 요리 추천해줘")).isEmpty();
        assertThat(check("떡 썰어서 할 수 있는 거")).isEmpty();
    }

    @Test
    @DisplayName("대명사·끼니로 흔히 쓰는 전·밥은 서버가 채우지 않는다")
    void skipsPronounAndMealWords() {
        assertThat(check("전 매운 거 좋아해")).isEmpty();
        assertThat(check("밥 뭐 먹지?")).isEmpty();
    }

    @Test
    @DisplayName("긴 분류 이름을 먼저 본다 (전골은 전이 아니라 전골)")
    void prefersLongerCategoryName() {
        assertThat(check("전골 추천해줘")).containsExactly(new CategoryStance("전골", Stance.WANT));
    }

    @Test
    @DisplayName("묶음 말(국물·면·안주)은 그 묶음의 분류 중 실제 있는 분류를 모두 원하고, 국수는 국으로 보지 않는다")
    void returnsGroupCategories_whenGroupWord() {
        assertThat(CategoryWordChecker.check("국물 밀키트 있어?", CATEGORIES, TasteWordCheckerTest.WORDS, GROUPS))
                .containsExactly(new CategoryStance("국", Stance.WANT), new CategoryStance("찌개", Stance.WANT),
                        new CategoryStance("전골", Stance.WANT));
        assertThat(CategoryWordChecker.check("면 요리 추천해줘", CATEGORIES, TasteWordCheckerTest.WORDS, GROUPS))
                .containsExactly(new CategoryStance("면류", Stance.WANT));
        assertThat(CategoryWordChecker.check("국수 추천해줘", CATEGORIES, TasteWordCheckerTest.WORDS, GROUPS)).isEmpty();
        // "스프"는 수프의 다른 표기
        assertThat(CategoryWordChecker.check("맵고 따뜻한 스프", List.of("국", "수프"), TasteWordCheckerTest.WORDS, GROUPS))
                .containsExactly(new CategoryStance("수프", Stance.WANT));
        assertThat(CategoryWordChecker.check("국물 말고 안주로 볶음 추천", CATEGORIES, TasteWordCheckerTest.WORDS, GROUPS))
                .contains(new CategoryStance("국", Stance.AVOID), new CategoryStance("볶음", Stance.WANT));
    }
}
