package org.example.takecareofmyfridgebe.domain.chat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

// app.chat.jev 설정(그림자 모드를 켤지)이 묶이는 값. 기본값은 application.yml만 읽어서 본다
// (시스템 환경 변수를 넣지 않아야 이 컴퓨터에 CHAT_JEV_ENABLED가 있어도 기본값을 볼 수 있다)
class ChatJevPropertiesTest {

    @Test
    @DisplayName("환경 변수가 없으면 application.yml 기본값으로 꺼져 있다")
    void bindsDisabled_whenNoEnvironmentVariables() throws IOException {
        List<PropertySource<?>> yaml = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        Binder binder = new Binder(ConfigurationPropertySources.from(yaml), new PropertySourcesPlaceholdersResolver(yaml));

        ChatJevProperties jev = binder.bind("app.chat.jev", ChatJevProperties.class).get();

        assertThat(jev.enabled()).isFalse();
    }

    @Test
    @DisplayName("속성으로 켜면 켜진 값으로 묶인다")
    void bindsEnabled_whenPropertyGiven() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("app.chat.jev.enabled", "true")));

        ChatJevProperties jev = binder.bind("app.chat.jev", ChatJevProperties.class).get();

        assertThat(jev.enabled()).isTrue();
    }
}
