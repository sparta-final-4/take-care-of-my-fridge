package org.example.takecareofmyfridgebe.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;

/**
 * 실제 Redis가 필요한 테스트가 상속하는 베이스 클래스.
 * 컨테이너는 JVM에서 한 번만 띄우고 모든 테스트 클래스가 공유한다. 종료는 JVM이 끝날 때 Testcontainers(Ryuk)가 맡는다.
 * {@code @Testcontainers}/{@code @Container}를 붙이지 않는다. JUnit 확장이 테스트 클래스마다 컨테이너를 멈춰,
 * 캐시된 Spring 컨텍스트가 멈춘 컨테이너를 가리키게 된다.
 */
public abstract class RedisContainerSupport {

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7").withExposedPorts(6379);

    static {
        REDIS.start();
    }
}
