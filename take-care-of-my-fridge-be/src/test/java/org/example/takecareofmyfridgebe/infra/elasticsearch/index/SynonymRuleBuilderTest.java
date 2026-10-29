package org.example.takecareofmyfridgebe.infra.elasticsearch.index;

import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.RecipeIndexProperties;
import org.example.takecareofmyfridgebe.infra.elasticsearch.config.EsIndexProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SynonymRuleBuilderTest {

    private final SynonymRuleBuilder builder = new SynonymRuleBuilder(JsonMapper.builder().build());

    private final EsIndexProperties recipeIndex = new RecipeIndexProperties(
            "recipes",
            new ClassPathResource("elasticsearch/recipes/recipes_index.json"),
            new ClassPathResource("elasticsearch/recipes/ingredient_synonyms.json"),
            new ClassPathResource("elasticsearch/recipes/synonyms.txt"),
            500);

    // "달걀, 계란, 삶은계란" → [달걀, 계란, 삶은계란]
    private List<String> ruleOf(List<String> rules, String name) {
        String rule = rules.stream()
                .filter(r -> r.startsWith(name + ", "))
                .findFirst()
                .orElseThrow();
        return Arrays.asList(rule.split(", "));
    }

    @Test
    @DisplayName("JSON 동의어는 대표명 뒤에 별칭을 붙인 규칙이 된다")
    void groupedRulesPutNameFirst() throws IOException {
        // When
        List<String> rules = builder.build(recipeIndex);

        // Then
        List<String> egg = ruleOf(rules, "달걀");
        assertTrue(egg.containsAll(List.of("계란", "삶은계란")));
    }

    @Test
    @DisplayName("대표명과 서로 포함하는 별칭은 규칙에서 빠진다")
    void skipsOverlappingAliases() throws IOException {
        // When
        List<String> rules = builder.build(recipeIndex);

        // Then: "고운고춧가루"는 대표명 "고춧가루"를 포함하므로 nori가 쪼개서 찾는다
        List<String> chili = ruleOf(rules, "고춧가루");
        assertTrue(chili.contains("고추가루"));
        assertFalse(chili.contains("고운고춧가루"));
    }

    @Test
    @DisplayName("txt 동의어는 주석과 빈 줄을 빼고 규칙으로 더한다")
    void lineRulesSkipCommentsAndBlankLines() throws IOException {
        // When
        List<String> rules = builder.build(recipeIndex);

        // Then
        assertTrue(rules.contains("파스타, 스파게티"));
        assertTrue(rules.stream().noneMatch(rule -> rule.isBlank() || rule.startsWith("#")));
    }

    @Test
    @DisplayName("동의어 파일이 없는 인덱스는 빈 규칙을 반환한다")
    void emptyWhenNoSynonymFiles() throws IOException {
        // Given
        EsIndexProperties noSynonyms = new RecipeIndexProperties(
                "products", new ClassPathResource("elasticsearch/recipes/recipes_index.json"), null, null, 500);

        // When
        List<String> rules = builder.build(noSynonyms);

        // Then
        assertEquals(List.of(), rules);
    }
}
