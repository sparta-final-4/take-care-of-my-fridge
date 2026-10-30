package org.example.takecareofmyfridgebe.domain.settlement.repository;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.settlement.entity.DailySales;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
class DailySalesRepositoryTest extends MySqlContainerSupport {

    private final DailySalesRepository dailySalesRepository;
    private final EntityManager entityManager;

    @Autowired
    DailySalesRepositoryTest(DailySalesRepository dailySalesRepository, EntityManager entityManager) {
        this.dailySalesRepository = dailySalesRepository;
        this.entityManager = entityManager;
    }

    @Test
    @DisplayName("시작일과 종료일을 포함한 기간 안의 집계된 날짜만 가져오고, 환불 건수 없이 집계된 예전 행은 뺀다")
    void findSalesDatesBetween_returnsDatesInRange() {
        save(LocalDate.of(2026, 9, 30));
        save(LocalDate.of(2026, 9, 28));
        save(LocalDate.of(2026, 10, 1));
        save(LocalDate.of(2026, 10, 3));
        save(LocalDate.of(2026, 10, 4));
        save(LocalDate.of(2026, 10, 5));
        entityManager.persist(DailySales.builder().salesDate(LocalDate.of(2026, 10, 2)).paidAmount(1000L)
                .refundedAmount(500L).paidCount(1).build());
        entityManager.persist(DailySales.builder().salesDate(LocalDate.of(2026, 9, 29)).paidAmount(1000L)
                .refundedAmount(500L).paidCount(1).refundedCount(1).build());
        entityManager.flush();
        entityManager.clear();

        assertThat(dailySalesRepository.findSalesDatesBetween(LocalDate.of(2026, 9, 29), LocalDate.of(2026, 10, 4)))
                .containsExactlyInAnyOrder(LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 30),
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 4));
    }

    private void save(LocalDate salesDate) {
        entityManager.persist(DailySales.builder().salesDate(salesDate).paidAmount(1000L).refundedAmount(0L)
                .paidCount(1).build());
    }
}
