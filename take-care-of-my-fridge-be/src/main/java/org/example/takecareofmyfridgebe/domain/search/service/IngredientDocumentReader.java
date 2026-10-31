package org.example.takecareofmyfridgebe.domain.search.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.ingredient.dto.IngredientIndexSource;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.infra.elasticsearch.dto.EsDocument;
import org.example.takecareofmyfridgebe.infra.elasticsearch.index.EsDocumentReader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 냉장고 재료명 정제용 문서. 표준 재료명 + 별칭 키 + (재료 임베딩을 켰으면) 임베딩.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngredientDocumentReader implements EsDocumentReader {

    private final IngredientService ingredientService;
    private final IngredientAliasDictionary aliasDictionary;
    private final RecipeEmbedder embedder;
    private final RecipeSearchProperties properties;

    @Override
    public List<EsDocument> readAll() {
        List<IngredientIndexSource> sources = ingredientService.findIndexSources();
        Map<String, List<String>> aliasesByName = aliasesByName(sources);

        List<Map<String, Object>> docs = sources.stream()
                .map(source -> toDocument(source, aliasesByName.getOrDefault(source.name(), List.of())))
                .toList();
        attachEmbeddings(docs, sources.stream()
                .map(source -> embeddingText(source.name(), aliasesByName.getOrDefault(source.name(), List.of())))
                .toList());

        return docs.stream()
                .map(doc -> new EsDocument((Long) doc.get("id"), doc))
                .toList();
    }

    @Override
    // 단건 색인용: 별칭이 다른 재료명과 겹치는지 보려면 전체 재료명이 필요해서 전체를 읽는다
    public Optional<EsDocument> readOne(long ingredientId) {
        Optional<IngredientIndexSource> source = ingredientService.findIndexSource(ingredientId);
        if (source.isEmpty()) {
            return Optional.empty();
        }
        List<String> aliases = aliasesByName(ingredientService.findIndexSources())
                .getOrDefault(source.get().name(), List.of());

        Map<String, Object> doc = toDocument(source.get(), aliases);
        attachEmbeddings(List.of(doc), List.of(embeddingText(source.get().name(), aliases)));
        return Optional.of(new EsDocument(source.get().id(), doc));
    }

    private Map<String, List<String>> aliasesByName(List<IngredientIndexSource> sources) {
        try {
            return aliasDictionary.aliasesByName(
                    properties.indexProperties().ingredient().aliasDictionaries(),
                    sources.stream().map(IngredientIndexSource::name).toList());
        } catch (IOException e) {
            throw new UncheckedIOException("재료 별칭 사전을 읽지 못했다", e);
        }
    }

    // 매핑에 필드를 추가하면 여기도 추가한다
    private Map<String, Object> toDocument(IngredientIndexSource source, List<String> aliases) {
        // 표준명 자신도 키로 넣어 "절인배추"처럼 띄어쓰기만 다른 입력을 같은 조회로 찾는다
        Set<String> keys = new LinkedHashSet<>();
        keys.add(IngredientAliasDictionary.key(source.name()));
        aliases.forEach(alias -> keys.add(IngredientAliasDictionary.key(alias)));

        Map<String, Object> doc = new HashMap<>();
        doc.put("id", source.id());
        doc.put("name", source.name());
        doc.put("aliases", List.copyOf(keys));
        return doc;
    }

    // "달걀 (계란, 신선란, 특란)": 이름만으로는 정보가 적어 별칭을 함께 임베딩한다
    static String embeddingText(String name, List<String> aliases) {
        return aliases.isEmpty() ? name : name + " (" + String.join(", ", aliases) + ")";
    }

    /**
     * 재료 임베딩을 켰을 때만 임베딩한다. 실패하면 벡터 없이 색인하고, 다음 재색인에서 채운다.
     * <br>
     * 자동 매칭은 벡터 없이도 동작하므로 OpenAI 장애가 재색인을 막지 않게 한다.
     */
    private void attachEmbeddings(List<Map<String, Object>> docs, List<String> texts) {
        if (!properties.indexProperties().ingredient().embeddingEnabled() || docs.isEmpty()) {
            return;
        }
        try {
            List<float[]> vectors = embedder.embedAll(texts);
            for (int i = 0; i < docs.size(); i++) {
                docs.get(i).put("embedding", vectors.get(i));
            }
        } catch (RuntimeException e) {
            log.warn("[{}] 재료 임베딩 실패, 벡터 없이 색인한다", properties.indexProperties().ingredient().alias(), e);
        }
    }

}
