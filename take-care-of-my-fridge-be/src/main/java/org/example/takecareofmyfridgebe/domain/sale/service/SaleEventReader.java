package org.example.takecareofmyfridgebe.domain.sale.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.sale.dto.ActiveSale;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventStatus;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 장바구니·주문이 서버에서 직접 부르는 진행 중 할인 조회. 결제 금액과 이어지므로 화면 표시용 경로와 섞지 않는다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SaleEventReader {

    // 종료로 닫은 행사만 뺀다. 상태는 스케줄러가 늦게 바꿔도 되고, 진행 중인지는 시각으로 판단한다.
    private static final List<SaleEventStatus> OPEN_STATUSES = List.of(SaleEventStatus.SCHEDULED, SaleEventStatus.ACTIVE);

    private final SaleEventItemRepository saleEventItemRepository;

    // 상품 ID별 진행 중 할인을 한 번에 돌려준다. 할인이 없는 상품은 맵에 없다.
    public Map<Long, ActiveSale> findActiveSales(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return saleEventItemRepository
                .findActiveByProductIds(productIds, OPEN_STATUSES, LocalDateTime.now(SaleEvent.ZONE))
                .stream()
                .collect(Collectors.toMap(ActiveSale::productId, Function.identity(), (first, second) -> first));
    }
}
