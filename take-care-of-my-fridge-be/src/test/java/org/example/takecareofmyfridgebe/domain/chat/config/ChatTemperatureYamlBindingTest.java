package org.example.takecareofmyfridgebe.domain.chat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

// config/chat-words.yml의 온도 말(app.chat.temperature-words)이 설정 값으로 묶이는지 본다
@SpringBootTest(classes = ChatTemperatureYamlBindingTest.class)
@EnableConfigurationProperties(ChatTemperatureWordProperties.class)
class ChatTemperatureYamlBindingTest {

    @Autowired
    private ChatTemperatureWordProperties temperatureWords;

    @Test
    @DisplayName("온도 말과 찬 요리·뜨거운 국물 이름이 yml에서 묶이고, 찬 요리에 \"냉\" 한 글자는 없다")
    void bindsTemperatureWords() {
        assertThat(temperatureWords.warm()).contains("따뜻", "뜨끈");
        assertThat(temperatureWords.cold()).contains("차가운", "시원");
        assertThat(temperatureWords.coldDishes()).contains("냉국", "냉면", "빙수").doesNotContain("냉");
        assertThat(temperatureWords.hotDishEndings()).containsExactly("탕", "전골", "찌개", "국밥");
        assertThat(temperatureWords.hotDishExclusions()).containsExactly("그라탕", "냉탕");
    }
}
