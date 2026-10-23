package org.example.takecareofmyfridgebe.global.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.ConversionException;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.converter.StringJacksonJsonMessageConverter;
import org.springframework.kafka.support.mapping.DefaultJacksonJavaTypeMapper;
import org.springframework.kafka.support.mapping.JacksonJavaTypeMapper;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kafka 인프라 공통 설정 클래스.
 *
 */
@EnableKafka
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    private JsonMapper createKafkaJsonMapper() {
        return JsonMapper.builder()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    // ----------------------------------------------------
    // Producer 설정 (비즈니스 이벤트 발행용 유일한 KafkaTemplate 빈)
    // ----------------------------------------------------

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.RETRIES_CONFIG, 3);

        JacksonJsonSerializer<Object> valueSerializer = new JacksonJsonSerializer<>(createKafkaJsonMapper());
        valueSerializer.setAddTypeInfo(false);

        return new DefaultKafkaProducerFactory<>(
                props,
                new StringSerializer(),
                valueSerializer
        );
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    // ----------------------------------------------------
    // DLT 전용 ProducerFactory 빈
    // (서버 종료 시 Spring이 destroy()를 호출하여 In-flight 메시지를 안전하게 flush)
    // ----------------------------------------------------

    @Bean
    public ProducerFactory<String, String> dltProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.RETRIES_CONFIG, 3);

        return new DefaultKafkaProducerFactory<>(props);
    }

    // ----------------------------------------------------
    // Consumer 설정
    // ----------------------------------------------------

    /**
     * group.id 기본값을 두지 않는다. @KafkaListener마다 groupId를 반드시 지정한다 (예: groupId = "fridge").
     * 같은 groupId를 쓰는 리스너는 메시지를 나눠 받으므로, 도메인마다 다른 groupId를 쓴다.
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * INFERRED: 메시지 헤더의 타입 정보를 무시하고 @KafkaListener 파라미터 타입으로만 변환한다 (보낸 쪽이 타입을 바꿀 수 없게).
     */
    @Bean
    public RecordMessageConverter kafkaRecordMessageConverter() {
        StringJacksonJsonMessageConverter converter = new StringJacksonJsonMessageConverter(createKafkaJsonMapper());

        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();
        typeMapper.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);
        // INFERRED에서는 쓰이지 않는 방어적 설정. 나중에 타입 결정 방식이 바뀌어도 프로젝트 패키지만 허용하도록 남겨둔다
        typeMapper.addTrustedPackages("org.example.takecareofmyfridgebe");

        converter.setTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler(ProducerFactory<String, String> dltProducerFactory) {
        // DLT 전용 KafkaTemplate은 빈으로 등록하지 않고 에러 핸들러 내부에서만 생성하여 주입 충돌 방지
        KafkaTemplate<String, String> dltKafkaTemplate = new KafkaTemplate<>(dltProducerFactory);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dltKafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1)
        );
        FixedBackOff backOff = new FixedBackOff(1000L, 2L);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        // 형식이 깨진 JSON 또는 변환 불가능한 메시지는 재시도 없이 바로 DLT로 전송
        errorHandler.addNotRetryableExceptions(
                ConversionException.class,
                MessageConversionException.class
        );
        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            RecordMessageConverter kafkaRecordMessageConverter,
            CommonErrorHandler kafkaErrorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setRecordMessageConverter(kafkaRecordMessageConverter);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

        return factory;
    }

    // ----------------------------------------------------
    // 로컬 기본 토픽 생성 (NewTopic 빈)
    // ----------------------------------------------------

    @Bean
    public NewTopic purchaseConfirmedTopic() {
        return TopicBuilder.name(KafkaTopics.PURCHASE_CONFIRMED)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic purchaseConfirmedDltTopic() {
        return TopicBuilder.name(KafkaTopics.PURCHASE_CONFIRMED + ".DLT")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic notificationRequestedTopic() {
        return TopicBuilder.name(KafkaTopics.NOTIFICATION_REQUESTED)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic notificationRequestedDltTopic() {
        return TopicBuilder.name(KafkaTopics.NOTIFICATION_REQUESTED + ".DLT")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
