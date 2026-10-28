package org.example.takecareofmyfridgebe.domain.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.search.config.EsIndexProperties;
import org.example.takecareofmyfridgebe.domain.search.dto.EsDocument;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
public abstract class EsIndexer {

    private static final List<String> SYNONYM_FILTER_TYPES = List.of("synonym", "synonym_graph");

    private final ElasticsearchClient esClient;
    private final SynonymRuleBuilder synonymRuleBuilder;
    private final ObjectMapper objectMapper;

    protected EsIndexer(ElasticsearchClient esClient, SynonymRuleBuilder synonymRuleBuilder) {
        this.esClient = esClient;
        this.synonymRuleBuilder = synonymRuleBuilder;
        this.objectMapper = new ObjectMapper();
    }

    protected abstract EsIndexProperties indexProperties();

    protected abstract EsDocumentReader documentReader();

    public String alias() {
        return indexProperties().alias();
    }

    public boolean aliasExists() throws IOException {
        return esClient.indices().existsAlias(a -> a.name(alias())).value();
    }

    /**
     * 전체 재인덱싱
     */
    public String reindex() throws IOException {
        String alias = indexProperties().alias();
        String newIndex = alias + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        // 원본 DB -> 문서
        List<EsDocument> docs = documentReader().readAll();
        log.info("[{}] 원본에서 {}건 읽음", alias, docs.size());

        // 동의어를 넣어서 새 인덱스 생성
        String body = indexBody().toString();
        esClient.indices().create(c -> c.withJson(new StringReader(body)).index(newIndex));

        long count;
        try {
            // batchSize 단위로 bulk 색인
            int batchSize = indexProperties().batchSize();
            for (int start = 0; start < docs.size(); start += batchSize) {
                bulk(newIndex, docs.subList(start, Math.min(start + batchSize, docs.size())));
            }
            esClient.indices().refresh(r -> r.index(newIndex));

            // 건수 확인. 틀리면 alias를 옮기지 않는다 (기존 검색은 그대로 유지)
            count = esClient.count(c -> c.index(newIndex)).count();
            if (count != docs.size()) {
                throw new IllegalStateException("[%s] 건수 불일치: 원본 %d / ES %d"
                        .formatted(alias, docs.size(), count));
            }
        } catch (IOException | RuntimeException e) {
            // alias를 옮기기 전이라 검색은 이전 인덱스로 계속된다. 재시도마다 쌓이지 않도록 실패한 새 인덱스는 지운다
            deleteFailedIndex(alias, newIndex, e);
            throw e;
        }

        // alias 전환 및 이전 인덱스 삭제
        switchAlias(alias, newIndex);
        log.info("[{}] alias -> {} ({}건)", alias, newIndex, count);

        return newIndex;
    }

    /**
     * 문서 하나만 다시 색인한다.
     * <br>
     * alias에 바로 쓰므로, alias가 가리키는 인덱스가 하나일 때만 동작한다.
     */
    public void indexOne(long id) throws IOException {
        Optional<EsDocument> doc = documentReader().readOne(id);
        if (doc.isEmpty()) {
            // 원본에서 지워졌으면 ES에서도 지운다
            deleteOne(id);
            return;
        }

        String alias = indexProperties().alias();
        esClient.index(i -> i
                .index(alias)
                .id(String.valueOf(id))
                .document(doc.get().source()));
        log.info("[{}] 단건 색인: {}", alias, id);
    }

    public void deleteOne(long id) throws IOException {
        String alias = indexProperties().alias();
        try {
            esClient.delete(d -> d.index(alias).id(String.valueOf(id)));
            log.info("[{}] 단건 삭제: {}", alias, id);
        } catch (ElasticsearchException e) {
            if (e.status() != 404) {
                throw e;
            }
            // 이미 없으면 할 일이 없다
        }
    }

    ObjectNode indexBody() throws IOException {
        EsIndexProperties indexProperties = indexProperties();
        ObjectNode body;
        try (InputStream in = indexProperties.indexSettings().getInputStream()) {
            body = (ObjectNode) objectMapper.readTree(in);
        }

        List<ObjectNode> synonymFilters = new ArrayList<>();
        Set<Map.Entry<String, JsonNode>> filterProperties = body
                .path("settings")
                .path("analysis")
                .path("filter")
                .properties();

        for (Map.Entry<String, JsonNode> filter: filterProperties) {
            if (SYNONYM_FILTER_TYPES.contains(filter.getValue().path("type").asString())) {
                synonymFilters.add((ObjectNode) filter.getValue());
            }
        }

        if (synonymFilters.isEmpty()) {
            return body;
        }

        List<String> rules = synonymRuleBuilder.build(indexProperties);
        for (ObjectNode filter : synonymFilters) {
            ArrayNode synonyms = filter.putArray("synonyms");
            rules.forEach(synonyms::add);
        }
        log.info("[{}] 동의어 규칙 {}개 (필터 {}개)", indexProperties.alias(), rules.size(), synonymFilters.size());

        return body;
    }

    private void bulk(String index, List<EsDocument> docs) throws IOException {
        BulkResponse response = esClient.bulk(b -> {
            b.index(index);
            for (EsDocument doc : docs) {
                b.operations(op ->
                        op.index(i ->
                                i.id(String.valueOf(doc.id())).document(doc.source())
                        )
                );
            }
            return b;
        });

        if (response.errors()) {
            BulkResponseItem failed = response.items().stream()
                    .filter(item -> item.error() != null)
                    .findFirst()
                    .orElseThrow();
            throw new IllegalStateException("bulk 실패 (id %s): %s".formatted(failed.id(), failed.error().reason()));
        }
    }

    private void switchAlias(String alias, String newIndex) throws IOException {
        List<String> oldIndices = new ArrayList<>();
        if (aliasExists()) {
            oldIndices.addAll(esClient.indices().getAlias(a -> a.name(alias)).aliases().keySet());
        }

        // remove + add 를 한 요청으로 보내야 alias가 비는 순간이 없다
        esClient.indices().updateAliases(u -> {
            for (String old : oldIndices) {
                u.actions(a -> a.remove(r -> r.index(old).alias(alias)));
            }
            u.actions(a -> a.add(ad -> ad.index(newIndex).alias(alias)));
            return u;
        });

        // alias는 이미 새 인덱스를 가리키므로 이전 인덱스 삭제가 실패해도 재색인은 성공이다
        for (String old : oldIndices) {
            try {
                esClient.indices().delete(d -> d.index(old));
                log.info("[{}] 이전 인덱스 삭제: {}", alias, old);
            } catch (IOException | RuntimeException e) {
                log.warn("[{}] 이전 인덱스 삭제 실패, 직접 지워야 함: {}", alias, old, e);
            }
        }
    }

    private void deleteFailedIndex(String alias, String newIndex, Exception cause) {
        try {
            esClient.indices().delete(d -> d.index(newIndex).ignoreUnavailable(true));
            log.warn("[{}] 재색인 실패로 새 인덱스 삭제: {}", alias, newIndex);
        } catch (IOException | RuntimeException e) {
            // 원래 실패 원인을 덮지 않도록 삭제 실패는 원인 예외에 붙여 둔다
            cause.addSuppressed(e);
            log.warn("[{}] 실패한 새 인덱스 삭제도 실패, 직접 지워야 함: {}", alias, newIndex, e);
        }
    }

}
