package org.example.takecareofmyfridgebe.domain.sale.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
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

// 등록할 수 있는 상품 목록(판매 중, 재고 있음, 예정·진행 중인 행사에 들지 않음)을 DB 값으로 확인한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, SaleCandidateProductQueryService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SaleCandidateProductQueryServiceTest extends MySqlContainerSupport {

    private static final LocalDateTime NOW = LocalDateTime.now(SaleEvent.ZONE);

    private final SaleCandidateProductQueryService saleCandidateProductQueryService;
    private final SaleEventRepository saleEventRepository;
    private final SaleEventItemRepository saleEventItemRepository;
    private final ProductRepository productRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    SaleCandidateProductQueryServiceTest(
            SaleCandidateProductQueryService saleCandidateProductQueryService,
            SaleEventRepository saleEventRepository,
            SaleEventItemRepository saleEventItemRepository,
            ProductRepository productRepository,
            DataSource dataSource) {
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
    @DisplayName("종료 시각이 지난 행사는 상태 정리가 늦어도 등록할 상품 목록에서 상품을 막지 않는다")
    void getCandidates_overdueEventDoesNotBlockProduct() {
        Product overdue = saveProduct("대파", 3000, 5);
        saveEvent(SaleEventType.SPECIAL, SaleEventStatus.ACTIVE, NOW.minusHours(2), NOW.minusMinutes(1), overdue, null);

        List<SaleCandidateProductResponse> candidates = saleCandidateProductQueryService
                .getCandidates(new SaleCandidateProductsRequest(null, null, null, null)).content();

        assertThat(candidates).extracting(SaleCandidateProductResponse::productId).containsExactly(overdue.getId());
    }

    @Test
    @DisplayName("등록할 상품 목록에는 판매 중이고 재고가 있으며 예정·진행 중인 행사에 들지 않은 상품만 나온다")
    void getCandidates_excludesProductsInOpenEventsAndUnsellable() {
        Product scheduled = saveProduct("대파", 3000, 7);
        Product active = saveProduct("두부", 3000, 5);
        Product ended = saveProduct("양파", 3000, 3);
        Product plain = saveProduct("무", 3000, 4);
        Product soldOut = saveProduct("우유", 3000, 0);
        Product stopped = saveProduct("감자", 3000, 6);
        jdbcTemplate.update("update products set status = 'STOPPED' where id = ?", stopped.getId());
        saveEvent(SaleEventType.SPECIAL, SaleEventStatus.SCHEDULED, NOW.plusHours(1), NOW.plusHours(2), scheduled, null);
        saveEvent(SaleEventType.SPECIAL, SaleEventStatus.ACTIVE, NOW.minusHours(1), NOW.plusHours(1), active, null);
        saveEvent(SaleEventType.SPECIAL, SaleEventStatus.ENDED, NOW.minusHours(2), NOW.minusHours(1), ended, null);

        List<SaleCandidateProductResponse> candidates = saleCandidateProductQueryService
                .getCandidates(new SaleCandidateProductsRequest(null, null, null, null)).content();

        assertThat(candidates)
                .extracting(SaleCandidateProductResponse::productId, SaleCandidateProductResponse::stock)
                .containsExactly(tuple(plain.getId(), 4), tuple(ended.getId(), 3));
        assertThat(soldOut.getStock()).isZero();
    }

    @Test
    @DisplayName("등록할 상품 목록은 이름·종류로 거르고 페이지로 나뉜다")
    void getCandidates_filtersAndPaginates() {
        saveProduct("국산 대파", 3000, 5);
        saveProduct("쪽파", 3000, 5);
        saveProduct("두부", 3000, 5);

        var byName = saleCandidateProductQueryService
                .getCandidates(new SaleCandidateProductsRequest("파", null, null, null));
        var firstPage = saleCandidateProductQueryService
                .getCandidates(new SaleCandidateProductsRequest(null, null, 1, 2));
        var lastPage = saleCandidateProductQueryService
                .getCandidates(new SaleCandidateProductsRequest(null, null, 2, 2));
        var mealkit = saleCandidateProductQueryService
                .getCandidates(new SaleCandidateProductsRequest(null, ProductType.MEALKIT, null, null));

        assertThat(byName.content()).extracting(SaleCandidateProductResponse::name).containsExactly("쪽파", "국산 대파");
        assertThat(firstPage.content()).hasSize(2);
        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.isLast()).isFalse();
        assertThat(lastPage.content()).hasSize(1);
        assertThat(lastPage.isLast()).isTrue();
        assertThat(mealkit.content()).isEmpty();
    }

    // 행사는 항상 예정으로 만들어지므로 원하는 상태는 DB 값을 직접 바꿔 만든다
    private SaleEvent saveEvent(SaleEventType type, SaleEventStatus status, LocalDateTime startsAt, LocalDateTime endsAt,
                                Product product, Integer limitedQuantity) {
        SaleEvent event = saleEventRepository.save(SaleEvent.builder().type(type).startsAt(startsAt).endsAt(endsAt).build());
        jdbcTemplate.update("update sale_events set status = ? where id = ?", status.name(), event.getId());
        saleEventItemRepository.save(SaleEventItem.builder()
                .saleEvent(event).productId(product.getId()).listPrice(product.getPrice())
                .discountRate(type == SaleEventType.FLASH_DROP ? 50 : 20).limitedQuantity(limitedQuantity).build());
        return event;
    }

    private Product saveProduct(String name, int price, int stock) {
        return productRepository.save(Product.builder()
                .productType(ProductType.INGREDIENT)
                .name(name)
                .price(price)
                .stock(stock)
                .storage(StorageType.REFRIGERATED)
                .shelfDays(7)
                .build());
    }
}
