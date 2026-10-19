package org.example.takecareofmyfridgebe.domain.forecast.repository;

import java.util.List;
import org.example.takecareofmyfridgebe.domain.forecast.entity.SalesForecast;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesForecastRepository extends JpaRepository<SalesForecast, Long> {

    List<SalesForecast> findByPredictionRunIdOrderByProductIdAsc(Long predictionRunId);
}
