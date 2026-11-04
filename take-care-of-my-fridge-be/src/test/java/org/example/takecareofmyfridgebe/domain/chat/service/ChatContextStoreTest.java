package org.example.takecareofmyfridgebe.domain.chat.service;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.LastRecommendation;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.example.takecareofmyfridgebe.global.config.RedisConfig;
import org.example.takecareofmyfridgebe.support.RedisContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {RedisConfig.class, ChatContextStore.class})
@ImportAutoConfiguration(DataRedisAutoConfiguration.class)
@EnableConfigurationProperties(ChatProperties.class)
class ChatContextStoreTest extends RedisContainerSupport {

    private static final Long SESSION_ID = 601L;
    private static final String KEY = "chat:context:" + SESSION_ID;

    @Autowired
    private ChatContextStore chatContextStore;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void clearKeys() {
        Set<String> keys = stringRedisTemplate.keys("chat:context:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("직전 추천 조건·카드를 chat:context:{sessionId}에 30분 동안 두고 그대로 읽는다")
    void savesAndFindsLastRecommendation_withSessionTimeout() {
        RecipeQuery query = new RecipeQuery(1L, List.of(21L), List.of(Taste.SPICY), List.of("국"), 30, List.of(15L), true,
                List.of("김치찌개"), "김치찌개", "얼큰한 국물").withAvoidTastes(List.of(Taste.SWEET));

        chatContextStore.saveRecommendation(SESSION_ID, query, List.of(10L, 20L, 30L));

        LastRecommendation last = chatContextStore.find(SESSION_ID).orElseThrow();
        assertThat(last.recipeIds()).containsExactly(10L, 20L, 30L);
        assertThat(last.query().toRecipeQuery(1L)).isEqualTo(query);
        assertThat(stringRedisTemplate.getExpire(KEY, TimeUnit.SECONDS)).isPositive().isLessThanOrEqualTo(1_800L);
    }

    @Test
    @DisplayName("유지 시간 늘리기는 직전 추천은 그대로 두고 유지 시간만 다시 30분으로 늘리며, 없으면 아무것도 만들지 않는다")
    void extendsTtlOnly_whenTouched() {
        chatContextStore.saveRecommendation(SESSION_ID, RecipeQuery.of(1L, List.of()), List.of(10L));
        stringRedisTemplate.expire(KEY, Duration.ofSeconds(5));

        chatContextStore.touch(SESSION_ID);
        chatContextStore.touch(999L);

        assertThat(stringRedisTemplate.getExpire(KEY, TimeUnit.SECONDS)).isGreaterThan(5L);
        assertThat(chatContextStore.find(SESSION_ID).orElseThrow().recipeIds()).containsExactly(10L);
        assertThat(stringRedisTemplate.hasKey("chat:context:999")).isFalse();
    }

    @Test
    @DisplayName("피할 맛이 없던 예전 값도 피할 맛 없이 읽는다")
    void readsOldValueWithoutAvoidTastes() {
        stringRedisTemplate.opsForValue().set(KEY, "{\"query\":{\"selectedIngredientIds\":[],\"wantedTastes\":[\"SPICY\"],"
                + "\"moodCategories\":[],\"maxMinutes\":null,\"excludeIngredientIds\":[],\"useSelectedOnly\":false,"
                + "\"keywords\":[],\"keywordText\":null,\"preferenceText\":null},\"recipeIds\":[10]}");

        RecipeQuery query = chatContextStore.find(SESSION_ID).orElseThrow().query().toRecipeQuery(1L);

        assertThat(query.wantedTastes()).containsExactly(Taste.SPICY);
        assertThat(query.avoidTastes()).isEmpty();
    }

    @Test
    @DisplayName("직전 추천이 없거나 값이 깨졌으면 빈 값으로 읽는다")
    void findsEmpty_whenMissingOrBroken() {
        assertThat(chatContextStore.find(SESSION_ID)).isEmpty();

        stringRedisTemplate.opsForValue().set(KEY, "{깨진");

        assertThat(chatContextStore.find(SESSION_ID)).isEmpty();
    }

    @Test
    @DisplayName("Redis 장애면 에러로 끝내지 않고 읽기는 빈 값, 저장·유지 시간 늘리기는 넘어간다")
    void ignoresRedisFailure() {
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        when(broken.opsForValue()).thenThrow(new IllegalStateException("Redis 끊김"));
        when(broken.expire(KEY, Duration.ofMinutes(30))).thenThrow(new IllegalStateException("Redis 끊김"));
        ChatContextStore store = new ChatContextStore(broken, new ChatProperties(30, Duration.ofMinutes(30), Duration.ofSeconds(150)));

        store.saveRecommendation(SESSION_ID, RecipeQuery.of(1L, List.of()), List.of(10L));
        store.touch(SESSION_ID);

        assertThat(store.find(SESSION_ID)).isEmpty();
    }
}
