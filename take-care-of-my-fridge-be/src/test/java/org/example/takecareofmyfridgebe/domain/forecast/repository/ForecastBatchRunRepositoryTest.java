package org.example.takecareofmyfridgebe.domain.forecast.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.example.takecareofmyfridgebe.domain.forecast.entity.ForecastBatchRun;
import org.example.takecareofmyfridgebe.domain.forecast.entity.ForecastBatchStatus;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
class ForecastBatchRunRepositoryTest extends MySqlContainerSupport {

    @Autowired
    private ForecastBatchRunRepository repository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void storesAndReadsLastAttemptByTargetWeek() {
        LocalDate week = LocalDate.of(2026, 11, 2);
        repository.save(new ForecastBatchRun(week, ForecastBatchStatus.NO_TRAINING_SAMPLES,
                LocalDateTime.of(2026, 11, 1, 22, 0), null));
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findByTargetWeekStart(week)).get()
                .extracting(ForecastBatchRun::getStatus)
                .isEqualTo(ForecastBatchStatus.NO_TRAINING_SAMPLES);
    }
}
