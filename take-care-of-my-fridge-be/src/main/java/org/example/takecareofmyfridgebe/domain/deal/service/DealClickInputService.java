package org.example.takecareofmyfridgebe.domain.deal.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.search.dto.ProductClickCount;
import org.example.takecareofmyfridgebe.domain.search.service.SearchClickMetricService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DealClickInputService {

    private final SearchClickMetricService clickMetricService;

    public Map<Long, Long> getSameWeekdayClicks(
            LocalDate targetWeekStart, DayOfWeek dayOfWeek, LocalDateTime asOf) {
        Map<Long, Long> clicks = new HashMap<>();
        for (int weeksAgo = 1; weeksAgo <= 4; weeksAgo++) {
            LocalDate date = targetWeekStart.minusWeeks(weeksAgo).with(dayOfWeek);
            for (ProductClickCount count : clickMetricService.getProductClicks(date, date, asOf)) {
                clicks.merge(count.productId(), count.clicks(), Long::sum);
            }
        }
        return Map.copyOf(clicks);
    }
}
