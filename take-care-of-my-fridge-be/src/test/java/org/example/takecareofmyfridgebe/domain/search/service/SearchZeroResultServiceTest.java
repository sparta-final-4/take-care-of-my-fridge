package org.example.takecareofmyfridgebe.domain.search.service;

import org.example.takecareofmyfridgebe.domain.search.entity.SearchTarget;
import org.example.takecareofmyfridgebe.domain.search.entity.SearchZeroResultHistory;
import org.example.takecareofmyfridgebe.domain.search.repository.SearchZeroResultHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchZeroResultServiceTest {

    private final SearchZeroResultHistoryRepository repository = mock(SearchZeroResultHistoryRepository.class);
    private final SearchZeroResultService service = new SearchZeroResultService(repository);

    @Test
    @DisplayName("0건 검색어를 검색 대상과 함께 저장한다")
    void record_savesKeywordWithTarget() {
        service.record(SearchTarget.RECIPE, "김치");

        ArgumentCaptor<SearchZeroResultHistory> captor = ArgumentCaptor.forClass(SearchZeroResultHistory.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTarget()).isEqualTo(SearchTarget.RECIPE);
        assertThat(captor.getValue().getKeyword()).isEqualTo("김치");
    }

    @Test
    @DisplayName("저장이 실패해도 예외를 던지지 않는다")
    void record_doesNotThrow_whenSaveFails() {
        when(repository.save(any())).thenThrow(new IllegalStateException("DB 오류"));

        assertThatCode(() -> service.record(SearchTarget.PRODUCT, "대파")).doesNotThrowAnyException();
    }
}
