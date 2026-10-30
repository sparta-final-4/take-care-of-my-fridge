package org.example.takecareofmyfridgebe.domain.sale.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
import org.example.takecareofmyfridgebe.domain.notification.entity.NotificationType;
import org.example.takecareofmyfridgebe.domain.notification.event.NotificationRequestedMessage;
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
import org.example.takecareofmyfridgebe.domain.sale.event.FlashDropNotificationEvent;
import org.example.takecareofmyfridgebe.domain.sale.event.FlashDropNotificationEvent.Moment;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 번개 드롭 등록·시작에서 어떤 행사의 알림을 내보내는지, 그 알림을 누가 받는지 확인한다.
// 커밋된 행사·회원을 다시 읽어 알림 요청을 만들고, Kafka 발행 리스너는 띄우지 않는다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class,
        SaleEventCreateService.class, SaleEventStatusService.class, FlashDropNotificationReader.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@RecordApplicationEvents
class FlashDropNotificationTransactionTest extends MySqlContainerSupport {

    private static final LocalDateTime NOW = LocalDateTime.now(SaleEvent.ZONE);
    private static final LocalDateTime TOMORROW = NOW.plusDays(1);

    private final SaleEventCreateService saleEventCreateService;
    private final SaleEventStatusService saleEventStatusService;
    private final FlashDropNotificationReader flashDropNotificationReader;
    private final SaleEventRepository saleEventRepository;
    private final SaleEventItemRepository saleEventItemRepository;
    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    FlashDropNotificationTransactionTest(
            SaleEventCreateService saleEventCreateService,
            SaleEventStatusService saleEventStatusService,
            FlashDropNotificationReader flashDropNotificationReader,
            SaleEventRepository saleEventRepository,
            SaleEventItemRepository saleEventItemRepository,
            ProductRepository productRepository,
            MemberRepository memberRepository,
            DataSource dataSource) {
        this.saleEventCreateService = saleEventCreateService;
        this.saleEventStatusService = saleEventStatusService;
        this.flashDropNotificationReader = flashDropNotificationReader;
        this.saleEventRepository = saleEventRepository;
        this.saleEventItemRepository = saleEventItemRepository;
        this.productRepository = productRepository;
        this.memberRepository = memberRepository;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void cleanUp() {
        saleEventItemRepository.deleteAll();
        saleEventRepository.deleteAll();
        productRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("번개 드롭을 등록하면 정지되지 않은 일반 회원마다 예고 알림 요청이 만들어지고 관리자와 정지 회원은 빠진다")
    void createFlashDrop_notifiesActiveUsersOnly(ApplicationEvents events) {
        Member user1 = saveMember("user1");
        Member user2 = saveMember("user2");
        Member admin = saveMember("admin");
        admin.promoteToAdmin();
        memberRepository.save(admin);
        Member suspended = saveMember("suspended");
        jdbcTemplate.update("update members set status = 'SUSPENDED' where id = ?", suspended.getId());
        Product tofu = saveProduct("두부", 3000);
        LocalDateTime startsAt = LocalDateTime.of(TOMORROW.getYear(), TOMORROW.getMonth(), TOMORROW.getDayOfMonth(), 18, 0);

        SaleEventsCreateResponse response = saleEventCreateService.createFlashDrop(new FlashDropCreateRequest(
                startsAt, startsAt.plusHours(2), List.of(new FlashDropItemRequest(tofu.getId(), 50, 10))));

        List<FlashDropNotificationEvent> published = events.stream(FlashDropNotificationEvent.class).toList();
        assertThat(published).containsExactly(FlashDropNotificationEvent.created(response.saleEventIds()));
        Long saleEventId = response.saleEventIds().get(0);
        String content = "두부 50%% 번개 드롭 · %d월 %d일 18:00 시작 · 10개 한정"
                .formatted(startsAt.getMonthValue(), startsAt.getDayOfMonth());
        String dedupeKey = "FLASH_DROP_CREATED:%d:%d".formatted(saleEventId, tofu.getId());
        assertThat(flashDropNotificationReader.read(published.get(0)))
                .extracting(NotificationRequestedMessage::memberId, NotificationRequestedMessage::notificationType,
                        NotificationRequestedMessage::content, NotificationRequestedMessage::linkUrl,
                        NotificationRequestedMessage::dedupeKey)
                .containsExactlyInAnyOrder(
                        tuple(user1.getId(), NotificationType.FLASH_DROP, content, "/products/" + tofu.getId(), dedupeKey),
                        tuple(user2.getId(), NotificationType.FLASH_DROP, content, "/products/" + tofu.getId(), dedupeKey));
    }

    @Test
    @DisplayName("특가를 등록하면 알림을 내보내지 않는다")
    void createSpecialSale_doesNotNotify(ApplicationEvents events) {
        saveMember("user1");
        Product tofu = saveProduct("두부", 3000);

        saleEventCreateService.createSpecialSale(new SpecialSaleCreateRequest(
                TOMORROW, TOMORROW.plusDays(7), List.of(new SpecialSaleItemRequest(tofu.getId(), 20))));

        assertThat(events.stream(FlashDropNotificationEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("시작 시각이 된 번개 드롭만 시작 알림을 내보내고, 특가와 아직 시작 전인 번개 드롭은 빠진다")
    void openAndCloseDue_notifiesStartedFlashDropsOnly(ApplicationEvents events) {
        Member user = saveMember("user1");
        Product tofu = saveProduct("두부", 3000);
        Product milk = saveProduct("우유", 4000);
        Product greenOnion = saveProduct("대파", 2000);
        SaleEvent dueFlashDrop = saveEvent(SaleEventType.FLASH_DROP, NOW.minusMinutes(1), tofu, 10);
        saveEvent(SaleEventType.FLASH_DROP, NOW.plusHours(1), milk, 5);
        saveEvent(SaleEventType.SPECIAL, NOW.minusMinutes(1), greenOnion, null);

        saleEventStatusService.openAndCloseDue(NOW);

        List<FlashDropNotificationEvent> published = events.stream(FlashDropNotificationEvent.class).toList();
        assertThat(published).containsExactly(FlashDropNotificationEvent.started(List.of(dueFlashDrop.getId())));
        assertThat(flashDropNotificationReader.read(published.get(0)))
                .extracting(NotificationRequestedMessage::memberId, NotificationRequestedMessage::content,
                        NotificationRequestedMessage::dedupeKey)
                .containsExactly(tuple(user.getId(), "두부 50% 번개 드롭 시작 · 10개 한정",
                        "FLASH_DROP_STARTED:%d:%d".formatted(dueFlashDrop.getId(), tofu.getId())));
        assertThat(published.get(0).moment()).isEqualTo(Moment.STARTED);
    }

    private SaleEvent saveEvent(SaleEventType type, LocalDateTime startsAt, Product product, Integer limitedQuantity) {
        SaleEvent event = saleEventRepository.save(
                SaleEvent.builder().type(type).startsAt(startsAt).endsAt(startsAt.plusHours(2)).build());
        saleEventItemRepository.save(SaleEventItem.builder()
                .saleEvent(event).productId(product.getId()).listPrice(product.getPrice())
                .discountRate(type == SaleEventType.FLASH_DROP ? 50 : 20).limitedQuantity(limitedQuantity).build());
        return event;
    }

    private Member saveMember(String nickname) {
        return memberRepository.save(Member.builder()
                .email(nickname + "@test.com")
                .passwordHash("hashed_password")
                .nickname(nickname)
                .build());
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
