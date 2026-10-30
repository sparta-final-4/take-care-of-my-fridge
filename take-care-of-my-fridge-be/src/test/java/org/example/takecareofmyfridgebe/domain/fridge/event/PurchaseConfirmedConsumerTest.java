package org.example.takecareofmyfridgebe.domain.fridge.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeCommerceHistory;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeSource;
import org.example.takecareofmyfridgebe.domain.fridge.repository.FridgeCommerceHistoryRepository;
import org.example.takecareofmyfridgebe.domain.fridge.repository.FridgeRepository;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.ingredient.repository.IngredientRepository;
import org.example.takecareofmyfridgebe.domain.order.event.OrderConfirmedEvent;
import org.example.takecareofmyfridgebe.domain.product.entity.IngredientProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.MealkitProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.product.repository.IngredientProductRepository;
import org.example.takecareofmyfridgebe.domain.product.repository.MealkitProductRepository;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.global.config.KafkaTopics;
import org.example.takecareofmyfridgebe.support.KafkaContainerSupport;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

// 주문 쪽이 보내는 구매 확정 이벤트를 실제 토픽에 보내고, 냉장고 행과 입고 이력이 어떻게 남는지 DB를 다시 읽어 확인한다.
// 주문 쪽 OrderConfirmedEvent를 그대로 보내 두 도메인의 메시지 필드가 맞는지도 함께 확인한다.
// 컨테이너는 다른 테스트와 공유하므로 테스트마다 다른 회원·주문·주문 상품 ID를 쓰고, 만든 행은 테스트가 끝나면 지운다
@SpringBootTest
@Import(PurchaseConfirmedConsumerTest.DltConsumerConfig.class)
class PurchaseConfirmedConsumerTest extends MySqlContainerSupport {

    private static final long WAIT_MILLIS = 20_000;
    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 10, 30, 14, 30, 5);
    private static final Long PROBE_MEMBER_ID = 9100L;

    // 소비자는 컨텍스트와 함께 계속 떠 있으므로 한 번 준비되면 다음 테스트에서는 확인하지 않는다
    private static boolean consumerReady;

    // MySQL은 상속으로 받으므로 Kafka는 주소만 넣는다.
    // @ImportTestcontainers로 가져오면 이 컨텍스트가 닫힐 때 공유 Kafka 컨테이너가 멈춰 다른 테스트가 실패한다
    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KafkaContainerSupport::bootstrapServers);
    }

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final FridgeRepository fridgeRepository;
    private final FridgeCommerceHistoryRepository historyRepository;
    private final IngredientRepository ingredientRepository;
    private final ProductRepository productRepository;
    private final IngredientProductRepository ingredientProductRepository;
    private final MealkitProductRepository mealkitProductRepository;
    private final DltConsumer dltConsumer;

    private final List<Ingredient> createdIngredients = new ArrayList<>();
    private Ingredient onion;
    private Ingredient mealkit;
    private Product onionProduct;
    private Product mealkitProduct;
    private Product unlinkedProduct;
    private IngredientProduct onionLink;
    private MealkitProduct mealkitDetail;

    @Autowired
    PurchaseConfirmedConsumerTest(
            KafkaTemplate<String, Object> kafkaTemplate,
            FridgeRepository fridgeRepository,
            FridgeCommerceHistoryRepository historyRepository,
            IngredientRepository ingredientRepository,
            ProductRepository productRepository,
            IngredientProductRepository ingredientProductRepository,
            MealkitProductRepository mealkitProductRepository,
            DltConsumer dltConsumer) {
        this.kafkaTemplate = kafkaTemplate;
        this.fridgeRepository = fridgeRepository;
        this.historyRepository = historyRepository;
        this.ingredientRepository = ingredientRepository;
        this.productRepository = productRepository;
        this.ingredientProductRepository = ingredientProductRepository;
        this.mealkitProductRepository = mealkitProductRepository;
        this.dltConsumer = dltConsumer;
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        onion = createIngredient("ING_PC_ONION", "양파");
        mealkit = ingredientRepository.findByIngredientCode(Ingredient.ING_MEALKIT)
                .orElseGet(() -> createIngredient(Ingredient.ING_MEALKIT, "밀키트"));

        onionProduct = productRepository.save(product(ProductType.INGREDIENT, "양파 1kg", StorageType.ROOM_TEMP, 14));
        onionLink = ingredientProductRepository.save(IngredientProduct.builder()
                .product(onionProduct)
                .ingredientId(onion.getId())
                .unitAmount(new BigDecimal("1000"))
                .unit("g")
                .baseQuantity(new BigDecimal("5"))
                .build());
        mealkitProduct = productRepository.save(product(ProductType.MEALKIT, "된장찌개 밀키트", StorageType.REFRIGERATED, 3));
        mealkitDetail = mealkitProductRepository.save(MealkitProduct.builder()
                .product(mealkitProduct)
                .servings(2)
                .build());
        // 재료 상품인데 재료 연결(ingredient_products 행)이 없는 상품
        unlinkedProduct = productRepository.save(product(ProductType.INGREDIENT, "연결 없는 대파", StorageType.REFRIGERATED, 7));

        waitUntilConsumerReady();
    }

    // 리스너가 auto.offset.reset=latest라, 커밋한 위치가 없을 때 파티션을 받기 전에 보낸 메시지는 읽지 않는다.
    // 확인용 구매 확정이 입고될 때까지 다시 보내 소비자가 준비된 뒤에 테스트 메시지를 보낸다.
    // 같은 주문 상품 ID로 다시 보내므로 늦게 처리돼도 한 번만 들어간다
    private void waitUntilConsumerReady() throws InterruptedException {
        if (consumerReady) {
            return;
        }
        OrderConfirmedEvent probe = event(9100L, PROBE_MEMBER_ID, List.of(
                new OrderConfirmedEvent.Item(910001L, mealkitProduct.getId(), "된장찌개 밀키트", 1)));
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            send(probe);
            if (!waitForFridges(PROBE_MEMBER_ID, 1, 3_000).isEmpty()) {
                consumerReady = true;
                return;
            }
        }
        throw new IllegalStateException("구매 확정 소비자가 파티션을 받지 못했습니다.");
    }

    @AfterEach
    void cleanUp() {
        List<Long> memberIds = List.of(PROBE_MEMBER_ID, 9101L, 9102L, 9103L);
        fridgeRepository.deleteAll(fridgeRepository.findAll().stream()
                .filter(fridge -> memberIds.contains(fridge.getMemberId()))
                .toList());
        historyRepository.deleteAll(historyRepository.findAll().stream()
                .filter(history -> memberIds.contains(history.getMemberId()))
                .toList());
        ingredientProductRepository.delete(onionLink);
        mealkitProductRepository.delete(mealkitDetail);
        productRepository.deleteAll(List.of(onionProduct, mealkitProduct, unlinkedProduct));
        ingredientRepository.deleteAll(createdIngredients);
        createdIngredients.clear();
    }

    @Test
    @DisplayName("재료 상품과 밀키트가 든 구매 확정을 받으면 상품마다 냉장고 행과 입고 이력을 하나씩 남긴다")
    void consume_stocksIngredientAndMealkit() throws InterruptedException {
        send(event(9101L, 9101L, List.of(
                new OrderConfirmedEvent.Item(910101L, onionProduct.getId(), "양파 1kg", 2),
                new OrderConfirmedEvent.Item(910102L, mealkitProduct.getId(), "된장찌개 밀키트", 1)
        )));

        List<Fridge> saved = waitForFridges(9101L, 2);
        assertThat(saved).hasSize(2);

        Fridge onionFridge = saved.get(0);
        assertThat(onionFridge.getOrderItemId()).isEqualTo(910101L);
        assertThat(onionFridge.getRawName()).isEqualTo("양파 1kg");
        // 확정 수량 2 × 상품 1개의 기본 단위 수량 5
        assertThat(onionFridge.getQuantity()).isEqualByComparingTo("10");
        assertThat(onionFridge.getStorage()).isEqualTo(StorageType.ROOM_TEMP);
        assertThat(onionFridge.getExpirationDate()).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(onionFridge.getSource()).isEqualTo(FridgeSource.COMMERCE);

        Fridge mealkitFridge = saved.get(1);
        assertThat(mealkitFridge.getOrderItemId()).isEqualTo(910102L);
        assertThat(mealkitFridge.getQuantity()).isEqualByComparingTo("1");
        assertThat(mealkitFridge.getStorage()).isEqualTo(StorageType.REFRIGERATED);
        assertThat(mealkitFridge.getExpirationDate()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(mealkitFridge.getSource()).isEqualTo(FridgeSource.COMMERCE);

        assertThat(findHistories(9101L)).extracting(FridgeCommerceHistory::getOrderItemId)
                .containsExactlyInAnyOrder(910101L, 910102L);
        assertThat(ingredientIds(9101L)).containsExactly(onion.getId(), mealkit.getId());
    }

    @Test
    @DisplayName("같은 구매 확정을 두 번 받아도 한 번만 넣고 DLT로 보내지 않는다")
    void consume_sameMessageTwice_stocksOnce() throws InterruptedException {
        OrderConfirmedEvent duplicated = event(9102L, 9102L, List.of(
                new OrderConfirmedEvent.Item(910201L, onionProduct.getId(), "양파 1kg", 1)));

        send(duplicated);
        send(duplicated);
        // 파티션이 하나라 순서대로 처리되므로, 뒤에 보낸 주문이 들어오면 앞의 두 메시지는 처리가 끝난 것이다
        send(event(9103L, 9102L, List.of(
                new OrderConfirmedEvent.Item(910301L, mealkitProduct.getId(), "된장찌개 밀키트", 1))));

        List<Fridge> saved = waitForFridges(9102L, 2);
        assertThat(saved).extracting(Fridge::getOrderItemId).containsExactly(910201L, 910301L);
        assertThat(findHistories(9102L)).hasSize(2);
        assertThat(pollDlt(9102L, 3)).isNull();
    }

    @Test
    @DisplayName("재료 연결이 없는 재료 상품이 있으면 주문 전체를 넣지 않고 재시도 뒤 DLT로 보낸다")
    void consume_unlinkedIngredientProduct_sendsToDlt() throws InterruptedException {
        send(event(9104L, 9103L, List.of(
                new OrderConfirmedEvent.Item(910401L, onionProduct.getId(), "양파 1kg", 1),
                new OrderConfirmedEvent.Item(910402L, unlinkedProduct.getId(), "연결 없는 대파", 1)
        )));

        assertThat(pollDlt(9104L, WAIT_MILLIS / 1000)).isNotNull();
        assertThat(findFridges(9103L)).isEmpty();
        assertThat(findHistories(9103L)).isEmpty();
    }

    @Test
    @DisplayName("상품이 모두 환불돼 items가 빈 구매 확정은 아무것도 넣지 않고 DLT로 보내지 않는다")
    void consume_emptyItems_doesNothing() throws InterruptedException {
        send(event(9105L, 9103L, List.of()));
        // 순서대로 처리되므로 뒤에 보낸 주문이 들어오면 빈 메시지는 처리가 끝난 것이다
        send(event(9106L, 9103L, List.of(
                new OrderConfirmedEvent.Item(910601L, mealkitProduct.getId(), "된장찌개 밀키트", 1))));

        assertThat(waitForFridges(9103L, 1)).extracting(Fridge::getOrderItemId).containsExactly(910601L);
        assertThat(pollDlt(9105L, 3)).isNull();
    }

    private Ingredient createIngredient(String code, String name) {
        Ingredient ingredient = ingredientRepository.save(Ingredient.builder()
                .ingredientCode(code)
                .name(name)
                .baseUnit("개")
                .defaultStorage(StorageType.REFRIGERATED)
                .defaultShelfDays(7)
                .basicSeasoning(false)
                .build());
        createdIngredients.add(ingredient);
        return ingredient;
    }

    private static Product product(ProductType type, String name, StorageType storage, int shelfDays) {
        return Product.builder()
                .productType(type)
                .name(name)
                .price(3900)
                .stock(10)
                .storage(storage)
                .shelfDays(shelfDays)
                .build();
    }

    private static OrderConfirmedEvent event(Long orderId, Long memberId, List<OrderConfirmedEvent.Item> items) {
        return new OrderConfirmedEvent(orderId, memberId, CONFIRMED_AT, items);
    }

    private void send(OrderConfirmedEvent event) {
        kafkaTemplate.send(KafkaTopics.PURCHASE_CONFIRMED, String.valueOf(event.orderId()), event).join();
    }

    // 다른 테스트가 보낸 구매 확정도 DLT로 갈 수 있어 이 테스트의 주문 ID(키)만 본다
    private ConsumerRecord<String, String> pollDlt(Long orderId, long seconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + seconds * 1000;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecord<String, String> record = dltConsumer.records.poll(200, TimeUnit.MILLISECONDS);
            if (record != null && String.valueOf(orderId).equals(record.key())) {
                return record;
            }
        }
        return null;
    }

    // 소비자가 비동기로 저장하므로 기대한 수만큼 쌓일 때까지 기다린 뒤 그 회원의 냉장고 행을 주문 상품 ID 순으로 돌려준다
    private List<Fridge> waitForFridges(Long memberId, int expectedCount) throws InterruptedException {
        return waitForFridges(memberId, expectedCount, WAIT_MILLIS);
    }

    private List<Fridge> waitForFridges(Long memberId, int expectedCount, long waitMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + waitMillis;
        List<Fridge> found = findFridges(memberId);
        while (found.size() < expectedCount && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
            found = findFridges(memberId);
        }
        return found;
    }

    private List<Fridge> findFridges(Long memberId) {
        return fridgeRepository.findAll().stream()
                .filter(fridge -> fridge.getMemberId().equals(memberId))
                .sorted(Comparator.comparing(Fridge::getOrderItemId))
                .toList();
    }

    private List<FridgeCommerceHistory> findHistories(Long memberId) {
        return historyRepository.findAll().stream()
                .filter(history -> history.getMemberId().equals(memberId))
                .toList();
    }

    // ingredient는 지연 로딩이라 트랜잭션 밖에서는 FK 값(id)만 읽는다
    private List<Long> ingredientIds(Long memberId) {
        return findFridges(memberId).stream()
                .map(fridge -> fridge.getIngredient().getId())
                .toList();
    }

    @TestConfiguration
    static class DltConsumerConfig {

        @Bean
        DltConsumer dltConsumer() {
            return new DltConsumer();
        }
    }

    static class DltConsumer {

        final BlockingQueue<ConsumerRecord<String, String>> records = new LinkedBlockingQueue<>();

        @KafkaListener(topics = KafkaTopics.PURCHASE_CONFIRMED + ".DLT", groupId = "test-group-purchase-confirmed-dlt")
        void receive(ConsumerRecord<String, String> record) {
            records.offer(record);
        }
    }
}
