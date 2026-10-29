package org.example.takecareofmyfridgebe.domain.search.service;

import org.example.takecareofmyfridgebe.domain.search.dto.ProductClickCount;
import org.example.takecareofmyfridgebe.domain.search.repository.SearchClickHistoryRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SearchClickMetricServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 3);
    private static final LocalDateTime AS_OF = LocalDateTime.of(2026, 10, 4, 3, 0);

    @Mock
    private SearchClickHistoryRepository searchClickHistoryRepository;

    @InjectMocks
    private SearchClickMetricService searchClickMetricService;

    @Test
    @DisplayName("from 0시부터 to 다음 날 0시 전까지를 기준 시각과 함께 조회한다")
    void queriesFromStartOfFromToStartOfDayAfterTo() {
        List<ProductClickCount> clicks = List.of(new ProductClickCount(1L, 3L), new ProductClickCount(2L, 1L));
        given(searchClickHistoryRepository.countClicksByProduct(
                LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 4, 0, 0), AS_OF)).willReturn(clicks);

        assertThat(searchClickMetricService.getProductClicks(FROM, TO, AS_OF)).isEqualTo(clicks);
    }

    @Test
    @DisplayName("from과 to가 같은 날이면 그날 하루를 조회한다")
    void queriesSingleDay() {
        given(searchClickHistoryRepository.countClicksByProduct(
                LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 2, 0, 0), AS_OF)).willReturn(List.of());

        assertThat(searchClickMetricService.getProductClicks(FROM, FROM, AS_OF)).isEmpty();
    }

    @Test
    @DisplayName("from이 to보다 늦으면 입력 오류이고 조회하지 않는다")
    void rejectsFromAfterTo() {
        assertThatThrownBy(() -> searchClickMetricService.getProductClicks(TO, FROM, AS_OF))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(searchClickHistoryRepository);
    }

    @Test
    @DisplayName("기간이나 기준 시각이 없으면 입력 오류이고 조회하지 않는다")
    void rejectsMissingInput() {
        assertThatThrownBy(() -> searchClickMetricService.getProductClicks(null, TO, AS_OF))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> searchClickMetricService.getProductClicks(FROM, null, AS_OF))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> searchClickMetricService.getProductClicks(FROM, TO, null))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(searchClickHistoryRepository);
    }
}
