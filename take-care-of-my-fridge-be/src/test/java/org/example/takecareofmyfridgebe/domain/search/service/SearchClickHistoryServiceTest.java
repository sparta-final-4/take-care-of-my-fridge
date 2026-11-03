package org.example.takecareofmyfridgebe.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.example.takecareofmyfridgebe.domain.search.dto.SearchClickRequest;
import org.example.takecareofmyfridgebe.domain.search.dto.SearchRecipeClickRequest;
import org.example.takecareofmyfridgebe.domain.search.entity.SearchClickHistory;
import org.example.takecareofmyfridgebe.domain.search.entity.SearchRecipeClickHistory;
import org.example.takecareofmyfridgebe.domain.search.repository.SearchClickHistoryRepository;
import org.example.takecareofmyfridgebe.domain.search.repository.SearchRecipeClickHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SearchClickHistoryServiceTest {

    private final SearchClickHistoryRepository repository = mock(SearchClickHistoryRepository.class);
    private final SearchRecipeClickHistoryRepository recipeRepository = mock(SearchRecipeClickHistoryRepository.class);
    private final SearchClickHistoryService service = new SearchClickHistoryService(repository, recipeRepository);

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
    @DisplayName("레시피 클릭을 레시피 번호·회원 번호·앞뒤 공백을 뺀 검색어로 저장한다")
    void recordRecipe_savesClick() {
        service.recordRecipe(new SearchRecipeClickRequest(3L, " 김치 "), 7L);

        ArgumentCaptor<SearchRecipeClickHistory> captor = ArgumentCaptor.forClass(SearchRecipeClickHistory.class);
        verify(recipeRepository).save(captor.capture());
        SearchRecipeClickHistory saved = captor.getValue();
        assertThat(saved.getRecipeId()).isEqualTo(3L);
        assertThat(saved.getMemberId()).isEqualTo(7L);
        assertThat(saved.getKeyword()).isEqualTo("김치");
        verifyNoInteractions(repository);
    }

    private SearchClickHistory captureSaved() {
        ArgumentCaptor<SearchClickHistory> captor = ArgumentCaptor.forClass(SearchClickHistory.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
