package org.example.takecareofmyfridgebe.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.example.takecareofmyfridgebe.domain.search.dto.SearchClickRequest;
import org.example.takecareofmyfridgebe.domain.search.entity.SearchClickHistory;
import org.example.takecareofmyfridgebe.domain.search.repository.SearchClickHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SearchClickHistoryServiceTest {

    private final SearchClickHistoryRepository repository = mock(SearchClickHistoryRepository.class);
    private final SearchClickHistoryService service = new SearchClickHistoryService(repository);

    @Test
    @DisplayName("클릭을 상품 번호·회원 번호·앞뒤 공백을 뺀 검색어로 저장한다")
    void recordsClick() {
        service.record(new SearchClickRequest(10L, " 계란 "), 7L);

        SearchClickHistory saved = captureSaved();
        assertThat(saved.getProductId()).isEqualTo(10L);
        assertThat(saved.getMemberId()).isEqualTo(7L);
        assertThat(saved.getKeyword()).isEqualTo("계란");
    }

    @Test
    @DisplayName("비로그인 클릭은 회원 번호 없이 저장한다")
    void recordsAnonymousClick() {
        service.record(new SearchClickRequest(10L, "계란"), null);

        assertThat(captureSaved().getMemberId()).isNull();
    }

    private SearchClickHistory captureSaved() {
        ArgumentCaptor<SearchClickHistory> captor = ArgumentCaptor.forClass(SearchClickHistory.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
