package org.example.takecareofmyfridgebe.domain.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.MsearchRequest;
import co.elastic.clients.elasticsearch.core.MsearchResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.msearch.MultiSearchResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientNameResolver;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties.IngredientIndexProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 냉장고 재료명 정제 (ES). 사전에서 바로 찾지 못한 이름을 표준 재료명으로 바꾼다.
 * <br>
 * 1) 별칭: 입력 전체, 없으면 띄어쓰기 단위 단어로 aliases 키를 찾는다 ("무항생제 신선란" -> 신선란 -> 달걀)
 * 2) 끝 단어: 입력이 2자 이상 재료명·별칭으로 끝나면 그 재료로 정한다 ("서울우유" -> 우유, "꿀참외" -> 참외).
 *    한국어 복합어는 핵심 명사가 끝에 오므로 "새우깡", "마늘빵"처럼 앞에만 재료명이 있는 이름은 잡지 않는다
 * 3) 벡터: autoMatchByVector를 켰을 때만. 측정 결과 오답이 많아 기본은 끈다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngredientNameSearchService implements IngredientNameResolver {

    // 끝 단어 비교용 키 표. 재료는 자주 바뀌지 않으므로 메모리에 두고 주기적으로 다시 읽는다
    private static final Duration KEY_TABLE_TTL = Duration.ofMinutes(10);
    private static final int MAX_INGREDIENTS = 10_000;
    private static final int MIN_SUFFIX_LENGTH = 2;

    private final ElasticsearchClient esClient;
    private final RecipeSearchProperties properties;
    private final RecipeEmbedder embedder;

    private volatile KeyTable keyTable;

    @Override
    public Map<String, String> resolveAll(Collection<String> names) {
        if (!properties.useElasticSearch() || names.isEmpty()) {
            return Map.of();
        }

        Map<String, String> resolved = new HashMap<>();
        try {
            resolved.putAll(resolveByAlias(names));
        } catch (IOException | RuntimeException e) {
            // ES가 안 되면 다음 단계도 못 쓰므로 바로 끝낸다. 호출한 쪽은 ING_CUSTOM으로 처리한다
            log.warn("재료명 별칭 조회 실패, 정제 없이 진행: names={}", names, e);
            return Map.of();
        }

        List<String> remaining = remaining(names, resolved);
        if (!remaining.isEmpty()) {
            try {
                resolved.putAll(pickBySuffix(remaining, keyTable()));
            } catch (IOException | RuntimeException e) {
                log.warn("재료명 끝 단어 조회 실패, 별칭 결과만 사용: names={}", remaining, e);
            }
        }

        remaining = remaining(names, resolved);
        if (!remaining.isEmpty() && ingredientIndex().autoMatchByVector()) {
            try {
                resolved.putAll(resolveByVector(remaining));
            } catch (IOException | RuntimeException e) {
                log.warn("재료명 벡터 조회 실패, 앞 단계 결과만 사용: names={}", remaining, e);
            }
        }
        return resolved;
    }

    private static List<String> remaining(Collection<String> names, Map<String, String> resolved) {
        return names.stream()
                .filter(name -> !resolved.containsKey(name))
                .distinct()
                .toList();
    }

    /**
     * 입력 키가 표의 키(2자 이상)로 끝나면 그 재료. 가장 긴 키를 쓰고, 길이가 같은 다른 재료가 있으면 정하지 않는다.
     * <br>
     * 입력 전체와 같은 키는 별칭 단계에서 이미 봤으므로 여기서는 보지 않는다.
     */
    static Map<String, String> pickBySuffix(Collection<String> names, Map<String, String> nameByKey) {
        Map<String, String> result = new HashMap<>();
        for (String name : names) {
            String input = IngredientAliasDictionary.key(name);
            int bestLength = 0;
            Set<String> best = new HashSet<>();
            for (Map.Entry<String, String> entry : nameByKey.entrySet()) {
                String key = entry.getKey();
                if (key.length() < MIN_SUFFIX_LENGTH || key.length() >= input.length() || !input.endsWith(key)) {
                    continue;
                }
                if (key.length() > bestLength) {
                    bestLength = key.length();
                    best.clear();
                }
                if (key.length() == bestLength) {
                    best.add(entry.getValue());
                }
            }
            if (best.size() == 1) {
                result.put(name, best.iterator().next());
            }
        }
        return result;
    }

    private Map<String, String> keyTable() throws IOException {
        KeyTable table = keyTable;
        if (table == null || table.loadedAt().plus(KEY_TABLE_TTL).isBefore(Instant.now())) {
            table = new KeyTable(loadKeyTable(), Instant.now());
            keyTable = table;
        }
        return table.nameByKey();
    }

    // 표준명·별칭 키 -> 표준명. 두 재료가 같은 키를 가지면 어느 쪽인지 모르므로 뺀다
    private Map<String, String> loadKeyTable() throws IOException {
        SearchResponse<IngredientHit> response = esClient.search(SearchRequest.of(s -> s
                .index(ingredientIndex().alias())
                .size(MAX_INGREDIENTS)
                .source(src -> src.filter(f -> f.includes("name", "aliases")))), IngredientHit.class);

        Map<String, String> nameByKey = new HashMap<>();
        Set<String> conflicts = new HashSet<>();
        for (Hit<IngredientHit> hit : response.hits().hits()) {
            IngredientHit doc = hit.source();
            if (doc == null || doc.aliases() == null) {
                continue;
            }
            for (String key : doc.aliases()) {
                String previous = nameByKey.put(key, doc.name());
                if (previous != null && !previous.equals(doc.name())) {
                    conflicts.add(key);
                }
            }
        }
        conflicts.forEach(nameByKey::remove);
        return nameByKey;
    }

    // 입력 전체 키와 단어 키를 한 번에 조회한다
    private Map<String, String> resolveByAlias(Collection<String> names) throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        for (String name : names) {
            keys.addAll(candidateKeys(name));
        }
        if (keys.isEmpty()) {
            return Map.of();
        }

        SearchResponse<IngredientHit> response = esClient.search(aliasRequest(keys), IngredientHit.class);

        Map<String, String> nameByKey = new HashMap<>();
        for (Hit<IngredientHit> hit : response.hits().hits()) {
            IngredientHit doc = hit.source();
            if (doc == null || doc.aliases() == null) {
                continue;
            }
            for (String alias : doc.aliases()) {
                if (keys.contains(alias)) {
                    nameByKey.put(alias, doc.name());
                }
            }
        }
        return pickByAlias(names, nameByKey);
    }

    // aliases 키 중 하나라도 맞는 재료. 점수와 상관없으므로 filter로 건다
    SearchRequest aliasRequest(Collection<String> keys) {
        List<FieldValue> values = keys.stream().map(FieldValue::of).toList();
        return SearchRequest.of(s -> s
                .index(ingredientIndex().alias())
                .query(q -> q.bool(b -> b.filter(f -> f.terms(t -> t.field("aliases").terms(v -> v.value(values))))))
                .size(keys.size())
                .source(src -> src.filter(f -> f.includes("name", "aliases"))));
    }

    /**
     * 입력 전체 키가 맞으면 그 재료, 아니면 단어 키들이 가리키는 재료가 하나뿐일 때만 쓴다.
     * <br>
     * "국산 흙대파" -> 흙대파 -> 대파 / "두부 김치"처럼 단어마다 다른 재료면 정하지 않는다.
     */
    static Map<String, String> pickByAlias(Collection<String> names, Map<String, String> nameByKey) {
        Map<String, String> result = new HashMap<>();
        for (String name : names) {
            String whole = nameByKey.get(IngredientAliasDictionary.key(name));
            if (whole != null) {
                result.put(name, whole);
                continue;
            }
            Set<String> byWord = new HashSet<>();
            for (String word : words(name)) {
                String matched = nameByKey.get(IngredientAliasDictionary.key(word));
                if (matched != null) {
                    byWord.add(matched);
                }
            }
            if (byWord.size() == 1) {
                result.put(name, byWord.iterator().next());
            }
        }
        return result;
    }

    // 입력 전체 키 + (두 단어 이상이면) 단어별 키
    static List<String> candidateKeys(String name) {
        List<String> keys = new ArrayList<>();
        String whole = IngredientAliasDictionary.key(name);
        if (whole.isEmpty()) {
            return keys;
        }
        keys.add(whole);
        List<String> words = words(name);
        if (words.size() > 1) {
            words.forEach(word -> keys.add(IngredientAliasDictionary.key(word)));
        }
        return keys;
    }

    private static List<String> words(String name) {
        return List.of(name.strip().split("\\s+"));
    }

    // 이름마다 가장 가까운 재료 하나. 임베딩은 한 번에, 검색은 msearch 한 번으로 묶는다
    // autoMatchByVector가 꺼져 있으면 호출되지 않는다. 후보 제안처럼 사용자가 확인하는 기능에서 재사용한다
    private Map<String, String> resolveByVector(List<String> names) throws IOException {
        List<float[]> vectors = embedder.embedAll(names);
        MsearchResponse<IngredientHit> response = esClient.msearch(vectorRequest(vectors), IngredientHit.class);

        Map<String, String> result = new HashMap<>();
        List<MultiSearchResponseItem<IngredientHit>> items = response.responses();
        for (int i = 0; i < items.size(); i++) {
            MultiSearchResponseItem<IngredientHit> item = items.get(i);
            if (!item.isResult() || item.result().hits().hits().isEmpty()) {
                continue;
            }
            Hit<IngredientHit> top = item.result().hits().hits().get(0);
            if (top.source() != null) {
                result.put(names.get(i), top.source().name());
                log.info("재료명 벡터 매칭: {} -> {} (score={})", names.get(i), top.source().name(), top.score());
            }
        }
        return result;
    }

    // 벡터마다 k=1 knn 하나. 유사도 기준 미만이면 ES가 결과를 비워서 돌려준다
    MsearchRequest vectorRequest(List<float[]> vectors) {
        IngredientIndexProperties index = ingredientIndex();
        return MsearchRequest.of(m -> {
            for (float[] vector : vectors) {
                m.searches(item -> item
                        .header(h -> h.index(index.alias()))
                        .body(b -> b
                                .knn(k -> k
                                        .field("embedding")
                                        .queryVector(toList(vector))
                                        .k(1)
                                        .numCandidates(properties.vector().numCandidates())
                                        .similarity(index.minSimilarity()))
                                .size(1)
                                .source(src -> src.filter(f -> f.includes("name")))));
            }
            return m;
        });
    }

    private IngredientIndexProperties ingredientIndex() {
        return properties.indexProperties().ingredient();
    }

    private static List<Float> toList(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }

    // ingredients 인덱스 문서 중 조회에 필요한 필드
    record IngredientHit(String name, List<String> aliases) {}

    private record KeyTable(Map<String, String> nameByKey, Instant loadedAt) {}

}
