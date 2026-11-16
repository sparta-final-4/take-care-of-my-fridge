package org.example.takecareofmyfridgebe.global.config;

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
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// app.typesafe 설정이 묶이는 값. 기본값은 application.yml만 읽어서 본다
// (시스템 환경 변수를 넣지 않아야 이 컴퓨터에 TYPESAFE_API_KEY가 있어도 기본값을 볼 수 있다)
class TypeSafePropertiesTest {

    // 테스트용 가짜 키 (실제 키가 아니다)
    private static final String FAKE_KEY = "fake-test-key-0000";

    @Test
    @DisplayName("환경 변수가 없으면 application.yml 기본값으로 TypeSafe 주소, 빈 키, jev-1.14.0, 3초로 묶인다")
    void bindsDefaults_whenNoEnvironmentVariables() throws IOException {
        List<PropertySource<?>> yaml = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        Binder binder = new Binder(ConfigurationPropertySources.from(yaml), new PropertySourcesPlaceholdersResolver(yaml));

        TypeSafeProperties typeSafe = binder.bind("app.typesafe", TypeSafeProperties.class).get();

        assertThat(typeSafe.baseUrl()).isEqualTo("https://api.typesafe.ai");
        assertThat(typeSafe.apiKey()).isEmpty();
        assertThat(typeSafe.hasApiKey()).isFalse();
        assertThat(typeSafe.model()).isEqualTo("jev-1.14.0");
        assertThat(typeSafe.timeout()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    @DisplayName("속성으로 주소·키·모델·시간을 바꾸면 그 값으로 묶인다")
    void bindsOverriddenValues_whenPropertiesGiven() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "app.typesafe.base-url", "http://localhost:8089",
                "app.typesafe.api-key", FAKE_KEY,
                "app.typesafe.model", "jev-preview",
                "app.typesafe.timeout", "1500ms")));

        TypeSafeProperties typeSafe = binder.bind("app.typesafe", TypeSafeProperties.class).get();

        assertThat(typeSafe.baseUrl()).isEqualTo("http://localhost:8089");
        assertThat(typeSafe.apiKey()).isEqualTo(FAKE_KEY);
        assertThat(typeSafe.hasApiKey()).isTrue();
        assertThat(typeSafe.model()).isEqualTo("jev-preview");
        assertThat(typeSafe.timeout()).isEqualTo(Duration.ofMillis(1500));
    }

    @Test
    @DisplayName("키가 있어도 toString에는 키 대신 설정됨만 나오고, 없으면 없음이 나온다")
    void hidesApiKey_whenPrinted() {
        TypeSafeProperties withKey = new TypeSafeProperties(null, FAKE_KEY, null, null);
        TypeSafeProperties withoutKey = new TypeSafeProperties(null, null, null, null);

        assertThat(withKey.toString()).doesNotContain(FAKE_KEY).contains("apiKey=(설정됨)", "model=jev-1.14.0", "timeout=PT3S");
        assertThat(withoutKey.toString()).contains("apiKey=(없음)");
    }
}
