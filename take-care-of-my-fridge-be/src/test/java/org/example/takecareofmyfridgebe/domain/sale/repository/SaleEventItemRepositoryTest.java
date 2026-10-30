package org.example.takecareofmyfridgebe.domain.sale.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import javax.sql.DataSource;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.domain.sale.dto.ActiveSale;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventItem;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventStatus;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;
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

// 행사 상태는 JDBC로 직접 바꿔 저장하므로, 테스트 자체 트랜잭션을 끄고(NOT_SUPPORTED) 커밋된 값을 쿼리로 읽는다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SaleEventItemRepositoryTest extends MySqlContainerSupport {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final SaleEventRepository saleEventRepository;
    private final SaleEventItemRepository saleEventItemRepository;
    private final ProductRepository productRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    SaleEventItemRepositoryTest(
            SaleEventRepository saleEventRepository,
            SaleEventItemRepository saleEventItemRepository,
            ProductRepository productRepository,
            DataSource dataSource) {
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
    @DisplayName("진행 중인 할인 전부를 읽을 때 시작 시각이 지난 예정 행사는 넣고, 시작 전·종료 시각이 지난·종료로 닫은 행사는 뺀다")
    void findAllActive_onlyActiveByTime() {
        // given
        Product active = saveProduct("두부");
        Product overdueScheduled = saveProduct("대파");
        Product notStarted = saveProduct("양파");
        Product expired = saveProduct("계란");
        Product ended = saveProduct("우유");
        saveEvent(SaleEventStatus.ACTIVE, minutesFromNow(-60), minutesFromNow(60), active);
        saveEvent(SaleEventStatus.SCHEDULED, minutesFromNow(-1), minutesFromNow(60), overdueScheduled);
        saveEvent(SaleEventStatus.SCHEDULED, minutesFromNow(60), minutesFromNow(120), notStarted);
        saveEvent(SaleEventStatus.ACTIVE, minutesFromNow(-120), minutesFromNow(-1), expired);
        saveEvent(SaleEventStatus.ENDED, minutesFromNow(-60), minutesFromNow(60), ended);

        // when
        LocalDateTime now = LocalDateTime.now(ZONE);

        // then
        assertThat(saleEventItemRepository.findAllActive(SaleEventStatus.OPEN, now))
                .extracting(ActiveSale::productId)
                .containsExactlyInAnyOrder(active.getId(), overdueScheduled.getId());
    }

    private LocalDateTime minutesFromNow(long minutes) {
        return LocalDateTime.now(ZONE).plusMinutes(minutes).withNano(0);
    }

    private void saveEvent(SaleEventStatus status, LocalDateTime startsAt, LocalDateTime endsAt, Product product) {
        SaleEvent event = saleEventRepository.save(SaleEvent.builder().type(SaleEventType.SPECIAL)
                .startsAt(startsAt).endsAt(endsAt).build());
        jdbcTemplate.update("update sale_events set status = ? where id = ?", status.name(), event.getId());
        saleEventItemRepository.save(SaleEventItem.builder().saleEvent(event).productId(product.getId())
                .listPrice(product.getPrice()).discountRate(20).build());
    }

    private Product saveProduct(String name) {
        return productRepository.save(Product.builder()
                .productType(ProductType.INGREDIENT)
                .name(name)
                .price(3000)
                .stock(10)
                .storage(StorageType.REFRIGERATED)
                .shelfDays(7)
                .status(ProductStatus.ON_SALE)
                .build());
    }
}
