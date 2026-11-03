package org.example.takecareofmyfridgebe.domain.search.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.search.entity.SearchTarget;
import org.example.takecareofmyfridgebe.domain.search.entity.SearchZeroResultHistory;
import org.example.takecareofmyfridgebe.domain.search.repository.SearchZeroResultHistoryRepository;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchZeroResultService {

    private final SearchZeroResultHistoryRepository searchZeroResultHistoryRepository;

    /**
     * 결과가 0건인 검색어를 기록한다.
     * <br>
     * 검색 응답 중에 부르므로 저장이 실패해도 로그만 남긴다. 트랜잭션을 걸지 않아 실패가 검색 쪽으로 번지지 않는다.
     */
    public void record(SearchTarget target, String keyword) {
        try {
            searchZeroResultHistoryRepository.save(new SearchZeroResultHistory(target, keyword));
        } catch (RuntimeException e) {
            log.warn("0건 검색어 기록 실패: target={}, keyword={}", target, keyword, e);
        }
    }
}
