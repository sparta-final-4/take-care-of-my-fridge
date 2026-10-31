package org.example.takecareofmyfridgebe.domain.admin.demo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealAnalysis;
import org.example.takecareofmyfridgebe.domain.deal.entity.DealCandidate;
import org.example.takecareofmyfridgebe.domain.deal.repository.DealAnalysisRepository;
import org.example.takecareofmyfridgebe.domain.deal.repository.DealCandidateRepository;
import org.example.takecareofmyfridgebe.domain.forecast.entity.PredictionRun;
import org.example.takecareofmyfridgebe.domain.forecast.entity.SalesForecast;
import org.example.takecareofmyfridgebe.domain.forecast.repository.PredictionRunRepository;
import org.example.takecareofmyfridgebe.domain.forecast.repository.SalesForecastRepository;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "APP_ENV", havingValue = "dev")
@RequiredArgsConstructor
@Slf4j
public class DevDemoResultSeeder implements ApplicationRunner {

    private static final String VERSION = "demo-preview-v1";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final PredictionRunRepository predictionRunRepository;
    private final SalesForecastRepository salesForecastRepository;
    private final DealAnalysisRepository dealAnalysisRepository;
    private final DealCandidateRepository dealCandidateRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Product> products = productRepository.findByStatusOrderByIdAsc(
                ProductStatus.ON_SALE, PageRequest.of(0, 12));
        if (products.size() < 3) {
            log.info("시연 결과 생성 건너뜀: 판매 중인 상품이 3개 미만입니다.");
            return;
        }
        seedForecast(products);
        memberRepository.findFirstByRoleOrderByIdAsc(Member.Role.ADMIN)
                .ifPresent(admin -> seedDeal(admin, products));
    }

    private void seedForecast(List<Product> products) {
        if (predictionRunRepository.findFirstByModelVersion(VERSION).isPresent()) {
            return;
        }
        LocalDate week = LocalDate.now(SEOUL)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(2);
        while (predictionRunRepository.findByTargetWeekStart(week).isPresent()) {
            week = week.minusWeeks(1);
        }
        PredictionRun run = predictionRunRepository.save(PredictionRun.builder()
                .targetWeekStart(week)
                .modelVersion(VERSION)
                .dataCutoffAt(week.minusDays(1).atTime(18, 0))
                .confirmedAt(week.plusWeeks(1).atTime(6, 0))
                .syntheticData(true)
                .build());
        List<SalesForecast> forecasts = new ArrayList<>();
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            forecasts.add(SalesForecast.builder()
                    .predictionRun(run)
                    .productId(product.getId())
                    .productName(product.getName())
                    .predictedRank(i < 10 ? i + 1 : null)
                    .predictedQty(i < 10 ? 34 - i * 2 : null)
                    .baselineQty(22 - i)
                    .actualRank(actualRank(i))
                    .actualQty(32 - i * 2)
                    .build());
        }
        salesForecastRepository.saveAll(forecasts);
    }

    // 실제 순위: 1~9위는 예측과 같고, 10번째 상품은 순위 밖, 11번째 상품이 10위다
    private Integer actualRank(int index) {
        if (index < 9) {
            return index + 1;
        }
        return index == 10 ? 10 : null;
    }

    private void seedDeal(Member admin, List<Product> products) {
        if (dealAnalysisRepository.findFirstByModelVersion(VERSION).isPresent()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(SEOUL);
        LocalDate targetWeek = now.toLocalDate()
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        DealAnalysis analysis = dealAnalysisRepository.save(DealAnalysis.builder()
                .member(admin)
                .targetWeekStart(targetWeek)
                .dayOfWeek(DealAnalysis.Day.FRI)
                .discountRate(20)
                .minStock(50)
                .modelVersion(VERSION)
                .dataCutoffAt(now)
                .syntheticData(true)
                .build());
        List<DealCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            boolean topSeller = i == 0;
            boolean lowStock = i == 1;
            boolean excluded = topSeller || lowStock;
            int regularQty = 6 + i;
            candidates.add(DealCandidate.builder()
                    .dealAnalysis(analysis)
                    .productId(product.getId())
                    .productName(product.getName())
                    .regularPrice(product.getPrice())
                    .discountPrice(product.getPrice() * 80 / 100)
                    .currentStock(lowStock ? 24 : Math.max(80, product.getStock()))
                    .avgSalesOnDay(topSeller ? 30 : regularQty)
                    .searchClicks(100 + i * 14)
                    .purchases(regularQty * 4)
                    .excluded(excluded)
                    .excludeReason(topSeller ? DealCandidate.ExcludeReason.TOP_SELLER_ON_DAY
                            : lowStock ? DealCandidate.ExcludeReason.LOW_STOCK : null)
                    .candidateReason(excluded ? null : "검색 관심 대비 판매가 낮아 할인 후보로 표시")
                    .expectedQtyRegular(excluded ? null : regularQty)
                    .expectedQtyDiscount(excluded ? null : regularQty + 4 + i / 3)
                    .expectedIncrease(excluded ? null : 4 + i / 3)
                    .build());
        }
        dealCandidateRepository.saveAll(candidates);
    }
}