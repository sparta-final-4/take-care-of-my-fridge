package org.example.takecareofmyfridgebe.domain.sale.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.sale.dto.FlashDropQuantity;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventItem;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventItemRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 번개 드롭 남은 수량을 바꾼다. 주문하기·주문 취소가 자기 트랜잭션 안에서 부르고, 남은 수량 행은 ID 순으로 잠근다.
// 상품 행을 먼저 잠근 뒤 부르는 순서는 호출하는 쪽이 지킨다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SaleEventStockService {

    private final SaleEventItemRepository saleEventItemRepository;

    // 남은 수량보다 많이 사려는 항목이 하나라도 있으면 아무것도 줄이지 않고 거절한다.
    @Transactional
    public void decreaseRemaining(List<FlashDropQuantity> quantities) {
        Map<Long, SaleEventItem> locked = lock(quantities);
        quantities.forEach(quantity -> {
            SaleEventItem saleEventItem = locked.get(quantity.saleEventItemId());
            if (saleEventItem.getRemainingQuantity() < quantity.quantity()) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, "품절 · " + quantity.productName());
            }
            saleEventItem.decreaseRemaining(quantity.quantity());
        });
    }

    // 주문 취소로 남은 수량을 되돌린다. 한정 수량을 넘지 않는다.
    @Transactional
    public void increaseRemaining(List<FlashDropQuantity> quantities) {
        Map<Long, SaleEventItem> locked = lock(quantities);
        quantities.forEach(quantity -> locked.get(quantity.saleEventItemId()).increaseRemaining(quantity.quantity()));
    }

    private Map<Long, SaleEventItem> lock(List<FlashDropQuantity> quantities) {
        List<Long> saleEventItemIds = quantities.stream().map(FlashDropQuantity::saleEventItemId).toList();
        return saleEventItemRepository.findAllByIdInForUpdate(saleEventItemIds).stream()
                .collect(Collectors.toMap(SaleEventItem::getId, Function.identity()));
    }
}
