package org.example.takecareofmyfridgebe.domain.search.service;

import org.example.takecareofmyfridgebe.global.config.RedisConfig;
import org.example.takecareofmyfridgebe.support.RedisContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

// 실제 Redis로 ZSET 집계와 회원별 SET 중복 확인을 확인한다
@SpringBootTest(classes = RedisConfig.class)
@ImportAutoConfiguration(DataRedisAutoConfiguration.class)
class SearchPopularKeywordServiceTest extends RedisContainerSupport {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        Set<String> keys = redisTemplate.keys("search:popular*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("여러 회원이 검색한 횟수 순으로 인기 검색어를 돌려준다")
    void returnsKeywordsByCount() {
        SearchPopularKeywordService service = serviceAt("2026-11-04T10:00:00Z");
        searchBy(service, "김치찌개", 3);
        searchBy(service, "두부", 2);

        assertThat(service.getTopKeywords().keywords()).containsExactly("김치찌개", "두부");
    }

    @Test
    @DisplayName("같은 회원이 같은 검색어를 하루에 여러 번 검색하면 한 번만 센다")
    void countsOncePerMemberPerDay() {
        SearchPopularKeywordService service = serviceAt("2026-11-04T10:00:00Z");
        service.record(1L, "김치");
        service.record(1L, "김치");
        service.record(1L, "김치");

        assertThat(service.getTopKeywords().keywords()).isEmpty();
        assertThat(redisTemplate.opsForZSet().score("search:popular:20261104", "김치")).isEqualTo(1.0);
        assertThat(redisTemplate.opsForSet().members("search:popular-seen:20261104:1")).containsExactly("김치");
    }

    @Test
    @DisplayName("한 회원이 다른 검색어를 검색하면 각각 세고 그날 검색어를 키 하나에 모은다")
    void countsEachKeyword_whenSameMemberSearchesDifferentKeywords() {
        SearchPopularKeywordService service = serviceAt("2026-11-04T10:00:00Z");
        service.record(1L, "김치");
        service.record(1L, "두부");

        assertThat(redisTemplate.opsForZSet().score("search:popular:20261104", "김치")).isEqualTo(1.0);
        assertThat(redisTemplate.opsForZSet().score("search:popular:20261104", "두부")).isEqualTo(1.0);
        assertThat(redisTemplate.opsForSet().members("search:popular-seen:20261104:1"))
                .containsExactlyInAnyOrder("김치", "두부");
    }

    @Test
    @DisplayName("비로그인 검색과 20자를 넘는 검색어는 세지 않는다")
    void skipsAnonymousAndLongKeyword() {
        SearchPopularKeywordService service = serviceAt("2026-11-04T10:00:00Z");
        service.record(null, "김치");
        service.record(1L, "가".repeat(21));

        assertThat(redisTemplate.hasKey("search:popular:20261104")).isFalse();
    }

    @Test
    @DisplayName("공백과 영문 대소문자가 달라도 같은 검색어로 센다")
    void mergesKeywords_whenOnlySpacingOrCaseDiffers() {
        SearchPopularKeywordService service = serviceAt("2026-11-04T10:00:00Z");
        service.record(1L, " Spam  김밥 ");
        service.record(2L, "spam 김밥");

        assertThat(service.getTopKeywords().keywords()).containsExactly("spam 김밥");
    }

    @Test
    @DisplayName("어제 검색은 오늘 인기 검색어에 들어가지 않는다")
    void returnsTodayOnly() {
        searchBy(serviceAt("2026-11-03T10:00:00Z"), "어제", 5);
        searchBy(serviceAt("2026-11-04T10:00:00Z"), "된장찌개", 2);

        assertThat(serviceAt("2026-11-04T12:00:00Z").getTopKeywords().keywords()).containsExactly("된장찌개");
    }

    private SearchPopularKeywordService serviceAt(String instant) {
        return new SearchPopularKeywordService(redisTemplate, Clock.fixed(Instant.parse(instant), SEOUL));
    }

    // 서로 다른 회원 count명이 같은 검색어를 검색한다
    private void searchBy(SearchPopularKeywordService service, String keyword, int count) {
        LongStream.rangeClosed(1, count).forEach(memberId -> service.record(memberId, keyword));
    }
}
