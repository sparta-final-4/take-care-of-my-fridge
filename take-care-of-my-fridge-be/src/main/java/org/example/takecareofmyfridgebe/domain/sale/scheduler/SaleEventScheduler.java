package org.example.takecareofmyfridgebe.domain.sale.scheduler;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventStatusService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 시작·종료 시각이 지난 할인 행사의 상태를 정리한다
@Slf4j
@Component
@RequiredArgsConstructor
public class SaleEventScheduler {

    private final SaleEventStatusService saleEventStatusService;

    // 진행 중인지는 조회가 시각으로 판단하므로 이 작업은 상태 정리용이다. 늦어도 화면과 동작이 같아 1분에 한 번이면 된다.
    // 이전 실행이 끝나고 1분 뒤 다시 실행해 실행끼리 겹치지 않는다. 실패하면 다음 실행에서 다시 처리한다
    @Scheduled(fixedDelay = 60_000)
    public void openAndCloseDueEvents() {
        try {
            saleEventStatusService.openAndCloseDue(LocalDateTime.now(SaleEvent.ZONE));
        } catch (RuntimeException e) {
            log.error("할인 행사 열기·닫기 실패, 다음 실행에서 재시도", e);
        }
    }
}
