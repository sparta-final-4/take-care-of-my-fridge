package org.example.takecareofmyfridgebe.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.settlement.dto.DailySalesSum;
import org.example.takecareofmyfridgebe.domain.settlement.entity.DailySales;
import org.example.takecareofmyfridgebe.domain.settlement.repository.DailySalesRepository;
import org.example.takecareofmyfridgebe.domain.settlement.repository.SalesHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementCommandService {

    private final SalesHistoryRepository salesHistoryRepository;
    private final DailySalesRepository dailySalesRepository;

    // 하루(한국 시간 0시 ~ 다음 날 0시)의 결제 이력을 처음부터 다시 세어 daily_sales에 저장한다.
    // 결제는 결제 완료 시점, 환불·콘솔 취소는 취소 시점에 잡힌다. 행이 이미 있으면 새 합계로 덮어쓴다.
    // 조회(readOnly 트랜잭션) 중에 불려도 저장되고, 충돌로 롤백돼도 호출한 쪽 트랜잭션을 망치지 않도록 새 트랜잭션에서 실행한다
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aggregate(LocalDate salesDate) {
        DailySalesSum sum = salesHistoryRepository.sumSalesBetween(salesDate.atStartOfDay(),
                salesDate.plusDays(1).atStartOfDay());
        int paidCount = Math.toIntExact(sum.paidCount());

        dailySalesRepository.findBySalesDate(salesDate).ifPresentOrElse(
                dailySales -> dailySales.reaggregate(sum.paidAmount(), sum.refundedAmount(), paidCount),
                () -> dailySalesRepository.saveAndFlush(DailySales.builder()
                        .salesDate(salesDate)
                        .paidAmount(sum.paidAmount())
                        .refundedAmount(sum.refundedAmount())
                        .paidCount(paidCount)
                        .build()));
    }
}
