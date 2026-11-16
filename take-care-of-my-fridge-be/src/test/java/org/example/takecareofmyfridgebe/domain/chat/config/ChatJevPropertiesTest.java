package org.example.takecareofmyfridgebe.domain.chat.config;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatJevProperties.Mode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// app.chat.jev 설정(Jev를 어떻게 쓸지)이 묶이는 값. 기본값은 application.yml만 읽어서 본다
// (시스템 환경 변수를 넣지 않아야 이 컴퓨터에 CHAT_JEV_MODE가 있어도 기본값을 볼 수 있다)
class ChatJevPropertiesTest {

    @Test
    @DisplayName("환경 변수가 없으면 application.yml 기본값으로 off다")
    void bindsOff_whenNoEnvironmentVariables() throws IOException {
        List<PropertySource<?>> yaml = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        Binder binder = new Binder(ConfigurationPropertySources.from(yaml), new PropertySourcesPlaceholdersResolver(yaml));

        ChatJevProperties jev = binder.bind("app.chat.jev", ChatJevProperties.class).get();

        assertThat(jev.mode()).isEqualTo(Mode.OFF);
        assertThat(jev.calls()).isFalse();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"off, OFF, false, false", "shadow, SHADOW, true, false", "on, ON, true, true"})
    @DisplayName("속성으로 준 모드(소문자)로 묶이고, shadow·on이면 Jev를 부르며 on만 Jev로 의도를 정한다")
    void bindsMode_whenPropertyGiven(String value, Mode mode, boolean calls, boolean on) {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("app.chat.jev.mode", value)));

        ChatJevProperties jev = binder.bind("app.chat.jev", ChatJevProperties.class).get();

        assertThat(jev.mode()).isEqualTo(mode);
        assertThat(jev.calls()).isEqualTo(calls);
        assertThat(jev.on()).isEqualTo(on);
    }

    @Test
    @DisplayName("모드가 없으면 off로 본다")
    void defaultsToOff_whenModeIsNull() {
        assertThat(new ChatJevProperties(null).mode()).isEqualTo(Mode.OFF);
    }
}
