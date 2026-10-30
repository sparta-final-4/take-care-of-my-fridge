package org.example.takecareofmyfridgebe.domain.order.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.example.takecareofmyfridgebe.global.config.KafkaConfig;
import org.example.takecareofmyfridgebe.global.config.KafkaTopics;
import org.example.takecareofmyfridgebe.support.KafkaContainerSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 받는 쪽이 보게 될 그대로(키와 JSON 원문) 구매 확정 토픽에 도착하는지 확인한다
@SpringBootTest(classes = {KafkaConfig.class, OrderConfirmedEventListener.class})
@Import(OrderConfirmedEventListenerTest.TestConsumerConfig.class)
class OrderConfirmedEventListenerTest extends KafkaContainerSupport {

    private final OrderConfirmedEventListener listener;
    private final TestConsumer testConsumer;

    @Autowired
    OrderConfirmedEventListenerTest(OrderConfirmedEventListener listener, TestConsumer testConsumer) {
        this.listener = listener;
        this.testConsumer = testConsumer;
    }

    @Test
    @DisplayName("구매 확정 이벤트를 주문 ID를 키로 구매 확정 토픽에 보내고 주문 상품별 확정 수량을 싣는다")
    void onOrderConfirmed_sendsToPurchaseConfirmedTopic() throws InterruptedException {
        LocalDateTime confirmedAt = LocalDateTime.of(2026, 10, 30, 14, 30, 5);
        OrderConfirmedEvent event = new OrderConfirmedEvent(100L, 7L, confirmedAt, List.of(
            new OrderConfirmedEvent.Item(11L, 3L, "대파", 2),
            new OrderConfirmedEvent.Item(12L, 4L, "두부", 1)));

        listener.onOrderConfirmed(event);

        ConsumerRecord<String, String> record = testConsumer.records.poll(10, TimeUnit.SECONDS);
        assertThat(record).isNotNull();
        assertThat(record.key()).isEqualTo("100");
        JsonNode body = JsonMapper.builder().build().readTree(record.value());
        assertThat(body.get("orderId").asLong()).isEqualTo(100L);
        assertThat(body.get("memberId").asLong()).isEqualTo(7L);
        assertThat(body.get("confirmedAt").asString()).isEqualTo("2026-10-30T14:30:05");
        JsonNode items = body.get("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("orderItemId").asLong()).isEqualTo(11L);
        assertThat(items.get(0).get("productId").asLong()).isEqualTo(3L);
        assertThat(items.get(0).get("productName").asString()).isEqualTo("대파");
        assertThat(items.get(0).get("confirmedQuantity").asInt()).isEqualTo(2);
        assertThat(items.get(1).get("orderItemId").asLong()).isEqualTo(12L);
    }

    @TestConfiguration
    static class TestConsumerConfig {

        @Bean
        TestConsumer testConsumer() {
            return new TestConsumer();
        }
    }

    static class TestConsumer {

        final BlockingQueue<ConsumerRecord<String, String>> records = new LinkedBlockingQueue<>();

        @KafkaListener(topics = KafkaTopics.PURCHASE_CONFIRMED, groupId = "test-group-order-confirmed")
        void receive(ConsumerRecord<String, String> record) {
            records.offer(record);
        }
    }
}
