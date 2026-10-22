package org.example.takecareofmyfridgebe.global.config;

import java.time.Duration;
import java.time.LocalDateTime;
import org.example.takecareofmyfridgebe.support.RedisContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = RedisConfig.class)
@ImportAutoConfiguration(DataRedisAutoConfiguration.class)
class RedisConfigTest extends RedisContainerSupport {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisMessageListenerContainer redisMessageListenerContainer;

    @Autowired
    RedisConfigTest(
            RedisTemplate<String, Object> redisTemplate,
            StringRedisTemplate stringRedisTemplate,
            RedisMessageListenerContainer redisMessageListenerContainer) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisMessageListenerContainer = redisMessageListenerContainer;
    }

    record SampleRedisDto(
            String id,
            String name,
            LocalDateTime createdAt
    ) {}

    @Test
    void saveAndGetWithTtl() {
        String key = "test:ttl:1";
        String value = "hello-redis";

        redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(60));

        Object retrieved = redisTemplate.opsForValue().get(key);
        assertThat(retrieved).isEqualTo(value);

        Long expire = redisTemplate.getExpire(key);
        assertThat(expire).isNotNull().isPositive();

        redisTemplate.delete(key);
    }

    @Test
    void saveAndGetLocalDateTime() {
        String key = "test:dto:1";
        LocalDateTime now = LocalDateTime.now();
        SampleRedisDto dto = new SampleRedisDto("user-1", "홍길동", now);

        redisTemplate.opsForValue().set(key, dto, Duration.ofSeconds(60));

        Object retrieved = redisTemplate.opsForValue().get(key);
        assertThat(retrieved).isNotNull().isInstanceOf(SampleRedisDto.class);

        SampleRedisDto casted = (SampleRedisDto) retrieved;
        assertThat(casted.id()).isEqualTo("user-1");
        assertThat(casted.name()).isEqualTo("홍길동");
        assertThat(casted.createdAt()).isEqualTo(now);

        redisTemplate.delete(key);
    }

    @Test
    void beanInjectionSuccess() {
        assertThat(stringRedisTemplate).isNotNull();
        assertThat(redisMessageListenerContainer).isNotNull();
    }
}
