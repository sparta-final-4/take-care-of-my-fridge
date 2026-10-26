package org.example.takecareofmyfridgebe.domain.forecast.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.forecast.dto.DailyNetSales;
import org.example.takecareofmyfridgebe.domain.forecast.entity.PredictionRun;
import org.example.takecareofmyfridgebe.domain.forecast.entity.SalesForecast;
import org.example.takecareofmyfridgebe.domain.forecast.repository.PredictionRunRepository;
import org.example.takecareofmyfridgebe.domain.forecast.repository.SalesForecastRepository;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ForecastActualService {

    private final PredictionRunRepository runRepository;
    private final SalesForecastRepository forecastRepository;
    private final NetSalesService netSalesService;
    private final ProductRepository productRepository;

    @Transactional
    public void confirmWeek(LocalDate weekStart, LocalDateTime asOf) {
        if (asOf.isBefore(weekStart.plusWeeks(1).atTime(6, 0))) {
            throw new IllegalArgumentException("실적 확정 시각 전입니다.");
        }
        PredictionRun run = runRepository.findByTargetWeekStartForUpdate(weekStart).orElse(null);
        if (run == null || run.getConfirmedAt() != null) {
            return;
        }
        Map<Long, Long> quantities = netSalesService.getDailySales(weekStart, weekStart.plusDays(6), asOf)
                .stream().collect(Collectors.groupingBy(DailyNetSales::productId,
                        Collectors.summingLong(DailyNetSales::quantity)));
        List<Long> soldIds = quantities.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey).toList();
        Map<Long, Integer> ranks = IntStream.range(0, Math.min(10, soldIds.size()))
                .boxed().collect(Collectors.toMap(soldIds::get, index -> index + 1));
        List<SalesForecast> existing = forecastRepository.findByPredictionRunIdOrderByProductIdAsc(run.getId());
        Map<Long, SalesForecast> byProduct = existing.stream()
                .collect(Collectors.toMap(SalesForecast::getProductId, Function.identity()));
        existing.forEach(item -> item.confirmActual(ranks.get(item.getProductId()),
                Math.toIntExact(quantities.getOrDefault(item.getProductId(), 0L))));

        List<Long> newIds = quantities.keySet().stream()
                .filter(id -> ranks.containsKey(id) && !byProduct.containsKey(id)).toList();
        Map<Long, String> names = productRepository.findAllById(newIds).stream()
                .collect(Collectors.toMap(Product::getId, Product::getName));
        List<SalesForecast> entrants = newIds.stream()
                .map(id -> SalesForecast.builder().predictionRun(run).productId(id)
                        .productName(names.get(id)).baselineQty(0)
                        .actualRank(ranks.get(id)).actualQty(Math.toIntExact(quantities.get(id)))
                        .build())
                .toList();
        if (entrants.stream().anyMatch(item -> item.getProductName() == null)) {
            throw new IllegalStateException("실제 판매 상품의 이름을 찾을 수 없습니다.");
        }
        forecastRepository.saveAll(entrants);
        run.confirm(asOf);
    }
}
