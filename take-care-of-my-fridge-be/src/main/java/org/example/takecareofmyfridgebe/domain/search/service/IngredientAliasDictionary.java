package org.example.takecareofmyfridgebe.domain.search.service;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 재료 별칭 사전. {"그룹": {"별칭": "표준명"}} 형식 파일들을 읽어 표준 재료명별 별칭 목록을 만든다.
 * <br>
 * 레시피 검색 동의어(ingredient_synonyms.json)와 같은 형식이라 두 파일을 함께 쓴다.
 */
@Component
@RequiredArgsConstructor
public class IngredientAliasDictionary {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final ObjectMapper objectMapper;

    /**
     * 공백을 빼고 소문자로 맞춘 비교용 키. 색인(aliases 필드)과 조회가 같은 규칙을 써야 한다.
     * <br>
     * "절인 배추", "절인배추"는 같은 키가 된다.
     */
    public static String key(String name) {
        return WHITESPACE.matcher(name).replaceAll("").toLowerCase(Locale.ROOT);
    }

    /**
     * @param dictionaries 별칭 파일 목록. 뒤 파일이 같은 별칭을 덮어쓴다
     * @param names        표준 재료명 목록. 대상이 여기에 없는 별칭은 버린다
     * @return 표준 재료명 -> 별칭 원문 목록
     */
    public Map<String, List<String>> aliasesByName(List<Resource> dictionaries, Collection<String> names) throws IOException {
        Map<String, String> nameByKey = new HashMap<>();
        for (String name : names) {
            nameByKey.putIfAbsent(key(name), name);
        }

        // 별칭 키 -> (별칭 원문, 대상). 뒤 파일이 덮어쓴다
        Map<String, AliasEntry> entries = new LinkedHashMap<>();
        for (Resource dictionary : dictionaries == null ? List.<Resource>of() : dictionaries) {
            readEntries(dictionary, entries);
        }

        Map<String, List<String>> result = new HashMap<>();
        for (Map.Entry<String, AliasEntry> entry : entries.entrySet()) {
            // 다른 표준 재료명과 같은 별칭은 그 재료명이 우선이다 (예: 자기 자신을 가리키는 "청주" -> "청주")
            if (nameByKey.containsKey(entry.getKey())) {
                continue;
            }
            String name = resolveTarget(entry.getValue().target(), entries, nameByKey);
            if (name != null) {
                result.computeIfAbsent(name, k -> new ArrayList<>()).add(entry.getValue().alias());
            }
        }
        return result;
    }

    private void readEntries(Resource dictionary, Map<String, AliasEntry> entries) throws IOException {
        JsonNode root;
        try (InputStream in = dictionary.getInputStream()) {
            root = objectMapper.readTree(in);
        }
        for (Map.Entry<String, JsonNode> group : root.properties()) {
            if (group.getKey().startsWith("_")) {
                continue;
            }
            for (Map.Entry<String, JsonNode> entry : group.getValue().properties()) {
                entries.put(key(entry.getKey()), new AliasEntry(entry.getKey(), entry.getValue().asString()));
            }
        }
    }

    /**
     * 대상이 표준 재료명이면 그대로, 대상이 다시 별칭이면 한 번만 따라간다.
     * <br>
     * 예: "노란색파프리카" -> "노란파프리카"(표준명 아님) -> "파프리카"
     */
    private static String resolveTarget(String target, Map<String, AliasEntry> entries, Map<String, String> nameByKey) {
        String name = nameByKey.get(key(target));
        if (name != null) {
            return name;
        }
        AliasEntry next = entries.get(key(target));
        return next == null ? null : nameByKey.get(key(next.target()));
    }

    private record AliasEntry(String alias, String target) {}

}
