package org.example.takecareofmyfridgebe.domain.search.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.infra.elasticsearch.index.EsIndexer;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * 처음 앱이 뜰 때 전체 인덱싱
 * <br>
 * alias가 없으면 ES 검색이 모두 실패해 MySQL 검색으로 대체되므로, 정기 재색인을 기다리지 않고 바로 만든다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EsIndexInitializer {

    private final List<EsIndexer> indexers;
    private final RecipeSearchProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void createMissingIndices() {
        if (!properties.useElasticSearch()) {
            return;
        }
        for (EsIndexer indexer : indexers) {
            try {
                if (!indexer.aliasExists()) {
                    log.info("[{}] alias가 없어 최초 색인", indexer.alias());
                    indexer.reindex();
                }
            } catch (IOException | RuntimeException e) {
                // 앱은 그대로 뜬다. 검색은 MySQL로 대체되고, 다음 정기 재색인에서 다시 시도한다
                log.error("[{}] 최초 색인 실패", indexer.alias(), e);
            }
        }
    }

}