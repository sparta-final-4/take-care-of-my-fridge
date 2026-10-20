package org.example.takecareofmyfridgebe.domain.forecast.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.example.takecareofmyfridgebe.domain.forecast.entity.PredictionRun;
import org.example.takecareofmyfridgebe.domain.forecast.entity.SalesForecast;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
class ForecastReadRepositoryTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @Autowired
    private PredictionRunRepository runRepository;
    @Autowired
    private SalesForecastRepository forecastRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void findsRequestedWeekAndExistingNeighborWeeks() {
        saveRun(LocalDate.of(2026, 10, 5));
        PredictionRun middle = saveRun(LocalDate.of(2026, 10, 19));
        saveRun(LocalDate.of(2026, 11, 2));
        entityManager.flush();
        entityManager.clear();

        assertThat(runRepository.findTopByOrderByTargetWeekStartDesc()).get()
                .extracting(PredictionRun::getTargetWeekStart).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(runRepository.findByTargetWeekStart(LocalDate.of(2026, 10, 19)))
                .get().extracting(PredictionRun::getId).isEqualTo(middle.getId());
        assertThat(runRepository.findTopByTargetWeekStartLessThanOrderByTargetWeekStartDesc(
                LocalDate.of(2026, 10, 19))).get()
                .extracting(PredictionRun::getTargetWeekStart).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(runRepository.findTopByTargetWeekStartGreaterThanOrderByTargetWeekStartAsc(
                LocalDate.of(2026, 10, 19))).get()
                .extracting(PredictionRun::getTargetWeekStart).isEqualTo(LocalDate.of(2026, 11, 2));
    }

    @Test
    void loadsOnlyItemsOfRequestedRun() {
        PredictionRun first = saveRun(LocalDate.of(2026, 10, 5));
        PredictionRun second = saveRun(LocalDate.of(2026, 10, 12));
        forecastRepository.save(SalesForecast.builder().predictionRun(first)
                .productId(2L).productName("두부").predictedRank(1)
                .predictedQty(20).baselineQty(18).build());
        forecastRepository.save(SalesForecast.builder().predictionRun(second)
                .productId(1L).productName("우유").predictedRank(1)
                .predictedQty(10).baselineQty(8).build());
        entityManager.flush();
        entityManager.clear();

        assertThat(forecastRepository.findByPredictionRunIdOrderByProductIdAsc(first.getId()))
                .extracting(SalesForecast::getProductId).containsExactly(2L);
    }

    private PredictionRun saveRun(LocalDate weekStart) {
        return runRepository.save(PredictionRun.builder()
                .targetWeekStart(weekStart).modelVersion("weekly-mlp-v1")
                .dataCutoffAt(LocalDateTime.of(2026, 10, 4, 18, 0))
                .syntheticData(true).build());
    }
}
