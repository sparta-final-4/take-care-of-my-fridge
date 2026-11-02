package org.example.takecareofmyfridgebe.domain.forecast.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.forecast.dto.DailyNetSales;
import org.example.takecareofmyfridgebe.domain.forecast.entity.ForecastBatchStatus;
import org.example.takecareofmyfridgebe.domain.forecast.repository.PredictionRunRepository;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ForecastPredictionService {

    // 워커 모델의 입력 기간(forecast_model/model.py WINDOW_DAYS)과 같아야 한다.
    private static final int HISTORY_DAYS = 28;
    private static final int FORECAST_DAYS = 7;
    // prediction_runs.model_version 열 길이
    private static final int MODEL_VERSION_MAX_LENGTH = 50;
    private static final int TRAINING_WEEKS = 8;

    private final PredictionRunRepository runRepository;
    private final ProductRepository productRepository;
    private final NetSalesService netSalesService;
    private final ForecastWorkerClient workerClient;
    private final ForecastPredictionSaveService saveService;

    public ForecastBatchStatus generate(LocalDate weekStart, LocalDateTime dataCutoffAt) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY
                || !dataCutoffAt.equals(weekStart.minusDays(1).atTime(18, 0))) {
            throw new IllegalArgumentException("대상 주와 예측 입력 마감 시각이 올바르지 않습니다.");
        }
        if (runRepository.findByTargetWeekStart(weekStart).isPresent()) {
            return ForecastBatchStatus.ALREADY_EXISTS;
        }
        List<Product> products = productRepository.findAll().stream()
                .filter(product -> product.getStatus() != ProductStatus.STOPPED)
                .sorted(Comparator.comparing(Product::getId)).toList();
        if (products.isEmpty()) {
            return ForecastBatchStatus.NO_PRODUCTS;
        }
        LocalDate from = weekStart.minusDays(HISTORY_DAYS + TRAINING_WEEKS * 7);
        Map<Long, Map<LocalDate, Long>> history = new HashMap<>();
        for (DailyNetSales sale : netSalesService.getDailySales(from, weekStart.minusDays(1), dataCutoffAt)) {
            history.computeIfAbsent(sale.productId(), ignored -> new HashMap<>())
                    .put(sale.salesDate(), sale.quantity());
        }
        List<ForecastWorkerClient.Sample> samples = new ArrayList<>();
        List<ForecastWorkerClient.ProductHistory> inputs = new ArrayList<>();
        for (Product product : products) {
            Map<LocalDate, Long> daily = history.getOrDefault(product.getId(), Map.of());
            inputs.add(new ForecastWorkerClient.ProductHistory(product.getId(),
                    window(daily, weekStart.minusDays(HISTORY_DAYS), HISTORY_DAYS)));
            for (int weeksAgo = 1; weeksAgo <= TRAINING_WEEKS; weeksAgo++) {
                LocalDate sampleWeek = weekStart.minusWeeks(weeksAgo);
                List<Long> values = window(daily, sampleWeek.minusDays(HISTORY_DAYS), HISTORY_DAYS);
                long target = window(daily, sampleWeek, FORECAST_DAYS).stream().mapToLong(Long::longValue).sum();
                if (target > 0 || values.stream().anyMatch(value -> value > 0)) {
                    samples.add(new ForecastWorkerClient.Sample(values, target));
                }
            }
        }
        if (samples.isEmpty()) {
            return ForecastBatchStatus.NO_TRAINING_SAMPLES;
        }
        ForecastWorkerClient.Result result = workerClient.predict(weekStart, samples, inputs);
        // 모델 버전은 워커가 정한다. 워커만 버전을 올려도 실행이 막히지 않도록 값의 형식만 확인한다.
        if (result == null || result.modelVersion() == null || result.modelVersion().isBlank()
                || result.modelVersion().length() > MODEL_VERSION_MAX_LENGTH
                || result.predictions() == null || result.predictions().size() != products.size()
                || result.predictions().stream().anyMatch(item -> item.productId() == null || item.quantity() < 0)
                || result.predictions().stream().map(ForecastWorkerClient.Prediction::productId)
                        .collect(Collectors.toSet()).size() != products.size()) {
            throw new IllegalStateException("예측 작업의 응답이 올바르지 않습니다.");
        }
        Map<Long, Product> byId = products.stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        if (result.predictions().stream().anyMatch(item -> !byId.containsKey(item.productId()))) {
            throw new IllegalStateException("알 수 없는 상품의 예측 결과입니다.");
        }
        Map<Long, Long> baselines = new HashMap<>();
        for (Product product : products) {
            baselines.put(product.getId(), window(history.getOrDefault(product.getId(), Map.of()),
                    weekStart.minusDays(FORECAST_DAYS), FORECAST_DAYS).stream().mapToLong(Long::longValue).sum());
        }
        saveService.save(weekStart, dataCutoffAt, result, byId, baselines);
        return ForecastBatchStatus.CREATED;
    }

    private List<Long> window(Map<LocalDate, Long> daily, LocalDate start, int days) {
        return IntStream.range(0, days)
                .mapToObj(index -> daily.getOrDefault(start.plusDays(index), 0L)).toList();
    }
}
