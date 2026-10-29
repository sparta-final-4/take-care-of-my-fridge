package org.example.takecareofmyfridgebe.domain.search.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.search.dto.ProductClickCount;
import org.example.takecareofmyfridgebe.domain.search.repository.SearchClickHistoryRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchClickMetricService {

    private final SearchClickHistoryRepository searchClickHistoryRepository;

    // 할인 분석용: from~to(양끝 포함) 기간의 검색 결과 클릭 수를 상품별로 반환한다.
    // asOf 이후 클릭은 빼서, 같은 asOf로 다시 조회하면 같은 결과가 나온다. 클릭이 없는 상품은 결과에 없다.
    public List<ProductClickCount> getProductClicks(LocalDate from, LocalDate to, LocalDateTime asOf) {
        if (from == null || to == null || asOf == null || from.isAfter(to)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        return searchClickHistoryRepository.countClicksByProduct(
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay(),
                asOf
        );
    }
}
