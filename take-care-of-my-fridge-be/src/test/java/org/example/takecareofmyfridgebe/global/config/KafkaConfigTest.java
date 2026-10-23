package org.example.takecareofmyfridgebe.global.config;

import java.time.LocalDateTime;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.example.takecareofmyfridgebe.support.KafkaContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.mapping.DefaultJacksonJavaTypeMapper;
import org.springframework.messaging.support.MessageBuilder;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = KafkaConfig.class)
@Import(KafkaConfigTest.TestKafkaListenerConfig.class)
class KafkaConfigTest extends KafkaContainerSupport {

    static final String TOPIC_SUCCESS = "test-kafka-success";
    static final String TOPIC_RETRY_DLT = "test-kafka-retry-dlt";
    static final String TOPIC_CORRUPT_JSON = "test-kafka-corrupt-json";
    static final String TOPIC_UNTRUSTED = "test-kafka-untrusted";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTemplate<String, String> testRawStringKafkaTemplate;
    private final TestConsumer testConsumer;
    private final ApplicationContext applicationContext;

    @Autowired
    KafkaConfigTest(
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaTemplate<String, String> testRawStringKafkaTemplate,
            TestConsumer testConsumer,
            ApplicationContext applicationContext) {
        this.kafkaTemplate = kafkaTemplate;
        this.testRawStringKafkaTemplate = testRawStringKafkaTemplate;
        this.testConsumer = testConsumer;
        this.applicationContext = applicationContext;
    }

    record SampleKafkaDto(
            String id,
            String name,
            LocalDateTime createdAt
    ) {}

    /**
     * 비즈니스용 KafkaTemplate 빈 단 1개만 등록되었는지 확인.
     */
    @Test
    void singleKafkaTemplateBeanInjection() {
        assertThat(kafkaTemplate).isNotNull();
        assertThat(applicationContext.containsBean("dltKafkaTemplate")).isFalse();
    }

    /**
     * Kafka 전용 JsonMapper가 빈으로 등록되지 않아 Spring Boot 기본 API 자동 구성을 방해하지 않는지 확인.
     */
    @Test
    void noKafkaJsonMapperBeanInContext() {
        assertThat(applicationContext.containsBean("kafkaJsonMapper")).isFalse();
        assertThat(applicationContext.getBeansOfType(JsonMapper.class)).isEmpty();
    }

    /**
     * 정상 메시지(LocalDateTime 포함) 발행 및 역직렬화 수신 확인.
     */
    @Test
    void sendAndReceiveLocalDateTime() throws InterruptedException {
        LocalDateTime now = LocalDateTime.now();
        SampleKafkaDto payload = new SampleKafkaDto("order-1", "냉장고", now);

        kafkaTemplate.send(TOPIC_SUCCESS, "key-1", payload);

        SampleKafkaDto received = testConsumer.successQueue.poll(10, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.id()).isEqualTo("order-1");
        assertThat(received.name()).isEqualTo("냉장고");
        assertThat(received.createdAt()).isEqualTo(now);
    }

    /**
     * 리스너 예외 발생 시 2회 재시도(총 3회 호출) 후 DLT 라우팅 확인.
     */
    @Test
    void retryAndRouteToDltOnException() throws InterruptedException {
        LocalDateTime now = LocalDateTime.now();
        SampleKafkaDto payload = new SampleKafkaDto("error-1", "실패테스트", now);

        kafkaTemplate.send(TOPIC_RETRY_DLT, "key-error", payload);

        ConsumerRecord<String, String> dltRecord = testConsumer.retryDltQueue.poll(15, TimeUnit.SECONDS);
        assertThat(dltRecord).isNotNull();
        assertThat(dltRecord.topic()).isEqualTo(TOPIC_RETRY_DLT + ".DLT");
        assertThat(dltRecord.key()).isEqualTo("key-error");
        assertThat(dltRecord.value()).contains("실패테스트");
        assertThat(testConsumer.retryAttemptCount.get()).isEqualTo(3);
    }

    /**
     * 깨진 JSON 수신 시 재시도 없이 즉시 DLT 라우팅 및 문자열 원형 보존 확인.
     */
    @Test
    void corruptJsonRouteToDltImmediately() throws InterruptedException {
        String brokenJson = "{ \"id\": \"corrupt\", invalid_json... }";

        // Raw String 메시지를 발행하여 변환 실패 유도
        testRawStringKafkaTemplate.send(TOPIC_CORRUPT_JSON, "key-corrupt", brokenJson);

        ConsumerRecord<String, String> dltRecord = testConsumer.corruptDltQueue.poll(10, TimeUnit.SECONDS);
        assertThat(dltRecord).isNotNull();
        assertThat(dltRecord.topic()).isEqualTo(TOPIC_CORRUPT_JSON + ".DLT");
        assertThat(dltRecord.key()).isEqualTo("key-corrupt");
        // DLT 전용 ProducerFactory(StringSerializer) 덕분에 감싸지지 않고 원래 보낸 문자열과 일치
        assertThat(dltRecord.value()).isEqualTo(brokenJson);
        assertThat(testConsumer.corruptInvocationCount.get()).isZero();
    }

    /**
     * 허용되지 않은 타입 헤더가 있어도 INFERRED 설정에 의해 리스너 파라미터 타입으로 정상 수신 확인.
     */
    @Test
    void untrustedTypeHeaderIgnoredAndInferred() throws InterruptedException {
        LocalDateTime now = LocalDateTime.now();
        SampleKafkaDto payload = new SampleKafkaDto("order-sec-1", "보안테스트", now);

        // 보낸 쪽에서 임의로 com.untrusted.MaliciousDto 헤더를 붙여서 전송
        var message = MessageBuilder.withPayload(payload)
                .setHeader(KafkaHeaders.TOPIC, TOPIC_UNTRUSTED)
                .setHeader(KafkaHeaders.KEY, "key-untrusted")
                .setHeader(DefaultJacksonJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME, "com.untrusted.MaliciousDto")
                .build();

        kafkaTemplate.send(message);

        // INFERRED 설정에 의해 헤더가 무시되고 리스너 메서드의 파라미터 타입인 SampleKafkaDto로 정상 수신됨을 검증
        SampleKafkaDto received = testConsumer.untrustedQueue.poll(10, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.id()).isEqualTo("order-sec-1");
        assertThat(received.name()).isEqualTo("보안테스트");
        assertThat(received.createdAt()).isEqualTo(now);
        assertThat(received).isInstanceOf(SampleKafkaDto.class);
        // DLT로 빠지지 않음 확인
        assertThat(testConsumer.untrustedDltQueue.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @TestConfiguration
    static class TestKafkaListenerConfig {

        @Bean
        public KafkaTemplate<String, String> testRawStringKafkaTemplate(
                ProducerFactory<String, String> dltProducerFactory) {
            return new KafkaTemplate<>(dltProducerFactory);
        }

        @Bean
        public TestConsumer testConsumer() {
            return new TestConsumer();
        }

        @Bean
        public NewTopic testSuccessTopic() {
            return TopicBuilder.name(TOPIC_SUCCESS).partitions(1).replicas(1).build();
        }

        @Bean
        public NewTopic testRetryDltTopic() {
            return TopicBuilder.name(TOPIC_RETRY_DLT).partitions(1).replicas(1).build();
        }

        @Bean
        public NewTopic testRetryDltTopicDLT() {
            return TopicBuilder.name(TOPIC_RETRY_DLT + ".DLT").partitions(1).replicas(1).build();
        }

        @Bean
        public NewTopic testCorruptJsonTopic() {
            return TopicBuilder.name(TOPIC_CORRUPT_JSON).partitions(1).replicas(1).build();
        }

        @Bean
        public NewTopic testCorruptJsonTopicDLT() {
            return TopicBuilder.name(TOPIC_CORRUPT_JSON + ".DLT").partitions(1).replicas(1).build();
        }

        @Bean
        public NewTopic testUntrustedTopic() {
            return TopicBuilder.name(TOPIC_UNTRUSTED).partitions(1).replicas(1).build();
        }

        @Bean
        public NewTopic testUntrustedTopicDLT() {
            return TopicBuilder.name(TOPIC_UNTRUSTED + ".DLT").partitions(1).replicas(1).build();
        }
    }

    static class TestConsumer {
        final BlockingQueue<SampleKafkaDto> successQueue = new LinkedBlockingQueue<>();
        final BlockingQueue<SampleKafkaDto> untrustedQueue = new LinkedBlockingQueue<>();
        final BlockingQueue<ConsumerRecord<String, String>> retryDltQueue = new LinkedBlockingQueue<>();
        final BlockingQueue<ConsumerRecord<String, String>> corruptDltQueue = new LinkedBlockingQueue<>();
        final BlockingQueue<ConsumerRecord<String, String>> untrustedDltQueue = new LinkedBlockingQueue<>();
        final AtomicInteger retryAttemptCount = new AtomicInteger();
        final AtomicInteger corruptInvocationCount = new AtomicInteger();

        @KafkaListener(topics = TOPIC_SUCCESS, groupId = "test-group-success")
        public void handleSuccess(SampleKafkaDto message) {
            successQueue.offer(message);
        }

        @KafkaListener(topics = TOPIC_RETRY_DLT, groupId = "test-group-retry")
        public void handleRetry(SampleKafkaDto message) {
            retryAttemptCount.incrementAndGet();
            throw new RuntimeException("Intentional listener test failure");
        }

        @KafkaListener(topics = TOPIC_CORRUPT_JSON, groupId = "test-group-corrupt")
        public void handleCorrupt(SampleKafkaDto message) {
            corruptInvocationCount.incrementAndGet();
        }

        @KafkaListener(topics = TOPIC_UNTRUSTED, groupId = "test-group-untrusted")
        public void handleUntrusted(SampleKafkaDto message) {
            untrustedQueue.offer(message);
        }

        @KafkaListener(topics = TOPIC_RETRY_DLT + ".DLT", groupId = "test-group-retry-dlt")
        public void handleRetryDlt(ConsumerRecord<String, String> record) {
            retryDltQueue.offer(record);
        }

        @KafkaListener(topics = TOPIC_CORRUPT_JSON + ".DLT", groupId = "test-group-corrupt-dlt")
        public void handleCorruptDlt(ConsumerRecord<String, String> record) {
            corruptDltQueue.offer(record);
        }

        @KafkaListener(topics = TOPIC_UNTRUSTED + ".DLT", groupId = "test-group-untrusted-dlt")
        public void handleUntrustedDlt(ConsumerRecord<String, String> record) {
            untrustedDltQueue.offer(record);
        }
    }
}
