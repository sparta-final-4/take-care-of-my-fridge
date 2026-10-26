package org.example.takecareofmyfridgebe.domain.forecast.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.forecast.dto.DailyNetSales;
import org.example.takecareofmyfridgebe.domain.forecast.repository.ForecastSalesRepository;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NetSalesService {

    private final ForecastSalesRepository salesRepository;

    public List<DailyNetSales> getDailySales(LocalDate from, LocalDate to, LocalDateTime asOf) {
        if (from == null || to == null || asOf == null || from.isAfter(to)) {
            throw new IllegalArgumentException("판매 집계 기간과 기준 시각이 올바르지 않습니다.");
        }
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        Map<ProductDay, Long> quantities = new HashMap<>();
        for (DailyNetSales sale : salesRepository.findConfirmedSales(start, end, asOf)) {
            quantities.merge(new ProductDay(sale.productId(), sale.salesDate()), sale.quantity(), Long::sum);
        }
        for (DailyNetSales refund : salesRepository.findCompletedRefunds(start, end, asOf,
                RefundStatus.COMPLETED)) {
            quantities.merge(new ProductDay(refund.productId(), refund.salesDate()), -refund.quantity(), Long::sum);
        }
        List<DailyNetSales> result = new ArrayList<>();
        quantities.forEach((key, quantity) -> {
            if (quantity < 0) {
                throw new IllegalStateException("환불 수량이 구매 확정 수량보다 큽니다: " + key.productId());
            }
            result.add(new DailyNetSales(key.productId(), key.date(), quantity));
        });
        result.sort(Comparator.comparing(DailyNetSales::salesDate).thenComparing(DailyNetSales::productId));
        return result;
    }

    private record ProductDay(Long productId, LocalDate date) {
    }
}
