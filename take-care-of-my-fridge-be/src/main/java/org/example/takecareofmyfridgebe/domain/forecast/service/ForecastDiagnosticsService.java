package org.example.takecareofmyfridgebe.domain.forecast.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.forecast.dto.DailyNetSales;
import org.example.takecareofmyfridgebe.domain.forecast.entity.ForecastBatchRun;
import org.example.takecareofmyfridgebe.domain.forecast.entity.ForecastBatchStatus;
import org.example.takecareofmyfridgebe.domain.forecast.repository.ForecastBatchRunRepository;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ForecastDiagnosticsService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final int HISTORY_DAYS = 28;
    private static final int TRAINING_WEEKS = 8;

    private final ProductRepository productRepository;
    private final NetSalesService netSalesService;
    private final ForecastBatchRunRepository batchRunRepository;

    public LocalDate latestDueWeek() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDate monday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate nextMonday = monday.plusWeeks(1);
        return now.isBefore(nextMonday.minusDays(1).atTime(22, 0)) ? monday : nextMonday;
    }

    public String describe(LocalDate weekStart) {
        LocalDateTime cutoff = weekStart.minusDays(1).atTime(18, 0);
        Set<Long> productIds = productRepository.findAll().stream()
                .filter(product -> product.getStatus() != ProductStatus.STOPPED)
                .map(Product::getId).collect(Collectors.toSet());
        Map<Long, Map<LocalDate, Long>> history = new HashMap<>();
        for (DailyNetSales sale : netSalesService.getDailySales(
                weekStart.minusDays(HISTORY_DAYS + TRAINING_WEEKS * 7), weekStart.minusDays(1), cutoff)) {
            if (productIds.contains(sale.productId())) {
                history.computeIfAbsent(sale.productId(), ignored -> new HashMap<>())
                        .put(sale.salesDate(), sale.quantity());
            }
        }
        long netQuantity = history.values().stream().flatMap(map -> map.values().stream())
                .mapToLong(Long::longValue).sum();
        int sampleCount = 0;
        for (Map<LocalDate, Long> daily : history.values()) {
            for (int weeksAgo = 1; weeksAgo <= TRAINING_WEEKS; weeksAgo++) {
                LocalDate sampleWeek = weekStart.minusWeeks(weeksAgo);
                boolean hasHistory = hasSales(daily, sampleWeek.minusDays(HISTORY_DAYS), HISTORY_DAYS);
                boolean hasTarget = hasSales(daily, sampleWeek, 7);
                if (hasHistory || hasTarget) sampleCount++;
            }
        }
        return "실제 판매 예측이 없습니다. 대상 주: " + weekStart
                + "\n입력 마감: " + cutoff + " (구매 확정·완료 환불 기준)"
                + "\n예측 대상 상품: " + productIds.size() + "개 / 최소 1개 ("
                + requirement(!productIds.isEmpty()) + ")"
                + "\n최근 84일 순판매 수량: " + netQuantity + "개 ("
                + requirement(netQuantity > 0) + ")"
                + "\n학습 표본: " + sampleCount + "개 / 최소 1개 ("
                + requirement(sampleCount > 0) + ")"
                + "\n배치: " + batchStatus(weekStart);
    }

    private String requirement(boolean met) {
        return met ? "충족" : "부족";
    }

    private boolean hasSales(Map<LocalDate, Long> daily, LocalDate start, int days) {
        for (int day = 0; day < days; day++) {
            if (daily.getOrDefault(start.plusDays(day), 0L) > 0) return true;
        }
        return false;
    }

    private String batchStatus(LocalDate weekStart) {
        ForecastBatchRun run = batchRunRepository.findByTargetWeekStart(weekStart).orElse(null);
        if (run == null) {
            LocalDateTime scheduledAt = weekStart.minusDays(1).atTime(22, 0);
            LocalDateTime now = LocalDateTime.now(ZONE);
            String phase = now.isBefore(scheduledAt) ? "실행 예정"
                    : now.isBefore(weekStart.atTime(6, 0)) ? "실행 시간 중" : "실행 시간 지남";
            return "시도 기록 없음 (" + phase + ", 예정: " + scheduledAt + ")";
        }
        ForecastBatchStatus status = run.getStatus();
        String result = switch (status) {
            case CREATED -> "예측 생성 완료";
            case ALREADY_EXISTS -> "해당 주 결과가 이미 있음";
            case NO_PRODUCTS -> "예측 대상 상품 없음";
            case NO_TRAINING_SAMPLES -> "학습 표본 없음";
            case FAILED -> "실행 실패";
        };
        return run.getAttemptedAt() + " " + result
                + (status == ForecastBatchStatus.FAILED && run.getFailureType() != null
                ? " (" + run.getFailureType() + ")" : "");
    }
}
