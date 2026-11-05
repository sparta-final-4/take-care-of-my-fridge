package org.example.takecareofmyfridgebe.domain.search.service;

import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.search.dto.PopularKeywordsResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 인기 검색어
 * <br>
 * 검색할 때마다 그날 키(ZSET)에 검색어 점수를 1 올리고, 조회할 때 오늘 키에서 상위 TOP_SIZE개를 바로 읽는다.
 * 오늘 순위만 보여 주므로 자정이 지나면 처음부터 다시 쌓인다.
 * 1) 로그인 회원만 센다. 비로그인은 사람을 구분할 수 없어 반복 검색으로 순위를 바꿀 수 있다
 * 2) 같은 회원·검색어는 하루에 한 번만 센다. 회원마다 그날 검색한 검색어를 SET 하나에 모아 처음 넣을 때만 센다
 * 3) MIN_SCORE번 이상 검색된 검색어만 보여 준다. 한 사람만 찾은 검색어가 올라오지 않게 한다
 * <br>
 * 검색 응답 중에 부르므로 Redis 장애면 세지 않고, 조회는 빈 목록을 돌려준다.
 */
@Slf4j
@Service
public class SearchPopularKeywordService {

    static final String DAILY_KEY_PREFIX = "search:popular:";
    static final String SEEN_KEY_PREFIX = "search:popular-seen:";
    static final int TOP_SIZE = 10;
    static final int MIN_SCORE = 2;
    // 긴 문장형 검색어는 인기 검색어로 보여 주기 어렵다
    static final int MAX_KEYWORD_LENGTH = 20;

    // 오늘 키만 읽는다. 자정을 넘겨 늦게 온 요청이 어제 키를 다시 살려도 이틀 안에 지워진다
    private static final Duration DAILY_KEY_TTL = Duration.ofDays(2);
    // 그날이 지나면 쓰지 않는다. 자정을 넘겨 늦게 온 요청이 키를 다시 살려도 하루 안에 지워진다
    private static final Duration SEEN_KEY_TTL = Duration.ofDays(1);
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    @Autowired
    public SearchPopularKeywordService(StringRedisTemplate redisTemplate) {
        this(redisTemplate, Clock.system(ZONE));
    }

    // 테스트에서 날짜를 정해 넣을 때 쓴다.
    SearchPopularKeywordService(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    public void record(Long memberId, String rawKeyword) {
        if (memberId == null || rawKeyword == null) {
            return;
        }
        String keyword = normalize(rawKeyword);
        if (keyword.isEmpty() || keyword.length() > MAX_KEYWORD_LENGTH) {
            return;
        }
        String day = today().format(DAY);
        try {
            // SADD는 새로 넣었을 때만 1을 돌려준다. 0이면 오늘 이미 센 검색어다
            String seenKey = SEEN_KEY_PREFIX + day + ":" + memberId;
            Long added = redisTemplate.opsForSet().add(seenKey, keyword);
            redisTemplate.expire(seenKey, SEEN_KEY_TTL);
            if (added == null || added == 0) {
                return;
            }
            String dailyKey = DAILY_KEY_PREFIX + day;
            redisTemplate.opsForZSet().incrementScore(dailyKey, keyword, 1);
            redisTemplate.expire(dailyKey, DAILY_KEY_TTL);
        } catch (DataAccessException e) {
            log.warn("인기 검색어 집계 실패: keyword={}", keyword, e);
        }
    }

    // 오늘 MIN_SCORE번 이상 검색된 검색어를 많이 검색된 순서로 TOP_SIZE개까지 돌려준다.
    public PopularKeywordsResponse getTopKeywords() {
        String dailyKey = DAILY_KEY_PREFIX + today().format(DAY);
        try {
            Set<String> top = redisTemplate.opsForZSet()
                    .reverseRangeByScore(dailyKey, MIN_SCORE, Double.MAX_VALUE, 0, TOP_SIZE);
            return new PopularKeywordsResponse(top == null ? List.of() : List.copyOf(top));
        } catch (DataAccessException e) {
            log.warn("인기 검색어 조회 실패", e);
            return new PopularKeywordsResponse(List.of());
        }
    }

    // 앞뒤 공백을 빼고, 연속 공백을 하나로, 영문은 소문자로 맞춰 같은 검색어를 하나로 센다
    static String normalize(String keyword) {
        return keyword.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }
}
