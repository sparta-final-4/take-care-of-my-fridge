package org.example.takecareofmyfridgebe.domain.sale.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventItemUpdateRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventUpdateRequest;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventItem;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventItemRepository;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 시작 전인 행사의 시각과 상품 값을 한 번에 고친다. 정가는 상품의 지금 정가로 다시 읽는다.
// 한 트랜잭션이라 어느 항목이 거절되면 아무것도 바뀌지 않는다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SaleEventUpdateService {

    private final SaleEventRepository saleEventRepository;
    private final SaleEventItemRepository saleEventItemRepository;
    private final ProductRepository productRepository;

    // 행사 행을 잠근 뒤 시작 전인지 확인한다. 상태 정리가 같은 행사를 시작하려 하면 이 요청이 끝날 때까지 기다린다.
    @Transactional
    public void update(Long saleEventId, SaleEventUpdateRequest request) {
        SaleEvent saleEvent = saleEventRepository.findByIdForUpdate(saleEventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SALE_EVENT_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(SaleEvent.ZONE);
        if (!saleEvent.isEditable(now)) {
            throw new BusinessException(ErrorCode.SALE_EVENT_NOT_EDITABLE);
        }
        if (request.startsAt().isBefore(now)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "시작 시각은 지금 이후여야 합니다.");
        }

        List<SaleEventItem> items = saleEventItemRepository.findBySaleEventIdOrderById(saleEventId);
        Map<Long, SaleEventItemUpdateRequest> requestsByProductId = request.items().stream()
                .collect(Collectors.toMap(SaleEventItemUpdateRequest::productId, Function.identity()));
        validateSameProducts(items, requestsByProductId);

        Map<Long, Product> productsById = productRepository.findAllByIdInForUpdate(requestsByProductId.keySet())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        for (SaleEventItem item : items) {
            SaleEventItemUpdateRequest itemRequest = requestsByProductId.get(item.getProductId());
            Product product = productsById.get(item.getProductId());
            validateLimitedQuantity(saleEvent.getType(), itemRequest.limitedQuantity(), product);
            item.update(product.getPrice(), itemRequest.discountRate(), itemRequest.limitedQuantity());
        }
        saleEvent.reschedule(request.startsAt(), request.endsAt());
    }

    // 상품을 넣거나 빼지 않으므로 행사에 든 상품과 보낸 상품이 정확히 같아야 한다.
    private void validateSameProducts(List<SaleEventItem> items, Map<Long, SaleEventItemUpdateRequest> requests) {
        boolean same = items.size() == requests.size()
                && items.stream().allMatch(item -> requests.containsKey(item.getProductId()));
        if (!same) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "행사에 든 상품과 보낸 상품이 다릅니다.");
        }
    }

    private void validateLimitedQuantity(SaleEventType type, Integer limitedQuantity, Product product) {
        if (type == SaleEventType.FLASH_DROP && limitedQuantity == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "번개 드롭은 한정 수량이 필수입니다.");
        }
        if (type == SaleEventType.SPECIAL && limitedQuantity != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "특가는 한정 수량을 받지 않습니다.");
        }
        if (limitedQuantity != null && limitedQuantity > product.getStock()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "한정 수량이 재고보다 많습니다 · " + product.getName());
        }
    }
}
