package org.example.takecareofmyfridgebe.domain.sale.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.domain.sale.dto.FlashDropCreateRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.FlashDropItemRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventItemUpdateRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventUpdateRequest;
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
// 응답이 아니라 커밋된 행사와 행사 상품을 다시 읽어 확인한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, SaleEventCreateService.class, SaleEventUpdateService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SaleEventUpdateServiceTransactionTest extends MySqlContainerSupport {

    private static final LocalDateTime TOMORROW =
            LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(1).withNano(0);

    private final SaleEventCreateService saleEventCreateService;
    private final SaleEventUpdateService saleEventUpdateService;
    private final SaleEventRepository saleEventRepository;
    private final SaleEventItemRepository saleEventItemRepository;
    private final ProductRepository productRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    SaleEventUpdateServiceTransactionTest(
            SaleEventCreateService saleEventCreateService,
            SaleEventUpdateService saleEventUpdateService,
            SaleEventRepository saleEventRepository,
            SaleEventItemRepository saleEventItemRepository,
            ProductRepository productRepository,
            DataSource dataSource) {
        this.saleEventCreateService = saleEventCreateService;
        this.saleEventUpdateService = saleEventUpdateService;
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
    @DisplayName("특가의 시각과 두 상품의 할인율을 한 번에 고치면 모두 저장되고, 정가는 상품의 지금 정가로 바뀐다")
    void update_special_savesTimeAndAllItems() {
        Product greenOnion = saveProduct("대파", 3000);
        Product tofu = saveProduct("두부", 2000);
        Long eventId = saveSpecialEvent(greenOnion, tofu);
        jdbcTemplate.update("update products set price = 4000 where id = ?", greenOnion.getId());

        saleEventUpdateService.update(eventId, new SaleEventUpdateRequest(
                TOMORROW.plusDays(2), TOMORROW.plusDays(9),
                List.of(item(greenOnion, 30, null), item(tofu, 10, null))));

        SaleEvent saved = saleEventRepository.findById(eventId).orElseThrow();
        assertThat(saved.getStartsAt()).isEqualTo(TOMORROW.plusDays(2));
        assertThat(saved.getEndsAt()).isEqualTo(TOMORROW.plusDays(9));
        assertThat(saved.getStatus()).isEqualTo(SaleEventStatus.SCHEDULED);
        assertThat(saleEventItemRepository.findAll())
                .extracting(SaleEventItem::getProductId, SaleEventItem::getListPrice, SaleEventItem::getDiscountRate,
                        SaleEventItem::getSalePrice)
                .containsExactlyInAnyOrder(
                        tuple(greenOnion.getId(), 4000, 30, 2800),
                        tuple(tofu.getId(), 2000, 10, 1800));
    }

    @Test
    @DisplayName("번개 드롭의 시각·할인율·한정 수량을 고치면 남은 수량도 한정 수량과 같게 맞춰진다")
    void update_flashDrop_resetsRemaining() {
        Product tofu = saveProduct("두부", 3000);
        Long eventId = saleEventCreateService.createFlashDrop(flashDrop(tofu)).saleEventIds().get(0);

        saleEventUpdateService.update(eventId, new SaleEventUpdateRequest(
                TOMORROW.plusHours(5), TOMORROW.plusHours(7), List.of(item(tofu, 40, 4))));

        SaleEvent saved = saleEventRepository.findById(eventId).orElseThrow();
        assertThat(saved.getStartsAt()).isEqualTo(TOMORROW.plusHours(5));
        assertThat(saved.getEndsAt()).isEqualTo(TOMORROW.plusHours(7));
        SaleEventItem savedItem = saleEventItemRepository.findAll().get(0);
        assertThat(savedItem.getDiscountRate()).isEqualTo(40);
        assertThat(savedItem.getSalePrice()).isEqualTo(1800);
        assertThat(savedItem.getLimitedQuantity()).isEqualTo(4);
        assertThat(savedItem.getRemainingQuantity()).isEqualTo(4);
    }

    @Test
    @DisplayName("진행 중·종료 행사와 시작 시각이 지난 예정 행사는 거절하고 값은 그대로다")
    void update_startedEvent_rejects() {
        Product greenOnion = saveProduct("대파", 3000);
        Product tofu = saveProduct("두부", 3000);
        Product onion = saveProduct("양파", 3000);
        Long activeId = saveSpecialEvent(greenOnion);
        Long endedId = saveSpecialEvent(tofu);
        Long overdueId = saveSpecialEvent(onion);
        changeStatus(activeId, SaleEventStatus.ACTIVE);
        changeStatus(endedId, SaleEventStatus.ENDED);
        // 상태 정리가 늦어 아직 예정으로 남았지만 시작 시각은 지났다
        jdbcTemplate.update("update sale_events set starts_at = ? where id = ?",
                LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(1), overdueId);
        // 행사마다 그 행사에 든 상품으로 고치려 한다
        Map<Long, Product> productByEventId = Map.of(activeId, greenOnion, endedId, tofu, overdueId, onion);

        productByEventId.forEach((eventId, product) ->
                assertThatThrownBy(() -> saleEventUpdateService.update(eventId,
                        new SaleEventUpdateRequest(TOMORROW.plusDays(3), TOMORROW.plusDays(4),
                                List.of(item(product, 50, null)))))
                        .isInstanceOf(BusinessException.class)
                        .extracting("errorCode").isEqualTo(ErrorCode.SALE_EVENT_NOT_EDITABLE));
        assertThat(saleEventItemRepository.findAll())
                .extracting(SaleEventItem::getDiscountRate, SaleEventItem::getSalePrice)
                .containsOnly(tuple(20, 2400));
        assertThat(saleEventRepository.findAll())
                .extracting(SaleEvent::getEndsAt).doesNotContain(TOMORROW.plusDays(4));
    }

    @Test
    @DisplayName("항목 하나라도 거절되면 시각과 다른 항목도 바뀌지 않는다")
    void update_oneItemInvalid_changesNothing() {
        Product greenOnion = saveProduct("대파", 3000);
        Product tofu = saveProduct("두부", 3000);
        Long eventId = saveSpecialEvent(greenOnion, tofu);

        // 특가는 한정 수량을 받지 않으므로 두 번째 항목이 거절된다
        assertThatThrownBy(() -> saleEventUpdateService.update(eventId, new SaleEventUpdateRequest(
                TOMORROW.plusDays(2), TOMORROW.plusDays(9),
                List.of(item(greenOnion, 50, null), item(tofu, 50, 5)))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        SaleEvent saved = saleEventRepository.findById(eventId).orElseThrow();
        assertThat(saved.getStartsAt()).isEqualTo(TOMORROW);
        assertThat(saleEventItemRepository.findAll())
                .extracting(SaleEventItem::getDiscountRate).containsOnly(20);
    }

    @Test
    @DisplayName("한정 수량이 재고보다 많으면 거절하고 값은 그대로다")
    void update_limitedQuantityOverStock_rejects() {
        Product tofu = saveProduct("두부", 3000);
        Long eventId = saleEventCreateService.createFlashDrop(flashDrop(tofu)).saleEventIds().get(0);

        assertThatThrownBy(() -> saleEventUpdateService.update(eventId, new SaleEventUpdateRequest(
                TOMORROW, TOMORROW.plusHours(2), List.of(item(tofu, 10, 11)))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("한정 수량이 재고보다 많습니다 · 두부")
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        SaleEventItem saved = saleEventItemRepository.findAll().get(0);
        assertThat(saved.getDiscountRate()).isEqualTo(50);
        assertThat(saved.getLimitedQuantity()).isEqualTo(10);
        assertThat(saved.getRemainingQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("번개 드롭에 한정 수량이 없으면 거절한다")
    void update_flashDropWithoutLimitedQuantity_rejects() {
        Product tofu = saveProduct("두부", 3000);
        Long eventId = saleEventCreateService.createFlashDrop(flashDrop(tofu)).saleEventIds().get(0);

        assertThatThrownBy(() -> saleEventUpdateService.update(eventId, new SaleEventUpdateRequest(
                TOMORROW, TOMORROW.plusHours(2), List.of(item(tofu, 10, null)))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(saleEventItemRepository.findAll().get(0).getLimitedQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("행사에 든 상품과 다른 상품을 보내면 거절하고 값은 그대로다")
    void update_differentProducts_rejects() {
        Product greenOnion = saveProduct("대파", 3000);
        Product tofu = saveProduct("두부", 3000);
        Product onion = saveProduct("양파", 3000);
        Long eventId = saveSpecialEvent(greenOnion, tofu);

        // 상품이 빠진 경우, 다른 상품으로 바꾼 경우
        for (List<SaleEventItemUpdateRequest> items : List.of(
                List.of(item(greenOnion, 50, null)),
                List.of(item(greenOnion, 50, null), item(onion, 50, null)))) {
            assertThatThrownBy(() -> saleEventUpdateService.update(eventId,
                    new SaleEventUpdateRequest(TOMORROW, TOMORROW.plusDays(7), items)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        }
        assertThat(saleEventItemRepository.findAll())
                .extracting(SaleEventItem::getDiscountRate).containsOnly(20);
    }

    @Test
    @DisplayName("시작 시각이 지금보다 과거면 거절하고 값은 그대로다")
    void update_pastStartsAt_rejects() {
        Product greenOnion = saveProduct("대파", 3000);
        Long eventId = saveSpecialEvent(greenOnion);
        LocalDateTime past = LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(1);

        assertThatThrownBy(() -> saleEventUpdateService.update(eventId, new SaleEventUpdateRequest(
                past, past.plusHours(2), List.of(item(greenOnion, 50, null)))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(saleEventRepository.findById(eventId).orElseThrow().getStartsAt()).isEqualTo(TOMORROW);
    }

    @Test
    @DisplayName("없는 행사는 404로 거절한다")
    void update_missing_rejects() {
        assertThatThrownBy(() -> saleEventUpdateService.update(999L, new SaleEventUpdateRequest(
                TOMORROW, TOMORROW.plusDays(1), List.of(new SaleEventItemUpdateRequest(1L, 10, null)))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SALE_EVENT_NOT_FOUND);
    }

    // 한 행사에 상품이 여럿인 특가. 지금은 상품마다 행사를 따로 만들지만, 예전에 묶어 등록한 행사가 남아 있을 수 있다.
    private Long saveSpecialEvent(Product... products) {
        SaleEvent event = saleEventRepository.save(SaleEvent.builder().type(SaleEventType.SPECIAL)
                .startsAt(TOMORROW).endsAt(TOMORROW.plusDays(7)).build());
        for (Product product : products) {
            saleEventItemRepository.save(SaleEventItem.builder().saleEvent(event).productId(product.getId())
                    .listPrice(product.getPrice()).discountRate(20).build());
        }
        return event.getId();
    }

    private FlashDropCreateRequest flashDrop(Product product) {
        return new FlashDropCreateRequest(TOMORROW, TOMORROW.plusHours(2),
                List.of(new FlashDropItemRequest(product.getId(), 50, 10)));
    }

    private SaleEventItemUpdateRequest item(Product product, int discountRate, Integer limitedQuantity) {
        return new SaleEventItemUpdateRequest(product.getId(), discountRate, limitedQuantity);
    }

    // 상태를 바꾸는 행사 메서드가 아직 없어 DB 값을 직접 바꾼다
    private void changeStatus(Long saleEventId, SaleEventStatus status) {
        jdbcTemplate.update("update sale_events set status = ? where id = ?", status.name(), saleEventId);
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
