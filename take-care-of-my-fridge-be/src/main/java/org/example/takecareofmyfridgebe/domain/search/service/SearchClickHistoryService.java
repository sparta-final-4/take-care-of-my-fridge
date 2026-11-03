package org.example.takecareofmyfridgebe.domain.search.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.search.dto.SearchClickRequest;
import org.example.takecareofmyfridgebe.domain.search.dto.SearchRecipeClickRequest;
import org.example.takecareofmyfridgebe.domain.search.repository.SearchClickHistoryRepository;
import org.example.takecareofmyfridgebe.domain.search.repository.SearchRecipeClickHistoryRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 검색 결과 상품 클릭 기록
 * <br>
 * 할인 분석과 메인 "지금 뜨는 할인"이 이 클릭 수를 읽으므로 반복 클릭을 걸러서 저장한다.
 * 1) 로그인: 같은 회원·상품·검색어는 DEDUP_WINDOW 안에 한 번만 저장한다
 * 2) 비로그인: 사람을 구분할 수 없어 상품별로 1분에 ANONYMOUS_LIMIT_PER_MINUTE건까지만 저장한다
 * <br>
 * Redis 장애면 클릭을 놓치지 않도록 거르지 않고 저장한다.
 * 레시피 클릭은 거르지 않고 그대로 저장한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchClickHistoryService {

    static final String DEDUP_KEY_PREFIX = "search:click-dedup:";
    static final String ANONYMOUS_LIMIT_KEY_PREFIX = "search:click-anon:";
    // 로그인 회원이 같은 상품·검색어를 이 시간 안에 다시 누르면 한 번으로 친다
    static final Duration DEDUP_WINDOW = Duration.ofMinutes(10);
    // 비로그인은 사람을 구분할 수 없어 상품별 1분 저장 수 상한만 둔다
    static final int ANONYMOUS_LIMIT_PER_MINUTE = 30;

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    // 분이 바뀐 뒤에도 늦게 온 increment가 새 키를 만들지 않도록 1분보다 길게 둔다
    private static final Duration ANONYMOUS_LIMIT_KEY_TTL = Duration.ofMinutes(2);

    private final SearchClickHistoryRepository searchClickHistoryRepository;
    private final SearchRecipeClickHistoryRepository searchRecipeClickHistoryRepository;
    private final StringRedisTemplate redisTemplate;

    // 검색 결과 상품 클릭을 기록한다. 비로그인이면 memberId는 null이다. 반복 클릭이면 저장하지 않는다.
    @Transactional
    public void record(SearchClickRequest request, Long memberId) {
        if (!shouldRecord(request.productId(), request.keyword().strip(), memberId)) {
            return;
        }
        searchClickHistoryRepository.save(request.toEntity(memberId));
    }

    private boolean shouldRecord(Long productId, String keyword, Long memberId) {
        try {
            return memberId == null ? underAnonymousLimit(productId) : firstInWindow(memberId, productId, keyword);
        } catch (DataAccessException e) {
            log.warn("클릭 중복 확인 실패, 거르지 않고 저장: productId={}", productId, e);
            return true;
        }
    }

    // SET NX: 키가 없을 때만 만들고 true. 이미 있으면 창 안의 반복 클릭이다
    private boolean firstInWindow(Long memberId, Long productId, String keyword) {
        String key = DEDUP_KEY_PREFIX + memberId + ":" + productId + ":" + keyword;
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, "1", DEDUP_WINDOW));
    }

    private boolean underAnonymousLimit(Long productId) {
        String key = ANONYMOUS_LIMIT_KEY_PREFIX + productId + ":" + LocalDateTime.now().format(MINUTE);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, ANONYMOUS_LIMIT_KEY_TTL);
        }
        return count != null && count <= ANONYMOUS_LIMIT_PER_MINUTE;
    }

    // 검색 결과 레시피 클릭을 기록한다. 비로그인이면 memberId는 null이다.
    @Transactional
    public void recordRecipe(SearchRecipeClickRequest request, Long memberId) {
        searchRecipeClickHistoryRepository.save(request.toEntity(memberId));
    }
}
