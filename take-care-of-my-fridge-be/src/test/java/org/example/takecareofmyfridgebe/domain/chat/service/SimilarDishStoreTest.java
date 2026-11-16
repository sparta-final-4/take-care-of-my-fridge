package org.example.takecareofmyfridgebe.domain.chat.service;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.DishListKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimilarDishStoreTest {

    private static final Long MEMBER_ID = 1L;
    private static final Duration TTL = Duration.ofDays(7);

    private ValueOperations<String, String> values;
    private SimilarDishStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(values);
        store = new SimilarDishStore(template, new ChatProperties(30, Duration.ofMinutes(30), Duration.ofSeconds(150), TTL));
    }

    @Test
    @DisplayName("요리 이름(공백 제거·소문자) 키에 저장된 비슷한 요리를 읽는다")
    void findsCachedDishes() {
        when(values.get("chat:similar-dishes:v3:나가사키짬뽕")).thenReturn("[\"짬뽕\",\"짬뽕밥\"]");

        assertThat(store.find(MEMBER_ID, DishListKind.SIMILAR, "나가사키 짬뽕")).contains(List.of("짬뽕", "짬뽕밥"));
    }

    @Test
    @DisplayName("저장된 값이 없거나 빈 목록이면 비어 있다")
    void returnsEmpty_whenNotCachedOrEmptyList() {
        assertThat(store.find(MEMBER_ID, DishListKind.SIMILAR, "김피탕")).isEmpty();

        when(values.get("chat:similar-dishes:v3:김피탕")).thenReturn("[]");
        assertThat(store.find(MEMBER_ID, DishListKind.SIMILAR, "김피탕")).isEmpty();
    }

    @Test
    @DisplayName("비슷한 요리를 유지 시간과 함께 저장하고, 영문 요리 이름은 소문자 키로 저장한다")
    void savesWithTtl_inLowercaseKey() {
        store.save(MEMBER_ID, DishListKind.SIMILAR, "Fish Taco", List.of("타코", "부리토"));

        verify(values).set("chat:similar-dishes:v3:fishtaco", "[\"타코\",\"부리토\"]", TTL);
    }

    @Test
    @DisplayName("넓은 요청 요리는 chat:broad-dishes:v1: 키(요청 표현, 공백 제거·소문자)에 같은 유지 시간으로 두고 읽는다")
    void savesAndFindsBroadDishes_inBroadKey() {
        store.save(MEMBER_ID, DishListKind.BROAD, "간단한 한 끼 Menu", List.of("김치볶음밥", "비빔국수"));
        when(values.get("chat:broad-dishes:v1:간단한한끼menu")).thenReturn("[\"김치볶음밥\",\"비빔국수\"]");

        verify(values).set("chat:broad-dishes:v1:간단한한끼menu", "[\"김치볶음밥\",\"비빔국수\"]", TTL);
        assertThat(store.find(MEMBER_ID, DishListKind.BROAD, "간단한 한 끼 Menu")).contains(List.of("김치볶음밥", "비빔국수"));
        assertThat(store.find(MEMBER_ID, DishListKind.SIMILAR, "간단한 한 끼 Menu")).isEmpty();
    }

    @Test
    @DisplayName("빈 목록이나 빈 요리 이름은 저장하지 않는다")
    void skipsSaving_whenEmpty() {
        store.save(MEMBER_ID, DishListKind.SIMILAR, "김피탕", List.of());
        store.save(MEMBER_ID, DishListKind.SIMILAR, " ", List.of("탕수육"));

        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("Redis 장애면 읽기는 비어 있고 저장은 예외를 내지 않는다")
    void ignoresRedisFailure() {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("redis down"));
        doThrow(new RedisConnectionFailureException("redis down")).when(values).set(anyString(), anyString(), any(Duration.class));

        assertThat(store.find(MEMBER_ID, DishListKind.SIMILAR, "김피탕")).isEmpty();
        assertThatCode(() -> store.save(MEMBER_ID, DishListKind.SIMILAR, "김피탕", List.of("탕수육"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("저장된 값이 깨졌으면 예외를 내지 않고 비어 있다")
    void returnsEmpty_whenCachedValueIsBroken() {
        when(values.get(anyString())).thenReturn("[\"탕수육\"");

        assertThat(store.find(MEMBER_ID, DishListKind.SIMILAR, "김피탕")).isEmpty();
    }
}
