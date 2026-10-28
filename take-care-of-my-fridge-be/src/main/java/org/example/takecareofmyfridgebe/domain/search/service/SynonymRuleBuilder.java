package org.example.takecareofmyfridgebe.domain.search.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.search.config.EsIndexProperties;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Component
@RequiredArgsConstructor
public class SynonymRuleBuilder {

    private final ObjectMapper objectMapper;

    public List<String> build(EsIndexProperties index) throws IOException {
        List<String> rules = new ArrayList<>(groupedRules(index.synonyms()));
        rules.addAll(lineRules(index.extraSynonyms()));
        return rules;
    }

    /**
     * 확장 가능성을 고려하여 resource를 받고 동의어 규칙을 그룹화하여 반환한다.
     * <br>
     * {"달걀": {"계란": "달걀", "삶은계란": "달걀"}} -> "달걀, 계란, 삶은계란"
     */
    private List<String> groupedRules(Resource synonymsResource) throws IOException {
        if (synonymsResource == null) {
            return List.of();
        }

        Map<String, Set<String>> aliasesByName = new LinkedHashMap<>();

        JsonNode root;
        // synonymsResource 로부터 JSON을 읽어들여서 aliasesByName 맵을 구성한다.
        try (InputStream in = synonymsResource.getInputStream()) {
            root = objectMapper.readTree(in);
        }

        for (Map.Entry<String, JsonNode> group : root.properties()) {
            if (group.getKey().startsWith("_")) {
                continue;
            }

            for (Map.Entry<String, JsonNode> entry : group.getValue().properties()) {
                String alias = entry.getKey();
                String name = entry.getValue().asString();
                // 다진마늘/마늘처럼 한쪽이 다른 쪽을 포함하면 nori가 이미 쪼개서 찾으므로 뺀다
                if (alias.equals(name) || alias.contains(name) || name.contains(alias)) {
                    continue;
                }
                aliasesByName.computeIfAbsent(name, k -> new TreeSet<>()).add(alias);
            }
        }

        List<String> rules = new ArrayList<>();
        aliasesByName.forEach((name, aliases) -> rules.add(name + ", " + String.join(", ", aliases)));
        return rules;
    }

    /**
     * txt 에서 빈 줄과 # 주석을 뺀 나머지 줄을 규칙으로 반환한다.
     */
    private List<String> lineRules(Resource extraSynonyms) throws IOException {
        if (extraSynonyms == null) {
            return List.of();
        }

        return extraSynonyms.getContentAsString(StandardCharsets.UTF_8)
                .lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
    }

}
