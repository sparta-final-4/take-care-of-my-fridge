package org.example.takecareofmyfridgebe.domain.sale.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import javax.sql.DataSource;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.domain.sale.dto.FlashDropCreateRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.FlashDropItemRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventDetailResponse;
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

// 행사 수정 화면에 보여 줄 행사 하나와 행사 항목을 DB 값으로 확인한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, SaleEventCreateService.class,
        SaleEventDetailQueryService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SaleEventDetailQueryServiceTest extends MySqlContainerSupport {

    private static final LocalDateTime TOMORROW =
            LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(1).withNano(0);

    private final SaleEventCreateService saleEventCreateService;
    private final SaleEventDetailQueryService saleEventDetailQueryService;
    private final SaleEventRepository saleEventRepository;
    private final SaleEventItemRepository saleEventItemRepository;
    private final ProductRepository productRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    SaleEventDetailQueryServiceTest(
            SaleEventCreateService saleEventCreateService,
            SaleEventDetailQueryService saleEventDetailQueryService,
            SaleEventRepository saleEventRepository,
            SaleEventItemRepository saleEventItemRepository,
            ProductRepository productRepository,
            DataSource dataSource) {
        this.saleEventCreateService = saleEventCreateService;
        this.saleEventDetailQueryService = saleEventDetailQueryService;
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
    @DisplayName("특가의 시각과 행사 항목을 먼저 담은 순서로 주고, 예정 행사의 정가는 상품의 지금 정가다")
    void getSaleEvent_special_returnsItemsWithCurrentListPrice() {
        Product greenOnion = saveProduct("대파", 3000);
        Product tofu = saveProduct("두부", 2000);
        // 한 행사에 상품이 둘인 특가(예전에 묶어 등록한 행사)
        SaleEvent event = saleEventRepository.save(SaleEvent.builder().type(SaleEventType.SPECIAL)
                .startsAt(TOMORROW).endsAt(TOMORROW.plusDays(7)).build());
        saleEventItemRepository.save(SaleEventItem.builder().saleEvent(event).productId(greenOnion.getId())
                .listPrice(3000).discountRate(20).build());
        saleEventItemRepository.save(SaleEventItem.builder().saleEvent(event).productId(tofu.getId())
                .listPrice(2000).discountRate(10).build());
        Long eventId = event.getId();
        jdbcTemplate.update("update products set price = 4000 where id = ?", greenOnion.getId());

        SaleEventDetailResponse response = saleEventDetailQueryService.getSaleEvent(eventId);

        assertThat(response.type()).isEqualTo(SaleEventType.SPECIAL);
        assertThat(response.status()).isEqualTo(SaleEventStatus.SCHEDULED);
        assertThat(response.startsAt()).isEqualTo(TOMORROW);
        assertThat(response.endsAt()).isEqualTo(TOMORROW.plusDays(7));
        assertThat(response.items())
                .extracting(SaleEventDetailResponse.SaleEventDetailItem::productName, SaleEventDetailResponse.SaleEventDetailItem::listPrice,
                        SaleEventDetailResponse.SaleEventDetailItem::salePrice, SaleEventDetailResponse.SaleEventDetailItem::discountRate,
                        SaleEventDetailResponse.SaleEventDetailItem::limitedQuantity)
                .containsExactly(tuple("대파", 4000, 2400, 20, null), tuple("두부", 2000, 1800, 10, null));
    }

    @Test
    @DisplayName("종료된 번개 드롭의 정가는 등록 당시 정가이고 한정·남은 수량을 함께 준다")
    void getSaleEvent_endedFlashDrop_returnsStoredListPrice() {
        Product tofu = saveProduct("두부", 3000);
        Long eventId = saleEventCreateService.createFlashDrop(
                new FlashDropCreateRequest(TOMORROW, TOMORROW.plusHours(2),
                        List.of(new FlashDropItemRequest(tofu.getId(), 50, 10)))).saleEventIds().get(0);
        jdbcTemplate.update("update sale_events set status = ? where id = ?", SaleEventStatus.ENDED.name(), eventId);
        jdbcTemplate.update("update products set price = 4000 where id = ?", tofu.getId());

        SaleEventDetailResponse response = saleEventDetailQueryService.getSaleEvent(eventId);

        assertThat(response.items())
                .extracting(SaleEventDetailResponse.SaleEventDetailItem::listPrice, SaleEventDetailResponse.SaleEventDetailItem::limitedQuantity,
                        SaleEventDetailResponse.SaleEventDetailItem::remainingQuantity)
                .containsExactly(tuple(3000, 10, 10));
    }

    @Test
    @DisplayName("없는 행사는 404로 거절한다")
    void getSaleEvent_missing_rejects() {
        assertThatThrownBy(() -> saleEventDetailQueryService.getSaleEvent(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SALE_EVENT_NOT_FOUND);
    }

    private Product saveProduct(String name, int price) {
        return productRepository.save(Product.builder()
                .productType(ProductType.INGREDIENT)
                .name(name)
                .imageUrl("https://img.test/" + name + ".png")
                .price(price)
                .stock(10)
                .storage(StorageType.REFRIGERATED)
                .shelfDays(7)
                .status(ProductStatus.ON_SALE)
                .build());
    }
}
