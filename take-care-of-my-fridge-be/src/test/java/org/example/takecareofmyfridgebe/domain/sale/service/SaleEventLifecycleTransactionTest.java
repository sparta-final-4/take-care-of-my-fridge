package org.example.takecareofmyfridgebe.domain.sale.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.domain.sale.dto.ActiveSale;
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

// 행사를 열고 닫는 상태 전환과, 시간에 따른 진행 중 할인 조회를 DB 값으로 확인한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, SaleEventStatusService.class, SaleEventReader.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SaleEventLifecycleTransactionTest extends MySqlContainerSupport {

    private static final LocalDateTime NOW = LocalDateTime.now(SaleEvent.ZONE);

    private final SaleEventStatusService saleEventStatusService;
    private final SaleEventReader saleEventReader;
    private final SaleEventRepository saleEventRepository;
    private final SaleEventItemRepository saleEventItemRepository;
    private final ProductRepository productRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    SaleEventLifecycleTransactionTest(
            SaleEventStatusService saleEventStatusService,
            SaleEventReader saleEventReader,
            SaleEventRepository saleEventRepository,
            SaleEventItemRepository saleEventItemRepository,
            ProductRepository productRepository,
            DataSource dataSource) {
        this.saleEventStatusService = saleEventStatusService;
        this.saleEventReader = saleEventReader;
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
    @DisplayName("시작 시각이 지난 예정 행사는 진행 중으로, 종료 시각이 지난 진행 중 행사는 종료로 바뀌고 나머지는 그대로다")
    void openAndCloseDue_changesOnlyDueEvents() {
        Product a = saveProduct("대파", 3000, 10);
        Product b = saveProduct("두부", 3000, 10);
        Product c = saveProduct("우유", 3000, 10);
        Product d = saveProduct("양파", 3000, 10);
        SaleEvent due = saveEvent(SaleEventType.SPECIAL, SaleEventStatus.SCHEDULED, NOW.minusMinutes(1), NOW.plusHours(1), a, null);
        SaleEvent notYet = saveEvent(SaleEventType.SPECIAL, SaleEventStatus.SCHEDULED, NOW.plusHours(1), NOW.plusHours(2), b, null);
        SaleEvent over = saveEvent(SaleEventType.SPECIAL, SaleEventStatus.ACTIVE, NOW.minusHours(2), NOW.minusMinutes(1), c, null);
        SaleEvent running = saveEvent(SaleEventType.SPECIAL, SaleEventStatus.ACTIVE, NOW.minusHours(1), NOW.plusHours(1), d, null);

        saleEventStatusService.openAndCloseDue(NOW);

        assertThat(statusOf(due)).isEqualTo(SaleEventStatus.ACTIVE);
        assertThat(statusOf(notYet)).isEqualTo(SaleEventStatus.SCHEDULED);
        assertThat(statusOf(over)).isEqualTo(SaleEventStatus.ENDED);
        assertThat(statusOf(running)).isEqualTo(SaleEventStatus.ACTIVE);
    }

    @Test
    @DisplayName("열기 전에 이미 종료 시각이 지난 예정 행사는 진행 중을 거치지 않고 바로 종료된다")
    void openAndCloseDue_scheduledEventAlreadyOver_endsDirectly() {
        Product a = saveProduct("대파", 3000, 10);
        SaleEvent missed = saveEvent(SaleEventType.SPECIAL, SaleEventStatus.SCHEDULED, NOW.minusHours(2), NOW.minusHours(1), a, null);

        saleEventStatusService.openAndCloseDue(NOW);

        assertThat(statusOf(missed)).isEqualTo(SaleEventStatus.ENDED);
    }

    @Test
    @DisplayName("같은 시각에 두 번 처리해도 결과가 같다")
    void openAndCloseDue_isIdempotent() {
        Product a = saveProduct("대파", 3000, 10);
        SaleEvent due = saveEvent(SaleEventType.SPECIAL, SaleEventStatus.SCHEDULED, NOW.minusMinutes(1), NOW.plusHours(1), a, null);

        saleEventStatusService.openAndCloseDue(NOW);
        saleEventStatusService.openAndCloseDue(NOW);

        assertThat(statusOf(due)).isEqualTo(SaleEventStatus.ACTIVE);
    }

    @Test
    @DisplayName("진행 중인 행사의 상품만 할인으로 조회되고 예정·종료 행사와 종료 시각이 지난 진행 중 행사는 빠진다")
    void findActiveSales_returnsOnlyActive() {
        Product special = saveProduct("두부", 3000, 10);
        Product flashDrop = saveProduct("우유", 4000, 10);
        Product scheduled = saveProduct("대파", 3000, 10);
        Product ended = saveProduct("양파", 3000, 10);
        Product overdue = saveProduct("감자", 3000, 10);
        Product plain = saveProduct("무", 3000, 10);
        saveEvent(SaleEventType.SPECIAL, SaleEventStatus.ACTIVE, NOW.minusHours(1), NOW.plusHours(1), special, null);
        saveEvent(SaleEventType.FLASH_DROP, SaleEventStatus.ACTIVE, NOW.minusHours(1), NOW.plusHours(1), flashDrop, 5);
        saveEvent(SaleEventType.SPECIAL, SaleEventStatus.SCHEDULED, NOW.plusHours(1), NOW.plusHours(2), scheduled, null);
        saveEvent(SaleEventType.SPECIAL, SaleEventStatus.ENDED, NOW.minusHours(2), NOW.minusHours(1), ended, null);
        saveEvent(SaleEventType.SPECIAL, SaleEventStatus.ACTIVE, NOW.minusHours(2), NOW.minusMinutes(1), overdue, null);

        Map<Long, ActiveSale> sales = saleEventReader.findActiveSales(
                List.of(special.getId(), flashDrop.getId(), scheduled.getId(), ended.getId(), overdue.getId(), plain.getId()));

        assertThat(sales).containsOnlyKeys(special.getId(), flashDrop.getId());
        ActiveSale flashDropSale = sales.get(flashDrop.getId());
        assertThat(flashDropSale.type()).isEqualTo(SaleEventType.FLASH_DROP);
        assertThat(flashDropSale.salePrice()).isEqualTo(2000);
        assertThat(flashDropSale.discountRate()).isEqualTo(50);
        assertThat(flashDropSale.remainingQuantity()).isEqualTo(5);
        assertThat(flashDropSale.saleEventItemId()).isNotNull();
        assertThat(sales.get(special.getId()).remainingQuantity()).isNull();
    }

    @Test
    @DisplayName("스케줄러가 아직 상태를 안 바꿨어도 시작 시각이 지난 예정 행사는 진행 중으로 조회되고, 종료로 닫은 행사는 시각이 남아도 빠진다")
    void findActiveSales_judgesByTimeNotByStatus() {
        Product startedButScheduled = saveProduct("대파", 3000, 10);
        Product manuallyEnded = saveProduct("두부", 3000, 10);
        saveEvent(SaleEventType.SPECIAL, SaleEventStatus.SCHEDULED, NOW.minusSeconds(5), NOW.plusHours(1), startedButScheduled, null);
        saveEvent(SaleEventType.SPECIAL, SaleEventStatus.ENDED, NOW.minusHours(1), NOW.plusHours(1), manuallyEnded, null);

        Map<Long, ActiveSale> sales = saleEventReader.findActiveSales(
                List.of(startedButScheduled.getId(), manuallyEnded.getId()));

        assertThat(sales).containsOnlyKeys(startedButScheduled.getId());
    }

    private SaleEventStatus statusOf(SaleEvent event) {
        return saleEventRepository.findById(event.getId()).orElseThrow().getStatus();
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
