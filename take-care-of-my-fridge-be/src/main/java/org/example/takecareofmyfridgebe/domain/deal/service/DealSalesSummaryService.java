package org.example.takecareofmyfridgebe.domain.deal.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealSalesSummary;
import org.example.takecareofmyfridgebe.domain.order.dto.DailyProductSales;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DealSalesSummaryService {

    private final DealSalesInputService salesInputService;

    public Map<Long, DealSalesSummary> getByProduct(
            LocalDate targetWeekStart, DayOfWeek dayOfWeek, LocalDateTime asOf) {
        Map<Long, Long> quantities = new HashMap<>();
        for (DailyProductSales sale : salesInputService.getSameWeekdaySales(targetWeekStart, dayOfWeek, asOf)) {
            quantities.merge(sale.productId(), sale.quantity(), Long::sum);
        }
        Map<Long, DealSalesSummary> summaries = new HashMap<>();
        quantities.forEach((productId, quantity) -> summaries.put(productId,
                new DealSalesSummary(Math.toIntExact(quantity), Math.toIntExact((quantity + 2) / 4))));
        return Map.copyOf(summaries);
    }
}
