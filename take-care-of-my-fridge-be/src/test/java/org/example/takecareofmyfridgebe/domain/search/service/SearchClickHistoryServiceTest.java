package org.example.takecareofmyfridgebe.domain.search.service;

import org.example.takecareofmyfridgebe.domain.search.dto.SearchClickRequest;
import org.example.takecareofmyfridgebe.domain.search.entity.SearchClickHistory;
import org.example.takecareofmyfridgebe.domain.search.repository.SearchClickHistoryRepository;
import org.example.takecareofmyfridgebe.global.config.RedisConfig;
import org.example.takecareofmyfridgebe.support.RedisContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 실제 Redis로 SET NX·INCR 동작을 확인한다
@SpringBootTest(classes = RedisConfig.class)
@ImportAutoConfiguration(DataRedisAutoConfiguration.class)
class SearchClickHistoryServiceTest extends RedisContainerSupport {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final SearchClickHistoryRepository repository = mock(SearchClickHistoryRepository.class);

    private SearchClickHistoryService service;

    @BeforeEach
    void setUp() {
        Set<String> keys = redisTemplate.keys("search:click-*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        service = new SearchClickHistoryService(repository, redisTemplate);
    }

    @Test
    @DisplayName("클릭을 상품 번호·회원 번호·앞뒤 공백을 뺀 검색어로 저장한다")
    void record_savesClick() {
        service.record(new SearchClickRequest(10L, " 계란 "), 7L);

        SearchClickHistory saved = captureSaved();
        assertThat(saved.getProductId()).isEqualTo(10L);
        assertThat(saved.getMemberId()).isEqualTo(7L);
        assertThat(saved.getKeyword()).isEqualTo("계란");
    }

    @Test
    @DisplayName("비로그인 클릭은 회원 번호 없이 저장한다")
    void record_savesWithoutMember_whenAnonymous() {
        service.record(new SearchClickRequest(10L, "계란"), null);

        assertThat(captureSaved().getMemberId()).isNull();
    }

    @Test
    @DisplayName("같은 회원이 같은 상품·검색어를 창 안에서 다시 누르면 한 번만 저장하고 창 길이만큼 TTL을 건다")
    void record_savesOnce_whenMemberRepeatsWithinWindow() {
        // When
        service.record(new SearchClickRequest(10L, "계란"), 7L);
        service.record(new SearchClickRequest(10L, " 계란 "), 7L);
        service.record(new SearchClickRequest(10L, "계란"), 7L);

        // Then
        verify(repository, times(1)).save(any());
        Long ttl = redisTemplate.getExpire(SearchClickHistoryService.DEDUP_KEY_PREFIX + "7:10:계란");
        assertThat(ttl).isNotNull().isPositive()
                .isLessThanOrEqualTo(SearchClickHistoryService.DEDUP_WINDOW.toSeconds());
    }

    @Test
    @DisplayName("창이 지나 키가 사라지면 같은 클릭을 다시 저장한다")
    void record_savesAgain_whenWindowExpires() {
        // Given
        service.record(new SearchClickRequest(10L, "계란"), 7L);

        // When: TTL 만료를 키 삭제로 흉내 낸다
        redisTemplate.delete(SearchClickHistoryService.DEDUP_KEY_PREFIX + "7:10:계란");
        service.record(new SearchClickRequest(10L, "계란"), 7L);

        // Then
        verify(repository, times(2)).save(any());
    }

    @Test
    @DisplayName("회원·상품·검색어 중 하나라도 다르면 따로 저장한다")
    void record_savesSeparately_whenMemberProductOrKeywordDiffers() {
        // When
        service.record(new SearchClickRequest(10L, "계란"), 7L);
        service.record(new SearchClickRequest(10L, "달걀"), 7L);
        service.record(new SearchClickRequest(11L, "계란"), 7L);
        service.record(new SearchClickRequest(10L, "계란"), 8L);

        // Then
        verify(repository, times(4)).save(any());
    }

    @Test
    @DisplayName("비로그인 클릭은 상품별로 1분 상한까지만 저장한다")
    void record_stopsAtAnonymousLimit() {
        // When
        for (int i = 0; i < SearchClickHistoryService.ANONYMOUS_LIMIT_PER_MINUTE + 5; i++) {
            service.record(new SearchClickRequest(10L, "계란"), null);
        }
        service.record(new SearchClickRequest(11L, "계란"), null);

        // Then: 상한만큼 + 다른 상품 1건
        verify(repository, times(SearchClickHistoryService.ANONYMOUS_LIMIT_PER_MINUTE + 1)).save(any());
    }

    @Test
    @DisplayName("다음 분이 되어 카운터가 바뀌면 비로그인 클릭을 다시 저장한다")
    void record_savesAgain_whenAnonymousMinuteChanges() {
        // Given
        for (int i = 0; i < SearchClickHistoryService.ANONYMOUS_LIMIT_PER_MINUTE; i++) {
            service.record(new SearchClickRequest(10L, "계란"), null);
        }

        // When: 분이 바뀐 상황을 카운터 키 삭제로 흉내 낸다
        Set<String> keys = redisTemplate.keys(SearchClickHistoryService.ANONYMOUS_LIMIT_KEY_PREFIX + "10:*");
        redisTemplate.delete(keys);
        service.record(new SearchClickRequest(10L, "계란"), null);

        // Then
        verify(repository, times(SearchClickHistoryService.ANONYMOUS_LIMIT_PER_MINUTE + 1)).save(any());
    }

    @Test
    @DisplayName("Redis 장애면 거르지 않고 저장한다")
    void record_saves_whenRedisFails() {
        // Given
        StringRedisTemplate brokenRedis = mock(StringRedisTemplate.class);
        when(brokenRedis.opsForValue()).thenThrow(new QueryTimeoutException("Redis 연결 실패"));
        SearchClickHistoryService serviceWithBrokenRedis = new SearchClickHistoryService(repository, brokenRedis);

        // When
        serviceWithBrokenRedis.record(new SearchClickRequest(10L, "계란"), 7L);
        serviceWithBrokenRedis.record(new SearchClickRequest(10L, "계란"), null);

        // Then
        verify(repository, times(2)).save(any());
    }

    private SearchClickHistory captureSaved() {
        ArgumentCaptor<SearchClickHistory> captor = ArgumentCaptor.forClass(SearchClickHistory.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
