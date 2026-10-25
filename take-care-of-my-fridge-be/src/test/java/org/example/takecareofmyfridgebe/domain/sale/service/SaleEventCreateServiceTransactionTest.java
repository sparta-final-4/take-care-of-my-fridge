package org.example.takecareofmyfridgebe.domain.sale.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.domain.sale.dto.FlashDropCreateRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.FlashDropItemRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventsCreateResponse;
import org.example.takecareofmyfridgebe.domain.sale.dto.SpecialSaleCreateRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SpecialSaleItemRequest;
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
@Import({JpaAuditingConfig.class, QueryDslConfig.class, SaleEventCreateService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SaleEventCreateServiceTransactionTest extends MySqlContainerSupport {

    private static final LocalDateTime TOMORROW = LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(1);

    private final SaleEventCreateService saleEventCreateService;
    private final SaleEventRepository saleEventRepository;
    private final SaleEventItemRepository saleEventItemRepository;
    private final ProductRepository productRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    SaleEventCreateServiceTransactionTest(
            SaleEventCreateService saleEventCreateService,
            SaleEventRepository saleEventRepository,
            SaleEventItemRepository saleEventItemRepository,
            ProductRepository productRepository,
            DataSource dataSource) {
        this.saleEventCreateService = saleEventCreateService;
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
    @DisplayName("특가를 등록하면 상품마다 같은 기간의 예정 행사가 따로 저장되고 할인가의 원 아래는 버린다")
    void createSpecialSale_savesOneEventPerItem() {
        Product greenOnion = saveProduct("대파", 3000);
        Product tofu = saveProduct("두부", 1999);

        SaleEventsCreateResponse response = saleEventCreateService.createSpecialSale(
                specialSale(item(greenOnion, 20), item(tofu, 15)));

        assertThat(response.saleEventIds()).hasSize(2);
        List<SaleEvent> events = saleEventRepository.findAllById(response.saleEventIds());
        assertThat(events).hasSize(2)
                .extracting(SaleEvent::getType, SaleEvent::getStatus, SaleEvent::getStartsAt)
                .containsOnly(tuple(SaleEventType.SPECIAL, SaleEventStatus.SCHEDULED, events.get(0).getStartsAt()));
        assertThat(events).extracting(SaleEvent::getEndsAt).containsOnly(events.get(0).getEndsAt());
        // 행사마다 상품이 하나씩이다.
        assertThat(saleEventItemRepository.findAll())
                .extracting(item -> item.getSaleEvent().getId(), SaleEventItem::getProductId,
                        SaleEventItem::getListPrice, SaleEventItem::getSalePrice, SaleEventItem::getDiscountRate,
                        SaleEventItem::getLimitedQuantity)
                .containsExactlyInAnyOrder(
                        tuple(response.saleEventIds().get(0), greenOnion.getId(), greenOnion.getPrice(), 2400, 20, null),
                        tuple(response.saleEventIds().get(1), tofu.getId(), tofu.getPrice(), 1699, 15, null));
    }

    @Test
    @DisplayName("번개 드롭을 등록하면 한정 수량과 남은 수량이 같은 값으로 저장된다")
    void createFlashDrop_savesLimitedQuantity() {
        Product tofu = saveProduct("두부", 3000);

        SaleEventsCreateResponse response = saleEventCreateService.createFlashDrop(flashDrop(tofu));

        SaleEvent saved = saleEventRepository.findById(response.saleEventIds().get(0)).orElseThrow();
        assertThat(saved.getType()).isEqualTo(SaleEventType.FLASH_DROP);
        assertThat(saved.getStatus()).isEqualTo(SaleEventStatus.SCHEDULED);
        SaleEventItem item = saleEventItemRepository.findAll().get(0);
        assertThat(item.getListPrice()).isEqualTo(3000);
        assertThat(item.getSalePrice()).isEqualTo(1500);
        assertThat(item.getLimitedQuantity()).isEqualTo(10);
        assertThat(item.getRemainingQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("번개 드롭 상품을 여럿 등록하면 상품마다 행사가 따로 저장되고 한정 수량도 상품마다 다르다")
    void createFlashDrop_multipleItems_savesOneEventPerItem() {
        Product tofu = saveProduct("두부", 3000);
        Product greenOnion = saveProduct("대파", 2000);

        SaleEventsCreateResponse response = saleEventCreateService.createFlashDrop(new FlashDropCreateRequest(
                TOMORROW, TOMORROW.plusHours(2),
                List.of(new FlashDropItemRequest(tofu.getId(), 50, 10), new FlashDropItemRequest(greenOnion.getId(), 30, 4))));

        assertThat(response.saleEventIds()).hasSize(2);
        assertThat(saleEventRepository.findAllById(response.saleEventIds()))
                .extracting(SaleEvent::getType, SaleEvent::getStatus)
                .containsOnly(tuple(SaleEventType.FLASH_DROP, SaleEventStatus.SCHEDULED));
        assertThat(saleEventItemRepository.findAll())
                .extracting(item -> item.getSaleEvent().getId(), SaleEventItem::getProductId,
                        SaleEventItem::getSalePrice, SaleEventItem::getLimitedQuantity,
                        SaleEventItem::getRemainingQuantity)
                .containsExactlyInAnyOrder(
                        tuple(response.saleEventIds().get(0), tofu.getId(), 1500, 10, 10),
                        tuple(response.saleEventIds().get(1), greenOnion.getId(), 1400, 4, 4));
    }

    @Test
    @DisplayName("한 상품의 한정 수량이 재고보다 많으면 다른 상품도 등록되지 않는다")
    void createFlashDrop_oneItemOverStock_savesNothing() {
        Product tofu = saveProduct("두부", 3000);
        Product greenOnion = saveProduct("대파", 2000);

        assertThatThrownBy(() -> saleEventCreateService.createFlashDrop(new FlashDropCreateRequest(
                TOMORROW, TOMORROW.plusHours(2),
                List.of(new FlashDropItemRequest(tofu.getId(), 50, 10), new FlashDropItemRequest(greenOnion.getId(), 30, 11)))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("한정 수량이 재고보다 많습니다 · 대파");
        assertThat(saleEventRepository.count()).isZero();
        assertThat(saleEventItemRepository.count()).isZero();
    }

    @Test
    @DisplayName("종료 시각이 지난 행사는 상태 정리가 늦어도 같은 상품의 새 행사를 막지 않는다")
    void create_productInOverdueEvent_allowed() {
        Product tofu = saveProduct("두부", 3000);
        Long overdueEventId = saleEventCreateService.createFlashDrop(flashDrop(tofu)).saleEventIds().get(0);
        changeStatus(overdueEventId, SaleEventStatus.ACTIVE);
        jdbcTemplate.update("update sale_events set ends_at = ? where id = ?",
                LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(1), overdueEventId);

        saleEventCreateService.createFlashDrop(flashDrop(tofu));

        assertThat(saleEventRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("번개 드롭 한정 수량이 재고보다 많으면 거절하고 행사는 저장되지 않는다")
    void createFlashDrop_limitedQuantityOverStock_rejects() {
        Product tofu = saveProduct("두부", 3000);
        FlashDropCreateRequest request = new FlashDropCreateRequest(TOMORROW, TOMORROW.plusHours(2),
                List.of(new FlashDropItemRequest(tofu.getId(), 50, 11)));

        assertThatThrownBy(() -> saleEventCreateService.createFlashDrop(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("한정 수량이 재고보다 많습니다 · 두부")
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(saleEventRepository.count()).isZero();
        assertThat(saleEventItemRepository.count()).isZero();
    }

    @Test
    @DisplayName("예정이나 진행 중인 행사에 든 상품은 거절하고 새 행사는 저장되지 않는다")
    void create_productInOpenEvent_rejects() {
        Product greenOnion = saveProduct("대파", 3000);
        Product tofu = saveProduct("두부", 3000);
        saleEventCreateService.createSpecialSale(specialSale(item(greenOnion, 20)));
        Long activeEventId = saleEventCreateService.createFlashDrop(flashDrop(tofu)).saleEventIds().get(0);
        changeStatus(activeEventId, SaleEventStatus.ACTIVE);

        assertThatThrownBy(() -> saleEventCreateService.createFlashDrop(flashDrop(greenOnion)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 행사가 있는 상품 · 대파")
                .extracting("errorCode").isEqualTo(ErrorCode.SALE_EVENT_PRODUCT_DUPLICATED);
        assertThatThrownBy(() -> saleEventCreateService.createSpecialSale(specialSale(item(tofu, 10))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SALE_EVENT_PRODUCT_DUPLICATED);
        assertThat(saleEventRepository.count()).isEqualTo(2);
        assertThat(saleEventItemRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("종료된 행사에 들었던 상품은 새 행사에 다시 넣을 수 있다")
    void create_productInEndedEvent_saves() {
        Product greenOnion = saveProduct("대파", 3000);
        Long endedEventId = saleEventCreateService.createSpecialSale(specialSale(item(greenOnion, 20))).saleEventIds().get(0);
        changeStatus(endedEventId, SaleEventStatus.ENDED);

        saleEventCreateService.createFlashDrop(flashDrop(greenOnion));

        assertThat(saleEventRepository.count()).isEqualTo(2);
        assertThat(saleEventItemRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("품절이나 판매 중지 상품은 거절하고 행사는 저장되지 않는다")
    void create_notOnSaleProduct_rejects() {
        Product greenOnion = saveProduct("대파", 3000);
        Product soldOut = saveProduct("두부", 3000, ProductStatus.SOLD_OUT);
        Product stopped = saveProduct("양파", 3000, ProductStatus.STOPPED);

        assertThatThrownBy(() -> saleEventCreateService.createSpecialSale(
                specialSale(item(greenOnion, 20), item(soldOut, 20))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("품절 · 두부")
                .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_NOT_ON_SALE);
        assertThatThrownBy(() -> saleEventCreateService.createFlashDrop(flashDrop(stopped)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("판매 중지 · 양파")
                .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_NOT_ON_SALE);
        assertThat(saleEventRepository.count()).isZero();
        assertThat(saleEventItemRepository.count()).isZero();
    }

    @Test
    @DisplayName("없는 상품이 섞이면 404이고 행사는 저장되지 않는다")
    void create_missingProduct_rejects() {
        Product greenOnion = saveProduct("대파", 3000);

        assertThatThrownBy(() -> saleEventCreateService.createSpecialSale(specialSale(
                item(greenOnion, 20), new SpecialSaleItemRequest(greenOnion.getId() + 1000, 20))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
        assertThat(saleEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("시작 시각이 지금보다 과거면 거절하고 행사는 저장되지 않는다")
    void create_pastStartsAt_rejects() {
        Product greenOnion = saveProduct("대파", 3000);
        LocalDateTime past = LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(1);

        assertThatThrownBy(() -> saleEventCreateService.createFlashDrop(
                new FlashDropCreateRequest(past, past.plusHours(2),
                        List.of(new FlashDropItemRequest(greenOnion.getId(), 50, 10)))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(saleEventRepository.count()).isZero();
    }

    @Test
    @DisplayName("같은 상품을 동시에 5번 등록해도 행사는 하나만 저장되고 나머지는 이미 행사가 있는 상품으로 거절된다")
    void create_sameProductConcurrently_savesOnlyOne() throws Exception {
        Product tofu = saveProduct("두부", 3000);

        List<Throwable> failures = runConcurrently(Collections.nCopies(5, () -> {
            saleEventCreateService.createFlashDrop(flashDrop(tofu));
            return null;
        }));

        assertThat(failures).hasSize(4)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOf(BusinessException.class)
                        .extracting("errorCode").isEqualTo(ErrorCode.SALE_EVENT_PRODUCT_DUPLICATED));
        assertThat(saleEventRepository.count()).isEqualTo(1);
        assertThat(saleEventItemRepository.findAll())
                .extracting(SaleEventItem::getProductId)
                .containsExactly(tofu.getId());
    }

    private List<Throwable> runConcurrently(List<Callable<Void>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Void>> futures = tasks.stream()
                    .map(task -> executor.submit(() -> {
                        start.await();
                        return task.call();
                    }))
                    .toList();
            start.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<Void> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            return failures;
        } finally {
            executor.shutdownNow();
        }
    }

    private SpecialSaleCreateRequest specialSale(SpecialSaleItemRequest... items) {
        return new SpecialSaleCreateRequest(TOMORROW, TOMORROW.plusDays(7), List.of(items));
    }

    private SpecialSaleItemRequest item(Product product, int discountRate) {
        return new SpecialSaleItemRequest(product.getId(), discountRate);
    }

    private FlashDropCreateRequest flashDrop(Product product) {
        return new FlashDropCreateRequest(TOMORROW, TOMORROW.plusHours(2),
                List.of(new FlashDropItemRequest(product.getId(), 50, 10)));
    }

    // 상태를 바꾸는 행사 메서드가 아직 없어 DB 값을 직접 바꾼다
    private void changeStatus(Long saleEventId, SaleEventStatus status) {
        jdbcTemplate.update("update sale_events set status = ? where id = ?", status.name(), saleEventId);
    }

    private Product saveProduct(String name, int price) {
        return saveProduct(name, price, ProductStatus.ON_SALE);
    }

    private Product saveProduct(String name, int price, ProductStatus status) {
        return productRepository.save(Product.builder()
                .productType(ProductType.INGREDIENT)
                .name(name)
                .imageUrl("https://img.test/" + name + ".png")
                .price(price)
                .stock(10)
                .storage(StorageType.REFRIGERATED)
                .shelfDays(7)
                .status(status)
                .build());
    }
}
