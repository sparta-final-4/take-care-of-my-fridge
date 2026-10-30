package org.example.takecareofmyfridgebe.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SalesAmountResponse;
import org.example.takecareofmyfridgebe.domain.settlement.dto.SettlementPaymentResponse;
import org.example.takecareofmyfridgebe.domain.settlement.entity.DailySales;
import org.example.takecareofmyfridgebe.domain.settlement.repository.DailySalesRepository;
import org.example.takecareofmyfridgebe.domain.settlement.repository.SalesHistoryRepository;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementQueryService {

    private final DailySalesRepository dailySalesRepository;
    private final SalesHistoryRepository salesHistoryRepository;

    // 기간 안에서 daily_sales 행이 없는 날. 스케줄러 배포 전 날짜나 스케줄러가 실패한 날이다
    public List<LocalDate> findMissingDates(LocalDate startDate, LocalDate endDate) {
        Set<LocalDate> aggregated = new HashSet<>(dailySalesRepository.findSalesDatesBetween(startDate, endDate));
        return startDate.datesUntil(endDate.plusDays(1))
                .filter(date -> !aggregated.contains(date))
                .toList();
    }

    public List<DailySales> getDailySales(LocalDate startDate, LocalDate endDate) {
        return dailySalesRepository.findBySalesDateBetweenOrderBySalesDateAsc(startDate, endDate);
    }

    // 아직 끝나지 않은 날의 from부터 to 전까지 매출. 저장하지 않고 결제 이력으로 바로 센다
    public SalesAmountResponse getSales(LocalDateTime from, LocalDateTime to) {
        return SalesAmountResponse.from(salesHistoryRepository.sumSalesBetween(from, to));
    }

    // from 이상 to 미만에 결제·환불이 있었던 결제를 한 건씩. page는 1부터 센다
    public PageResponse<SettlementPaymentResponse> getPayments(LocalDateTime from, LocalDateTime to, int page,
                                                               int size) {
        return PageResponse.of(salesHistoryRepository.findPaymentSalesBetween(from, to, PageRequest.of(page - 1, size))
                .map(SettlementPaymentResponse::from));
    }
}
