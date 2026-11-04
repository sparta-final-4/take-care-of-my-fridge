package org.example.takecareofmyfridgebe.domain.deal.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.deal.dto.DealProductInput;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DealAnalysisDiagnosticsService {

    private final DealTrainingObservationService observationService;
    private final DealProductInputService productInputService;

    public String describeMissing() {
        LocalDateTime asOf = LocalDateTime.now(SaleEvent.ZONE);
        List<DealDiscountModel.Observation> observations = observationService.getObservations(asOf);
        long rateCount = observations.stream().map(DealDiscountModel.Observation::discountRate)
                .distinct().count();
        List<DealProductInput> products = productInputService.getProducts();
        long activeCount = products.stream()
                .filter(product -> product.status() != ProductStatus.STOPPED).count();
        long stockedCount = products.stream()
                .filter(product -> product.status() != ProductStatus.STOPPED)
                .filter(product -> product.currentStock() >= DealExclusionPolicy.MIN_STOCK).count();

        return "할인 후보 분석 결과가 없습니다."
                + "\n분석 실행: 관리자가 위 버튼으로 직접 실행"
                + "\n분석 대상 상품: " + activeCount + "개 (판매 중지 제외)"
                + "\n재고 기준 상품: " + stockedCount + "개 ("
                + DealExclusionPolicy.MIN_STOCK + "개 이상)"
                + "\n학습 가능한 종료 일반 특가: " + observations.size() + "건 / 최소 3건 ("
                + requirement(observations.size() >= 3) + ")"
                + "\n학습 할인율 종류: " + rateCount + "개 / 최소 2개 ("
                + requirement(rateCount >= 2) + ")";
    }

    private String requirement(boolean met) {
        return met ? "충족" : "부족";
    }
}
