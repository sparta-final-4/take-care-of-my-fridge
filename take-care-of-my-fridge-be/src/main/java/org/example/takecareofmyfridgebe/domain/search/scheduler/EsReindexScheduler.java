package org.example.takecareofmyfridgebe.domain.search.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.service.ProductIndexer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 스케쥴러 전체 인덱싱
 * <br>
 * 단건 색인 이벤트를 거치지 않은 DB 변경(SQL 직접 입력, DB 초기화, 단건 색인 실패)을 정기적으로 맞춘다.
 * 레시피·재료는 재색인할 때마다 전부 다시 임베딩(OpenAI)하므로 상품만 다시 색인한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EsReindexScheduler {

    private final ProductIndexer productIndexer;
    private final RecipeSearchProperties properties;

    // 매일 02시. dev 서버는 03~07시에 꺼지므로 그 밖의 시각으로 둔다
    // 서버가 여러 대여도 잠금을 잡은 한 대만 실행한다. 상품은 수십 건이라 몇 초면 끝난다
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "esProductReindex", lockAtMostFor = "10m", lockAtLeastFor = "1m")
    public void reindexProducts() {
        if (!properties.useElasticSearch()) {
            return;
        }

        try {
            productIndexer.reindex();
        } catch (IOException | RuntimeException e) {
            // 실패해도 alias는 이전 인덱스에 그대로 있어 검색은 계속된다. 다음 실행에서 다시 시도한다
            log.error("[{}] 정기 재색인 실패", productIndexer.alias(), e);
        }
    }

}
