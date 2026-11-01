package org.example.takecareofmyfridgebe.domain.sale.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderItemRepository;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderRepository;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventListRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventListResponse;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventListSort;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 행사 목록의 상태·종류·시작일·종료일 조건을 DB 값으로 확인한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, SaleEventListQueryService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SaleEventListQueryServiceTest extends MySqlContainerSupport {

    private final SaleEventListQueryService saleEventListQueryService;
    private final SaleEventRepository saleEventRepository;
    private final SaleEventItemRepository saleEventItemRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final JdbcTemplate jdbcTemplate;

    private SaleEvent scheduledSpecial;
    private SaleEvent activeFlash;
    private SaleEvent endedSpecial;

    @Autowired
    SaleEventListQueryServiceTest(
            SaleEventListQueryService saleEventListQueryService,
            SaleEventRepository saleEventRepository,
            SaleEventItemRepository saleEventItemRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            DataSource dataSource) {
        this.saleEventListQueryService = saleEventListQueryService;
        this.saleEventRepository = saleEventRepository;
        this.saleEventItemRepository = saleEventItemRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() {
        Product a = saveProduct("대파");
        Product b = saveProduct("두부");
        Product c = saveProduct("양파");
        // 10/1 18시 ~ 10/1 20시 번개 드롭(진행), 9/20 ~ 9/30 특가(종료), 11/1 ~ 11/7 특가(예정, 상품 둘)
        endedSpecial = saveEvent(SaleEventType.SPECIAL, SaleEventStatus.ENDED,
                LocalDateTime.of(2026, 9, 20, 0, 0), LocalDateTime.of(2026, 9, 30, 23, 59), a);
        activeFlash = saveEvent(SaleEventType.FLASH_DROP, SaleEventStatus.ACTIVE,
                LocalDateTime.of(2026, 10, 1, 18, 0), LocalDateTime.of(2026, 10, 1, 20, 0), b);
        scheduledSpecial = saveEvent(SaleEventType.SPECIAL, SaleEventStatus.SCHEDULED,
                LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 7, 23, 59), c, a);
    }

    @AfterEach
    void cleanUp() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        saleEventItemRepository.deleteAll();
        saleEventRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("조건이 없으면 행사의 상품을 한 행씩 최신 행사부터 준다")
    void getSaleEvents_noCondition_returnsOneRowPerItemNewestFirst() {
        List<SaleEventListResponse> events = query(null, null, null, null, null, null);

        assertThat(events).extracting(SaleEventListResponse::saleEventId, SaleEventListResponse::productName)
                .containsExactly(
                        tuple(scheduledSpecial.getId(), "양파"), tuple(scheduledSpecial.getId(), "대파"),
                        tuple(activeFlash.getId(), "두부"), tuple(endedSpecial.getId(), "대파"));
    }

    @Test
    @DisplayName("종료된 행사는 등록 당시 정가를, 예정·진행 중 행사는 상품의 현재 정가를 준다")
    void getSaleEvents_listPriceByStatus() {
        jdbcTemplate.update("update products set price = 5000");

        List<SaleEventListResponse> events = query(null, null, null, null, null, null);

        assertThat(events).extracting(SaleEventListResponse::status, SaleEventListResponse::listPrice,
                        SaleEventListResponse::salePrice, SaleEventListResponse::discountRate)
                .containsExactly(
                        tuple(SaleEventStatus.SCHEDULED, 5000, 2400, 20), tuple(SaleEventStatus.SCHEDULED, 5000, 2400, 20),
                        tuple(SaleEventStatus.ACTIVE, 5000, 2400, 20), tuple(SaleEventStatus.ENDED, 3000, 2400, 20));
    }

    @Test
    @DisplayName("상태와 종류로 거른다")
    void getSaleEvents_filtersByStatusAndType() {
        assertThat(ids(query(SaleEventStatus.ACTIVE, null, null, null, null, null))).containsExactly(activeFlash.getId());
        assertThat(ids(query(null, SaleEventType.SPECIAL, null, null, null, null)))
                .containsExactly(scheduledSpecial.getId(), endedSpecial.getId());
        assertThat(query(SaleEventStatus.ENDED, SaleEventType.FLASH_DROP, null, null, null, null)).isEmpty();
    }

    @Test
    @DisplayName("시작일 하나만 주면 그날 시작한 행사만, 기간을 주면 그 안에 시작한 행사만 나온다")
    void getSaleEvents_filtersByStartDate() {
        assertThat(ids(query(null, null, LocalDate.of(2026, 10, 1), null, null, null))).containsExactly(activeFlash.getId());
        assertThat(query(null, null, LocalDate.of(2026, 10, 2), null, null, null)).isEmpty();
        assertThat(ids(query(null, null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1), null, null)))
                .containsExactly(activeFlash.getId(), endedSpecial.getId());
    }

    @Test
    @DisplayName("종료일 하나만 주면 그날 끝난 행사만, 기간을 주면 그 안에 끝난 행사만 나온다")
    void getSaleEvents_filtersByEndDate() {
        assertThat(ids(query(null, null, null, null, LocalDate.of(2026, 9, 30), null))).containsExactly(endedSpecial.getId());
        assertThat(ids(query(null, null, null, null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 7))))
                .containsExactly(scheduledSpecial.getId(), activeFlash.getId());
    }

    @Test
    @DisplayName("판매량은 결제 완료·구매 확정 주문의 수량만 센다")
    void getSaleEvents_soldQuantityCountsPaidAndConfirmedOnly() {
        SaleEventItem item = saleEventItemRepository.findAll().stream()
                .filter(i -> i.getSaleEvent().getId().equals(activeFlash.getId())).findFirst().orElseThrow();
        saveOrder(OrderStatus.PAID, item, 2);
        saveOrder(OrderStatus.CONFIRMED, item, 1);
        saveOrder(OrderStatus.CANCELLED, item, 5);
        saveOrder(OrderStatus.PENDING, item, 4);

        List<SaleEventListResponse> events = query(null, null, null, null, null, null);

        assertThat(events).extracting(SaleEventListResponse::productName, SaleEventListResponse::soldQuantity)
                .containsExactly(tuple("양파", 0L), tuple("대파", 0L), tuple("두부", 3L), tuple("대파", 0L));
    }

    @Test
    @DisplayName("정렬 기준과 방향을 주면 그 순서로 나오고, 기준이 같으면 최신 행사가 먼저다")
    void getSaleEvents_sorts() {
        assertThat(sorted(SaleEventListSort.STARTS_AT, Sort.Direction.ASC))
                .containsExactly(endedSpecial.getId(), activeFlash.getId(), scheduledSpecial.getId());
        assertThat(sorted(SaleEventListSort.ENDS_AT, Sort.Direction.DESC))
                .containsExactly(scheduledSpecial.getId(), activeFlash.getId(), endedSpecial.getId());
        assertThat(sorted(SaleEventListSort.STARTS_AT, null))
                .containsExactly(scheduledSpecial.getId(), activeFlash.getId(), endedSpecial.getId());
    }

    private List<SaleEventListResponse> rows(SaleEventListSort sort, Sort.Direction direction) {
        return saleEventListQueryService.getSaleEvents(
                new SaleEventListRequest(null, null, null, null, null, null, null, sort, direction, null, null)).content();
    }

    private List<Long> sorted(SaleEventListSort sort, Sort.Direction direction) {
        return saleEventListQueryService.getSaleEvents(
                        new SaleEventListRequest(null, null, null, null, null, null, null, sort, direction, null, null))
                .content().stream().map(SaleEventListResponse::saleEventId).distinct().toList();
    }

    // 같은 행사의 상품 행이 여러 개라 행사 순서만 본다.
    private List<Long> ids(List<SaleEventListResponse> rows) {
        return rows.stream().map(SaleEventListResponse::saleEventId).distinct().toList();
    }

    private List<SaleEventListResponse> query(SaleEventStatus status, SaleEventType type, LocalDate startFrom,
                                              LocalDate startTo, LocalDate endFrom, LocalDate endTo) {
        return saleEventListQueryService.getSaleEvents(
                new SaleEventListRequest(status, type, null, startFrom, startTo, endFrom, endTo, null, null, null, null)).content();
    }

    private void saveOrder(OrderStatus status, SaleEventItem item, int quantity) {
        Order order = Order.builder()
                .orderNumber(UUID.randomUUID().toString().substring(0, 20))
                .memberId(1L)
                .idempotencyKey(UUID.randomUUID().toString())
                .totalAmount(0L)
                .build();
        // 취소는 결제 대기 주문에서만 되고, 구매 확정은 결제 완료 주문에서만 된다.
        switch (status) {
            case PAID -> order.markPaid(LocalDateTime.now());
            case CONFIRMED -> {
                order.markPaid(LocalDateTime.now());
                order.confirm(LocalDateTime.now());
            }
            case CANCELLED -> order.cancel();
            case PENDING -> { }
        }
        Order saved = orderRepository.save(order);
        orderItemRepository.save(OrderItem.builder().order(saved).productId(item.getProductId()).productName("상품")
                .quantity(quantity).listPrice(3000).unitPrice(2400).saleType(SaleType.SPECIAL)
                .saleEventItemId(item.getId()).build());
    }

    private SaleEvent saveEvent(SaleEventType type, SaleEventStatus status, LocalDateTime startsAt,
                                LocalDateTime endsAt, Product... products) {
        SaleEvent event = saleEventRepository.save(SaleEvent.builder().type(type).startsAt(startsAt).endsAt(endsAt).build());
        jdbcTemplate.update("update sale_events set status = ? where id = ?", status.name(), event.getId());
        for (Product product : products) {
            saleEventItemRepository.save(SaleEventItem.builder()
                    .saleEvent(event).productId(product.getId()).listPrice(product.getPrice())
                    .discountRate(20).limitedQuantity(type == SaleEventType.FLASH_DROP ? 5 : null).build());
        }
        return event;
    }

    private Product saveProduct(String name) {
        return productRepository.save(Product.builder()
                .productType(ProductType.INGREDIENT)
                .name(name)
                .price(3000)
                .stock(10)
                .storage(StorageType.REFRIGERATED)
                .shelfDays(7)
                .build());
    }
}
