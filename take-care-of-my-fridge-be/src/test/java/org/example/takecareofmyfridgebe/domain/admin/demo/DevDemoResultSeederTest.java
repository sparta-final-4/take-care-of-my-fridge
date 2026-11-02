package org.example.takecareofmyfridgebe.domain.admin.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.takecareofmyfridgebe.domain.deal.repository.DealAnalysisRepository;
import org.example.takecareofmyfridgebe.domain.deal.repository.DealCandidateRepository;
import org.example.takecareofmyfridgebe.domain.forecast.entity.PredictionRun;
import org.example.takecareofmyfridgebe.domain.forecast.entity.SalesForecast;
import org.example.takecareofmyfridgebe.domain.forecast.repository.PredictionRunRepository;
import org.example.takecareofmyfridgebe.domain.forecast.repository.SalesForecastRepository;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, DevDemoResultSeeder.class})
@TestPropertySource(properties = "APP_ENV=dev")
class DevDemoResultSeederTest extends MySqlContainerSupport {

    @Autowired private DevDemoResultSeeder seeder;
    @Autowired private ProductRepository products;
    @Autowired private MemberRepository members;
    @Autowired private PredictionRunRepository runs;
    @Autowired private SalesForecastRepository forecasts;
    @Autowired private DealAnalysisRepository analyses;
    @Autowired private DealCandidateRepository candidates;

    @Test
    void createsMarkedSnapshotsOnceWithoutChangingRealRuns() {
        members.save(Member.builder().email("demo-admin@example.com").passwordHash("hash")
                .nickname("demo-admin").role(Member.Role.ADMIN).build());
        for (int i = 0; i < 4; i++) {
            products.save(Product.builder().productType(ProductType.INGREDIENT)
                    .name("시연 상품 " + i).price(1000 + i * 500).stock(80)
                    .storage(StorageType.ROOM_TEMP).shelfDays(7)
                    .status(ProductStatus.ON_SALE).build());
        }
        PredictionRun real = runs.save(PredictionRun.builder()
                // 시더와 같은 서울 기준 주차. JVM 기본 시간대(CI는 UTC)를 쓰면 KST 월요일 00~09시에 주차가 어긋난다
                .targetWeekStart(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).with(
                        java.time.temporal.TemporalAdjusters.previousOrSame(
                                java.time.DayOfWeek.MONDAY)).minusWeeks(2))
                .modelVersion("real-model").dataCutoffAt(java.time.LocalDateTime.now())
                .syntheticData(false).build());
        ApplicationArguments args = new DefaultApplicationArguments(new String[0]);

        seeder.run(args);
        seeder.run(args);

        assertThat(runs.count()).isEqualTo(2);
        assertThat(runs.findById(real.getId()).orElseThrow().isSyntheticData()).isFalse();
        PredictionRun demo = runs.findFirstByModelVersion("demo-preview-v1").orElseThrow();
        assertThat(demo.isSyntheticData()).isTrue();
        assertThat(runs.findTopBySyntheticDataTrueOrderByTargetWeekStartDesc())
                .contains(demo);
        assertThat(demo.getTargetWeekStart()).isBefore(real.getTargetWeekStart());
        assertThat(forecasts.findByPredictionRunIdOrderByProductIdAsc(demo.getId())).hasSize(4);
        var analysis = analyses.findFirstByModelVersion("demo-preview-v1").orElseThrow();
        assertThat(analysis.isSyntheticData()).isTrue();
        assertThat(analyses.findTopBySyntheticDataTrueOrderByCreatedAtDescIdDesc())
                .contains(analysis);
        assertThat(candidates.findByDealAnalysisIdOrderByIdAsc(analysis.getId())).hasSize(4);
    }

    // 판매 중인 상품이 10개를 넘으면 실제 순위가 없는 상품(10번째, 12번째)이 생긴다
    @Test
    void leavesActualRankEmptyWhenMoreThanTenProducts() {
        for (int i = 0; i < 12; i++) {
            products.save(Product.builder().productType(ProductType.INGREDIENT)
                    .name("시연 상품 " + i).price(1000 + i * 500).stock(80)
                    .storage(StorageType.ROOM_TEMP).shelfDays(7)
                    .status(ProductStatus.ON_SALE).build());
        }

        seeder.run(new DefaultApplicationArguments(new String[0]));

        PredictionRun demo = runs.findFirstByModelVersion("demo-preview-v1").orElseThrow();
        List<Integer> actualRanks = forecasts.findByPredictionRunIdOrderByProductIdAsc(demo.getId()).stream()
                .map(SalesForecast::getActualRank)
                .toList();
        assertThat(actualRanks).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, null, 10, null);
    }
}
