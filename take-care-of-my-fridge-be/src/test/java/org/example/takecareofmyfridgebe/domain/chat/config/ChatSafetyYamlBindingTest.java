package org.example.takecareofmyfridgebe.domain.chat.config;

import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

// config/chat-words.yml(맛 반대 쌍)·config/chat-safety.yml(알레르기 묶음)을 application.yml의 spring.config.import로 불러와 설정 값으로 묶이는지 본다 (목록 안의 목록·객체 목록)
@SpringBootTest(classes = ChatSafetyYamlBindingTest.class)
@EnableConfigurationProperties({ChatWordProperties.class, ChatAllergyGroupProperties.class, ChatAllergyWordProperties.class})
class ChatSafetyYamlBindingTest {

    @Autowired
    private ChatWordProperties words;

    @Autowired
    private ChatAllergyGroupProperties allergyGroups;

    @Autowired
    private ChatAllergyWordProperties allergyWords;

    @Test
    @DisplayName("맛 반대 쌍(MILD ↔ SPICY, MILD ↔ SALTY)이 yml에서 묶인다")
    void bindsTasteOpposites() {
        assertThat(words.tasteOpposites().oppositesOf(Taste.MILD)).containsExactlyInAnyOrder(Taste.SPICY, Taste.SALTY);
    }

    @Test
    @DisplayName("알레르기 묶음과 표시 말·넓히지 않을 이름이 yml에서 묶인다")
    void bindsAllergyConfig() {
        assertThat(allergyGroups.groups()).extracting(ChatAllergyGroupProperties.Group::word)
                .containsExactly("견과류", "갑각류", "유제품", "달걀", "계란", "밀가루");
        assertThat(allergyGroups.groups().get(0).ingredients()).containsExactly("호두", "아몬드", "잣", "캐슈넛", "피스타치오", "마카다미아", "땅콩");
        assertThat(allergyWords.markers()).containsExactly("알레르기", "알러지", "못먹", "먹으면안");
        assertThat(allergyWords.notSameKindOf("땅콩")).containsExactly("땅콩호박");
        assertThat(allergyWords.notSameKindOf("굴")).containsExactly("굴비");
        assertThat(allergyWords.notSameKindOf("마")).contains("마늘", "마요네즈", "마카다미아");
        assertThat(allergyWords.notSameKindOf("양")).contains("양송이버섯밑동", "양파").doesNotContain("양고기");
        assertThat(allergyWords.notSameKindOf("쌀")).isEmpty();
    }
}
