package org.example.takecareofmyfridgebe.domain.deal.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.order.dto.DailyProductSales;
import org.example.takecareofmyfridgebe.domain.order.service.OrderSalesService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DealSalesInputService {

    private final OrderSalesService orderSalesService;

    public List<DailyProductSales> getSameWeekdaySales(
            LocalDate targetWeekStart, DayOfWeek dayOfWeek, LocalDateTime asOf) {
        return orderSalesService.getDailyProductSales(
                        targetWeekStart.minusWeeks(4), targetWeekStart.minusDays(1), asOf).stream()
                .filter(sale -> sale.salesDate().getDayOfWeek() == dayOfWeek)
                .toList();
    }
}
