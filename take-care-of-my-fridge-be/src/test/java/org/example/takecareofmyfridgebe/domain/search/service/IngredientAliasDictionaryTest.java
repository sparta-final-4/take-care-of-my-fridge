package org.example.takecareofmyfridgebe.domain.search.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IngredientAliasDictionaryTest {

    private final IngredientAliasDictionary dictionary = new IngredientAliasDictionary(new ObjectMapper());

    private static Resource json(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("비교용 키는 공백을 빼고 소문자로 맞춘다")
    void key_removesWhitespaceAndLowercases() {
        assertThat(IngredientAliasDictionary.key("절인 배추")).isEqualTo("절인배추");
        assertThat(IngredientAliasDictionary.key(" S P A M ")).isEqualTo("spam");
    }

    @Test
    @DisplayName("표준 재료명별로 별칭을 모으고, 설명 키(_)와 없는 재료를 가리키는 별칭은 버린다")
    void aliasesByName_groupsAliasesByExistingName() throws IOException {
        Resource file = json("""
                {"_설명": "무시", "달걀": {"계란": "달걀", "신선란": "달걀", "청고추": "없는재료"}}
                """);

        Map<String, List<String>> aliases = dictionary.aliasesByName(List.of(file), List.of("달걀", "대파"));

        assertThat(aliases.get("달걀")).isEqualTo(List.of("계란", "신선란"));
        assertThat(aliases.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 표준 재료명과 같은 별칭은 버린다 (표준명이 우선)")
    void aliasesByName_skipsAlias_whenAliasIsAnotherName() throws IOException {
        Resource file = json("""
                {"g": {"검은깨": "참깨", "흑임자": "검은깨"}}
                """);

        Map<String, List<String>> aliases = dictionary.aliasesByName(List.of(file), List.of("참깨", "검은깨"));

        assertThat(aliases.containsKey("참깨")).isFalse();
        assertThat(aliases.get("검은깨")).isEqualTo(List.of("흑임자"));
    }

    @Test
    @DisplayName("뒤 파일이 같은 별칭을 덮어쓰고, 대상이 다시 별칭이면 한 번 따라간다")
    void aliasesByName_laterFileWinsAndFollowsOneHop() throws IOException {
        Resource first = json("""
                {"g": {"맛살": "어묵", "노란색파프리카": "노란파프리카"}}
                """);
        Resource second = json("""
                {"g": {"맛살": "게맛살", "노란파프리카": "파프리카"}}
                """);

        Map<String, List<String>> aliases = dictionary.aliasesByName(
                List.of(first, second), List.of("어묵", "게맛살", "파프리카"));

        assertThat(aliases.get("게맛살")).isEqualTo(List.of("맛살"));
        assertThat(aliases.containsKey("어묵")).isFalse();
        assertThat(aliases.get("파프리카").containsAll(List.of("노란색파프리카", "노란파프리카"))).isTrue();
    }

    @Test
    @DisplayName("실제 별칭 파일을 읽을 수 있고 계란·신선란이 달걀로 모인다")
    void aliasesByName_readsBundledAliasFiles() throws IOException {
        List<Resource> files = List.of(
                new ClassPathResource("elasticsearch/recipes/ingredient_synonyms.json"),
                new ClassPathResource("elasticsearch/ingredients/ingredient_aliases.json"));

        Map<String, List<String>> aliases = dictionary.aliasesByName(files, List.of("달걀", "대파", "절인 배추"));

        assertThat(aliases.get("달걀").containsAll(List.of("계란", "신선란", "특란"))).isTrue();
        assertThat(aliases.get("대파").contains("흙대파")).isTrue();
        assertThat(aliases.get("절인 배추").contains("절임배추")).isTrue();
    }

}
