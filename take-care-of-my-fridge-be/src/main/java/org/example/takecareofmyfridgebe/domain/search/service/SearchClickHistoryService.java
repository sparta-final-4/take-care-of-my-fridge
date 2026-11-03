package org.example.takecareofmyfridgebe.domain.search.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.search.dto.SearchClickRequest;
import org.example.takecareofmyfridgebe.domain.search.dto.SearchRecipeClickRequest;
import org.example.takecareofmyfridgebe.domain.search.repository.SearchClickHistoryRepository;
import org.example.takecareofmyfridgebe.domain.search.repository.SearchRecipeClickHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchClickHistoryService {

    private final SearchClickHistoryRepository searchClickHistoryRepository;
    private final SearchRecipeClickHistoryRepository searchRecipeClickHistoryRepository;

    // 검색 결과 상품 클릭을 기록한다. 비로그인이면 memberId는 null이다.
    @Transactional
    public void record(SearchClickRequest request, Long memberId) {
        searchClickHistoryRepository.save(request.toEntity(memberId));
    }

    // 검색 결과 레시피 클릭을 기록한다. 비로그인이면 memberId는 null이다.
    @Transactional
    public void recordRecipe(SearchRecipeClickRequest request, Long memberId) {
        searchRecipeClickHistoryRepository.save(request.toEntity(memberId));
    }
}
