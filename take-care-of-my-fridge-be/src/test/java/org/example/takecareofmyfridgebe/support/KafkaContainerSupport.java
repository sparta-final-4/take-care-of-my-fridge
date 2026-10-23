package org.example.takecareofmyfridgebe.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

/**
 * 실제 Kafka가 필요한 테스트가 상속하는 베이스 클래스.
 * 컨테이너는 JVM에서 한 번만 띄우고 모든 테스트 클래스가 공유한다. 종료는 JVM이 끝날 때 Testcontainers(Ryuk)가 맡는다.
 * {@code @Testcontainers}/{@code @Container}를 붙이지 않는다. JUnit 확장이 테스트 클래스마다 컨테이너를 멈춰,
 * 캐시된 Spring 컨텍스트가 멈춘 컨테이너를 가리키게 된다.
 */
public abstract class KafkaContainerSupport {

    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}
