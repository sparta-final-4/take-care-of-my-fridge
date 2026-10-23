package org.example.takecareofmyfridgebe.domain.sale.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.sale.dto.ActiveSale;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleInfoResponse;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventStatus;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 진행 중 할인 조회. 장바구니·주문은 금액을 정하려고 `findActiveSales`를 서버에서 직접 부르고,
// 상품 목록·상세는 퍼사드가 표시용 `findSaleInfos`를 불러 응답에 합친다. 결제 금액과 이어지는 경로는 표시용과 섞지 않는다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SaleEventReader {

    private final SaleEventItemRepository saleEventItemRepository;

    // 상품 ID별 진행 중 할인을 한 번에 돌려준다. 할인이 없는 상품은 맵에 없다.
    public Map<Long, ActiveSale> findActiveSales(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return saleEventItemRepository
                // 종료로 닫은 행사만 뺀다. 상태는 스케줄러가 늦게 바꿔도 되고, 진행 중인지는 시각으로 판단한다.
                .findActiveByProductIds(productIds, SaleEventStatus.OPEN, LocalDateTime.now(SaleEvent.ZONE))
                .stream()
                .collect(Collectors.toMap(ActiveSale::productId, Function.identity(), (first, second) -> first));
    }

    // 상품 목록·상세에 붙일 표시용 할인 정보. 상품 서비스가 아니라 그 위의 퍼사드가 불러 응답에 합친다.
    // 할인이 없는 상품은 맵에 없다.
    public Map<Long, SaleInfoResponse> findSaleInfos(Collection<Long> productIds) {
        return findActiveSales(productIds).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> SaleInfoResponse.from(entry.getValue())));
    }
}
