package org.example.takecareofmyfridgebe.domain.forecast.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.forecast.entity.PredictionRun;
import org.example.takecareofmyfridgebe.domain.forecast.entity.SalesForecast;
import org.example.takecareofmyfridgebe.domain.forecast.repository.PredictionRunRepository;
import org.example.takecareofmyfridgebe.domain.forecast.repository.SalesForecastRepository;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ForecastPredictionSaveService {

    private final PredictionRunRepository runRepository;
    private final SalesForecastRepository forecastRepository;

    @Transactional
    public void save(LocalDate weekStart, LocalDateTime cutoff,
                     ForecastWorkerClient.Result result, Map<Long, Product> products,
                     Map<Long, Long> baselines) {
        if (runRepository.findByTargetWeekStart(weekStart).isPresent()) {
            return;
        }
        PredictionRun run = runRepository.save(PredictionRun.builder()
                .targetWeekStart(weekStart).modelVersion(result.modelVersion())
                .dataCutoffAt(cutoff).syntheticData(false).build());
        List<ForecastWorkerClient.Prediction> ranked = result.predictions().stream()
                .sorted(Comparator.comparingInt(ForecastWorkerClient.Prediction::quantity).reversed()
                        .thenComparing(ForecastWorkerClient.Prediction::productId)).toList();
        List<SalesForecast> forecasts = IntStream.range(0, ranked.size())
                .mapToObj(index -> {
                    var prediction = ranked.get(index);
                    Product product = products.get(prediction.productId());
                    return SalesForecast.builder().predictionRun(run).productId(product.getId())
                            .productName(product.getName()).predictedRank(index < 10 ? index + 1 : null)
                            .predictedQty(prediction.quantity())
                            .baselineQty(Math.toIntExact(baselines.get(product.getId()))).build();
                }).toList();
        forecastRepository.saveAll(forecasts);
    }
}
