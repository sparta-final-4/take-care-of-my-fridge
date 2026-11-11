package org.example.takecareofmyfridgebe.domain.chat.config;

import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// config/chat-words.yml·chat-safety.yml·chat-candidate.yml을 application.yml의 spring.config.import로 불러와 설정 값으로 묶이는지 보고
// (목록 안의 목록·객체 목록 포함), 꼭 있어야 하는 목록이 비면 설정 검증에 걸려 뜨지 않는지 본다
@SpringBootTest(classes = ChatConfigYamlBindingTest.class)
@EnableConfigurationProperties({ChatWordProperties.class, ChatSafetyProperties.class, ChatCandidateProperties.class})
class ChatConfigYamlBindingTest {

    @Autowired
    private ChatWordProperties words;

    @Autowired
    private ChatSafetyProperties safety;

    @Autowired
    private ChatCandidateProperties candidate;

    @Test
    @DisplayName("단어 설정(chat-words.yml)이 app.chat.words 아래로 묶인다: 맛 반대 쌍, 온도 말, 분류 묶음")
    void bindsWords() {
        assertThat(words.tasteOpposites().oppositesOf(Taste.MILD)).containsExactlyInAnyOrder(Taste.SPICY, Taste.SALTY);
        assertThat(words.temperature().warm()).contains("따뜻", "뜨끈");
        assertThat(words.temperature().cold()).contains("차가운", "시원");
        assertThat(words.temperature().coldDishes()).contains("냉국", "냉면", "빙수").doesNotContain("냉");
        assertThat(words.temperature().hotDishEndings()).containsExactly("탕", "전골", "찌개", "국밥");
        assertThat(words.temperature().hotDishExclusions()).containsExactly("그라탕", "냉탕");
        assertThat(words.category().groups().get(0).word()).isEqualTo("국물");
        assertThat(words.category().groups().get(0).categories()).containsExactly("국", "찌개", "전골", "수프", "스튜");
        assertThat(words.ask().recipe()).containsExactly("레시피", "만드는법", "만들기", "요리법", "조리법");
    }

    @Test
    @DisplayName("안전 설정(chat-safety.yml)이 app.chat.safety 아래로 묶인다: 금지어, 건강 말, 알레르기 묶음, 넓히지 않을 이름")
    void bindsSafety() {
        assertThat(safety.words().banned()).contains("마약", "해킹");
        assertThat(safety.words().exclusions()).contains("마약김밥", "폭탄주");
        assertThat(safety.health().health()).contains("당뇨", "임신");
        assertThat(safety.allergyGroups()).extracting(ChatSafetyProperties.AllergyGroup::word)
                .containsExactly("견과류", "갑각류", "유제품", "달걀", "계란", "밀가루");
        assertThat(safety.allergyGroups().get(0).ingredients()).containsExactly("호두", "아몬드", "잣", "캐슈넛", "피스타치오", "마카다미아", "땅콩");
        assertThat(safety.allergy().markers()).containsExactly("알레르기", "알러지", "못먹", "먹으면안");
        assertThat(safety.allergy().notSameKindOf("땅콩")).containsExactly("땅콩호박");
        assertThat(safety.allergy().notSameKindOf("굴")).containsExactly("굴비");
        assertThat(safety.allergy().notSameKindOf("마")).contains("마늘", "마요네즈", "마카다미아");
        assertThat(safety.allergy().notSameKindOf("양")).contains("양송이버섯밑동", "양파").doesNotContain("양고기");
        assertThat(safety.allergy().notSameKindOf("쌀")).isEmpty();
    }

    @Test
    @DisplayName("후보 설정(chat-candidate.yml)이 app.chat.candidate 아래로 묶인다")
    void bindsCandidate() {
        assertThat(candidate.excludedCategories()).containsExactly("양념", "음료");
        assertThat(candidate.minOwnedRatio()).isEqualTo(0.4);
        assertThat(candidate.spicyIngredients()).contains("청양고추", "김치");
        assertThat(candidate.spicyExclusions()).containsExactly("백김치", "물김치", "동치미", "나박김치");
    }

    @Test
    @DisplayName("꼭 있어야 하는 단어 목록이 비면 설정 검증에 걸려 서버가 뜨지 않는다")
    void failsToStart_whenRequiredListIsEmpty() {
        // 명령줄 인자는 yml보다 우선한다 (properties()는 기본값이라 yml에 밀린다)
        assertThatThrownBy(() -> new SpringApplicationBuilder(ChatConfigYamlBindingTest.class)
                .web(WebApplicationType.NONE)
                .run("--app.chat.words.temperature.warm=")
                .close())
                .rootCause()
                .isInstanceOf(BindValidationException.class)
                .hasMessageContaining("warm");
    }

    @Test
    @DisplayName("제외 목록은 비어도 서버가 뜬다")
    void starts_whenExclusionListIsEmpty() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(ChatConfigYamlBindingTest.class)
                .web(WebApplicationType.NONE)
                .run("--app.chat.safety.words.exclusions=")) {
            assertThat(context.getBean(ChatSafetyProperties.class).words().exclusions()).isEmpty();
            assertThat(context.getBean(ChatSafetyProperties.class).words().banned()).isNotEmpty();
        }
    }
}
