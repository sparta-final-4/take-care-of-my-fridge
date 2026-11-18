package org.example.takecareofmyfridgebe.domain.chat.service;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.DishListKind;
import org.example.takecareofmyfridgebe.domain.chat.dto.SimilarDishes;
import org.example.takecareofmyfridgebe.domain.chat.text.ChatTexts;
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
    @DisplayName("요리 이름(공백 제거·소문자)의 해시 키에 저장된 정식 이름과 비슷한 요리를 읽는다")
    void findsCachedDishes() {
        when(values.get(similarKey("나가사키짬뽕"))).thenReturn("{\"formalName\":null,\"dishes\":[\"짬뽕\",\"짬뽕밥\"]}");
        when(values.get(similarKey("김피탕"))).thenReturn("{\"formalName\":\"김치피자탕수육\",\"dishes\":[\"탕수육\"]}");

        assertThat(store.find(MEMBER_ID, DishListKind.SIMILAR, "나가사키 짬뽕")).contains(SimilarDishes.of(List.of("짬뽕", "짬뽕밥")));
        assertThat(store.find(MEMBER_ID, DishListKind.SIMILAR, "김피탕"))
                .contains(new SimilarDishes("김치피자탕수육", List.of("탕수육")));
    }

    @Test
    @DisplayName("저장된 값이 없거나 정식 이름·요리 이름이 모두 비었거나 예전 모양(목록)이면 비어 있다")
    void returnsEmpty_whenNotCachedOrEmptyList() {
        assertThat(store.find(MEMBER_ID, DishListKind.SIMILAR, "김피탕")).isEmpty();

        when(values.get(similarKey("김피탕"))).thenReturn("{\"formalName\":null,\"dishes\":[]}");
        assertThat(store.find(MEMBER_ID, DishListKind.SIMILAR, "김피탕")).isEmpty();

        when(values.get(similarKey("김피탕"))).thenReturn("[\"탕수육\"]");
        assertThat(store.find(MEMBER_ID, DishListKind.SIMILAR, "김피탕")).isEmpty();
    }

    @Test
    @DisplayName("비슷한 요리를 유지 시간과 함께 저장하고, 영문 요리 이름은 소문자 키로 저장한다")
    void savesWithTtl_inLowercaseKey() {
        store.save(MEMBER_ID, DishListKind.SIMILAR, "Fish Taco", SimilarDishes.of(List.of("타코", "부리토")));

        verify(values).set(similarKey("fishtaco"), "{\"formalName\":null,\"dishes\":[\"타코\",\"부리토\"]}", TTL);
    }

    @Test
    @DisplayName("넓은 요청 요리는 chat:broad-dishes:v3: 키(요청 표현을 공백 제거·소문자로 한 해시)에 같은 유지 시간으로 두고 읽는다")
    void savesAndFindsBroadDishes_inBroadKey() {
        String json = "{\"formalName\":null,\"dishes\":[\"김치볶음밥\",\"비빔국수\"]}";
        store.save(MEMBER_ID, DishListKind.BROAD, "간단한 한 끼 Menu", SimilarDishes.of(List.of("김치볶음밥", "비빔국수")));
        when(values.get(broadKey("간단한한끼menu"))).thenReturn(json);

        verify(values).set(broadKey("간단한한끼menu"), json, TTL);
        assertThat(store.find(MEMBER_ID, DishListKind.BROAD, "간단한 한 끼 Menu"))
                .contains(SimilarDishes.of(List.of("김치볶음밥", "비빔국수")));
        assertThat(store.find(MEMBER_ID, DishListKind.SIMILAR, "간단한 한 끼 Menu")).isEmpty();
    }

    @Test
    @DisplayName("빈 목록이나 빈 요리 이름은 저장하지 않는다")
    void skipsSaving_whenEmpty() {
        store.save(MEMBER_ID, DishListKind.SIMILAR, "김피탕", SimilarDishes.NONE);
        store.save(MEMBER_ID, DishListKind.SIMILAR, " ", SimilarDishes.of(List.of("탕수육")));

        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("Redis 장애면 읽기는 비어 있고 저장은 예외를 내지 않는다")
    void ignoresRedisFailure() {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("redis down"));
        doThrow(new RedisConnectionFailureException("redis down")).when(values).set(anyString(), anyString(), any(Duration.class));

        assertThat(store.find(MEMBER_ID, DishListKind.SIMILAR, "김피탕")).isEmpty();
        assertThatCode(() -> store.save(MEMBER_ID, DishListKind.SIMILAR, "김피탕", SimilarDishes.of(List.of("탕수육")))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("저장된 값이 깨졌으면 예외를 내지 않고 비어 있다")
    void returnsEmpty_whenCachedValueIsBroken() {
        when(values.get(anyString())).thenReturn("[\"탕수육\"");

        assertThat(store.find(MEMBER_ID, DishListKind.SIMILAR, "김피탕")).isEmpty();
    }

    @Test
    @DisplayName("넓은 요청 캐시 키에는 메시지 원문이 없고, 띄어쓰기·대소문자만 다른 같은 문장은 같은 키가 된다")
    void buildsBroadKeyWithoutOriginalText_whenMessageIsWholeSentence() {
        String message = "오늘 회사에서 너무 힘들었는데 위로가 되는 저녁 메뉴";

        String key = SimilarDishStore.key(DishListKind.BROAD, message);

        assertThat(key).startsWith("chat:broad-dishes:v3:").doesNotContain("회사").doesNotContain("저녁");
        assertThat(key.substring("chat:broad-dishes:v3:".length())).hasSize(16).matches("[0-9a-f]+");
        assertThat(SimilarDishStore.key(DishListKind.BROAD, "오늘 회사에서  너무 힘들었는데 위로가 되는 저녁메뉴")).isEqualTo(key);
        assertThat(SimilarDishStore.key(DishListKind.BROAD, "술안주")).isNotEqualTo(key);
    }

    private static String similarKey(String compact) {
        return "chat:similar-dishes:v5:" + ChatTexts.shortHash(compact);
    }

    private static String broadKey(String compact) {
        return "chat:broad-dishes:v3:" + ChatTexts.shortHash(compact);
    }
}
