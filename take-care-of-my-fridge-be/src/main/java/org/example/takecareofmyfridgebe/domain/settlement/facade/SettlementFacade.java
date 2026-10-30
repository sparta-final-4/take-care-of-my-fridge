package org.example.takecareofmyfridgebe.domain.settlement.facade;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.settlement.service.SettlementCommandService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class SettlementFacade {

    private static final String SALES_DATE_UNIQUE_KEY = "uk_daily_sales_sales_date";

    private final SettlementCommandService settlementCommandService;

    // 스케줄러와 조회가 같은 날의 첫 행을 동시에 만들면 한쪽은 UNIQUE 충돌로 롤백된다.
    // 그때는 새 트랜잭션에서 한 번 더 집계해 먼저 생긴 행을 덮어쓴다
    public void aggregate(LocalDate salesDate) {
        try {
            settlementCommandService.aggregate(salesDate);
        } catch (DataIntegrityViolationException e) {
            String message = e.getMostSpecificCause().getMessage();
            if (message == null || !message.toLowerCase().contains(SALES_DATE_UNIQUE_KEY)) {
                throw e;
            }
            settlementCommandService.aggregate(salesDate);
        }
    }
}
