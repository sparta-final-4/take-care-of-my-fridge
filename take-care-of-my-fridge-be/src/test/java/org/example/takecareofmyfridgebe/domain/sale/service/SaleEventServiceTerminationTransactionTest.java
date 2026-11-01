package org.example.takecareofmyfridgebe.domain.sale.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import javax.sql.DataSource;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleCandidateProductResponse;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleCandidateProductsRequest;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventItem;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventStatus;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventItemRepository;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventRepository;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 테스트 자체 트랜잭션을 끄고(NOT_SUPPORTED) 서비스의 트랜잭션 경계로 DB에 반영되는지 확인한다.
// 응답이 아니라 커밋된 할인 행사와 행사 항목을 다시 읽어 확인한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, SaleEventTerminationService.class, SaleCandidateProductQueryService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SaleEventTerminationServiceTransactionTest extends MySqlContainerSupport {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final SaleEventTerminationService saleEventTerminationService;
    private final SaleCandidateProductQueryService saleCandidateProductQueryService;
    private final SaleEventRepository saleEventRepository;
    private final SaleEventItemRepository saleEventItemRepository;
    private final ProductRepository productRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    SaleEventTerminationServiceTransactionTest(
            SaleEventTerminationService saleEventTerminationService,
            SaleCandidateProductQueryService saleCandidateProductQueryService,
            SaleEventRepository saleEventRepository,
            SaleEventItemRepository saleEventItemRepository,
            ProductRepository productRepository,
            DataSource dataSource) {
        this.saleEventTerminationService = saleEventTerminationService;
        this.saleCandidateProductQueryService = saleCandidateProductQueryService;
        this.saleEventRepository = saleEventRepository;
        this.saleEventItemRepository = saleEventItemRepository;
        this.productRepository = productRepository;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void cleanUp() {
        saleEventItemRepository.deleteAll();
        saleEventRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("진행 중인 할인 행사를 종료하면 상태가 종료로 바뀌고 닫은 시각이 남으며, 종료 시각과 행사 항목은 그대로다")
    void end_active_keepsEndsAt() {
        Product tofu = saveProduct("두부");
        SaleEvent event = saveEvent(SaleEventStatus.ACTIVE, minutesFromNow(-60), minutesFromNow(60), tofu);

        LocalDateTime before = LocalDateTime.now(ZONE).withNano(0);
        saleEventTerminationService.end(event.getId());

        SaleEvent saved = saleEventRepository.findById(event.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(SaleEventStatus.ENDED);
        assertThat(saved.getEndsAt()).isEqualTo(event.getEndsAt());
        assertThat(saved.getEndedAt()).isAfterOrEqualTo(before).isBefore(saved.getEndsAt());
        assertThat(saved.actualEndsAt()).isEqualTo(saved.getEndedAt());
        assertThat(saleEventItemRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("종료하면 진행 중 할인 조회에서 빠지고, 그 상품이 후보 상품에 다시 나온다")
    void end_removesActiveSaleAndFreesProduct() {
        Product tofu = saveProduct("두부");
        SaleEvent event = saveEvent(SaleEventStatus.ACTIVE, minutesFromNow(-60), minutesFromNow(60), tofu);
        List<Long> productIds = List.of(tofu.getId());
        LocalDateTime now = LocalDateTime.now(ZONE);
        assertThat(saleEventItemRepository.findActiveByProductIds(productIds, SaleEventStatus.OPEN, now)).hasSize(1);
        assertThat(candidateProductIds()).isEmpty();

        saleEventTerminationService.end(event.getId());

        assertThat(saleEventItemRepository.findActiveByProductIds(productIds, SaleEventStatus.OPEN, now)).isEmpty();
        assertThat(candidateProductIds()).containsExactly(tofu.getId());
    }

    @Test
    @DisplayName("상태 정리가 늦어 예정으로 남았어도 시각상 진행 중이면 종료할 수 있다")
    void end_scheduledButStarted_ends() {
        Product tofu = saveProduct("두부");
        SaleEvent event = saveEvent(SaleEventStatus.SCHEDULED, minutesFromNow(-1), minutesFromNow(60), tofu);

        saleEventTerminationService.end(event.getId());

        assertThat(saleEventRepository.findById(event.getId()).orElseThrow().getStatus())
                .isEqualTo(SaleEventStatus.ENDED);
    }

    @Test
    @DisplayName("예정·종료·종료 시각이 지난 할인 행사는 종료를 거절하고 상태는 그대로다")
    void end_notActive_rejects() {
        Product greenOnion = saveProduct("대파");
        Product tofu = saveProduct("두부");
        Product onion = saveProduct("양파");
        SaleEvent scheduled = saveEvent(SaleEventStatus.SCHEDULED, minutesFromNow(60), minutesFromNow(120), greenOnion);
        SaleEvent ended = saveEvent(SaleEventStatus.ENDED, minutesFromNow(-120), minutesFromNow(-60), tofu);
        // 상태 정리가 늦어 아직 진행 중으로 남았지만 종료 시각은 지났다
        SaleEvent overdue = saveEvent(SaleEventStatus.ACTIVE, minutesFromNow(-120), minutesFromNow(-1), onion);

        for (SaleEvent event : new SaleEvent[] {scheduled, ended, overdue}) {
            assertThatThrownBy(() -> saleEventTerminationService.end(event.getId()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.SALE_EVENT_NOT_ENDABLE);
        }
        assertThat(saleEventRepository.findById(scheduled.getId()).orElseThrow().getStatus())
                .isEqualTo(SaleEventStatus.SCHEDULED);
        assertThat(saleEventRepository.findById(overdue.getId()).orElseThrow().getStatus())
                .isEqualTo(SaleEventStatus.ACTIVE);
    }

    @Test
    @DisplayName("없는 할인 행사는 종료·삭제 모두 404로 거절한다")
    void missing_rejects() {
        assertThatThrownBy(() -> saleEventTerminationService.end(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SALE_EVENT_NOT_FOUND);
        assertThatThrownBy(() -> saleEventTerminationService.delete(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SALE_EVENT_NOT_FOUND);
    }

    @Test
    @DisplayName("시작 전인 할인 행사를 삭제하면 할인 행사와 행사 항목이 모두 지워지고 다른 할인 행사는 남는다")
    void delete_scheduled_removesEventAndItems() {
        Product greenOnion = saveProduct("대파");
        Product tofu = saveProduct("두부");
        Product onion = saveProduct("양파");
        SaleEvent target = saveEvent(SaleEventStatus.SCHEDULED, minutesFromNow(60), minutesFromNow(120), greenOnion, tofu);
        SaleEvent other = saveEvent(SaleEventStatus.SCHEDULED, minutesFromNow(60), minutesFromNow(120), onion);

        saleEventTerminationService.delete(target.getId());

        assertThat(saleEventRepository.findAll()).extracting(SaleEvent::getId).containsExactly(other.getId());
        assertThat(saleEventItemRepository.findAll()).extracting(SaleEventItem::getProductId)
                .containsExactly(onion.getId());
    }

    @Test
    @DisplayName("진행 중·종료 할인 행사와 시작 시각이 지난 예정 할인 행사는 삭제를 거절하고 모두 그대로다")
    void delete_startedEvent_rejects() {
        Product greenOnion = saveProduct("대파");
        Product tofu = saveProduct("두부");
        Product onion = saveProduct("양파");
        SaleEvent active = saveEvent(SaleEventStatus.ACTIVE, minutesFromNow(-60), minutesFromNow(60), greenOnion);
        SaleEvent ended = saveEvent(SaleEventStatus.ENDED, minutesFromNow(-120), minutesFromNow(-60), tofu);
        // 상태 정리가 늦어 아직 예정으로 남았지만 시작 시각은 지났다
        SaleEvent overdue = saveEvent(SaleEventStatus.SCHEDULED, minutesFromNow(-1), minutesFromNow(60), onion);

        for (SaleEvent event : new SaleEvent[] {active, ended, overdue}) {
            assertThatThrownBy(() -> saleEventTerminationService.delete(event.getId()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.SALE_EVENT_NOT_DELETABLE);
        }
        assertThat(saleEventRepository.count()).isEqualTo(3);
        assertThat(saleEventItemRepository.count()).isEqualTo(3);
    }

    private List<Long> candidateProductIds() {
        return saleCandidateProductQueryService.getCandidates(new SaleCandidateProductsRequest(null, null, null, null))
                .content().stream().map(SaleCandidateProductResponse::productId).toList();
    }

    private LocalDateTime minutesFromNow(long minutes) {
        return LocalDateTime.now(ZONE).plusMinutes(minutes).withNano(0);
    }

    private SaleEvent saveEvent(SaleEventStatus status, LocalDateTime startsAt, LocalDateTime endsAt,
                                Product... products) {
        SaleEvent event = saleEventRepository.save(SaleEvent.builder().type(SaleEventType.SPECIAL)
                .startsAt(startsAt).endsAt(endsAt).build());
        jdbcTemplate.update("update sale_events set status = ? where id = ?", status.name(), event.getId());
        for (Product product : products) {
            saleEventItemRepository.save(SaleEventItem.builder().saleEvent(event).productId(product.getId())
                    .listPrice(product.getPrice()).discountRate(20).build());
        }
        return event;
    }

    private Product saveProduct(String name) {
        return productRepository.save(Product.builder()
                .productType(ProductType.INGREDIENT)
                .name(name)
                .imageUrl("https://img.test/" + name + ".png")
                .price(3000)
                .stock(10)
                .storage(StorageType.REFRIGERATED)
                .shelfDays(7)
                .status(ProductStatus.ON_SALE)
                .build());
    }
}
