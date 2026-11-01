package org.example.takecareofmyfridgebe.domain.sale.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.sale.dto.FlashDropCreateRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.FlashDropItemRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventItemUpdateRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventUpdateRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventsCreateResponse;
import org.example.takecareofmyfridgebe.domain.sale.dto.SpecialSaleCreateRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SpecialSaleItemRequest;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventItem;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventStatus;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;
import org.example.takecareofmyfridgebe.domain.sale.event.FlashDropNotificationEvent;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventItemRepository;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 관리자가 하는 할인 행사 쓰기. 등록, 시작 전 수정, 진행 중 종료, 시작 전 삭제를 한곳에서 한다.
// 등록·수정에 쓸 상품은 AdminSaleEventFacade가 읽어 넘긴다.
// 수정·종료·삭제는 행사 행을 잠근 뒤 상태를 확인하므로, 상태 정리가 같은 행사를 바꾸려 하면 이 요청이 끝날 때까지 기다린다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SaleEventService {

    private final SaleEventRepository saleEventRepository;
    private final SaleEventItemRepository saleEventItemRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SaleEventsCreateResponse createSpecialSale(SpecialSaleCreateRequest request, List<Product> products) {
        validateStartsAt(request.startsAt());
        Map<Long, Product> productsById = getAvailableProducts(request.productIds(), products);

        // 상품마다 행사를 따로 만든다. 같은 기간을 한 번에 입력해도 시각 수정·삭제·종료는 상품 하나씩 한다.
        List<SaleEvent> saleEvents = new ArrayList<>();
        for (SpecialSaleItemRequest item : request.items()) {
            SaleEvent saleEvent = saleEventRepository.save(request.toSaleEvent());
            saleEventItemRepository.save(item.toSaleEventItem(saleEvent, productsById.get(item.productId()).getPrice()));
            saleEvents.add(saleEvent);
        }
        return SaleEventsCreateResponse.from(saleEvents);
    }

    @Transactional
    public SaleEventsCreateResponse createFlashDrop(FlashDropCreateRequest request, List<Product> products) {
        validateStartsAt(request.startsAt());
        Map<Long, Product> productsById = getAvailableProducts(request.productIds(), products);
        request.items().forEach(item -> validateLimitedQuantity(
                SaleEventType.FLASH_DROP, item.limitedQuantity(), productsById.get(item.productId())));

        List<SaleEvent> saleEvents = new ArrayList<>();
        for (FlashDropItemRequest item : request.items()) {
            SaleEvent saleEvent = saleEventRepository.save(request.toSaleEvent());
            saleEventItemRepository.save(item.toSaleEventItem(saleEvent, productsById.get(item.productId()).getPrice()));
            saleEvents.add(saleEvent);
        }
        // 실제 발행은 커밋 뒤에 한다.
        eventPublisher.publishEvent(FlashDropNotificationEvent.created(
                saleEvents.stream().map(SaleEvent::getId).toList()));
        return SaleEventsCreateResponse.from(saleEvents);
    }

    // 시작 전인 행사의 시각과 상품 값을 한 번에 고친다. 정가는 상품의 지금 정가로 다시 읽는다.
    // 한 트랜잭션이라 어느 항목이 거절되면 아무것도 바뀌지 않는다.
    @Transactional
    public void update(Long saleEventId, SaleEventUpdateRequest request, List<Product> products) {
        SaleEvent saleEvent = getSaleEventForUpdate(saleEventId);
        LocalDateTime now = LocalDateTime.now(SaleEvent.ZONE);
        if (!saleEvent.isEditable(now)) {
            throw new BusinessException(ErrorCode.SALE_EVENT_NOT_EDITABLE);
        }
        validateStartsAt(request.startsAt());

        List<SaleEventItem> items = saleEventItemRepository.findBySaleEventIdOrderById(saleEventId);
        Map<Long, SaleEventItemUpdateRequest> requestsByProductId = request.items().stream()
                .collect(Collectors.toMap(SaleEventItemUpdateRequest::productId, Function.identity()));
        validateSameProducts(items, requestsByProductId);
        Map<Long, Product> productsById = products.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        for (SaleEventItem item : items) {
            SaleEventItemUpdateRequest itemRequest = requestsByProductId.get(item.getProductId());
            Product product = productsById.get(item.getProductId());
            validateLimitedQuantity(saleEvent.getType(), itemRequest.limitedQuantity(), product);
            item.update(product.getPrice(), itemRequest.discountRate(), itemRequest.limitedQuantity());
        }
        saleEvent.reschedule(request.startsAt(), request.endsAt());
    }

    @Transactional
    public void end(Long saleEventId) {
        SaleEvent saleEvent = getSaleEventForUpdate(saleEventId);
        LocalDateTime now = LocalDateTime.now(SaleEvent.ZONE);
        if (!saleEvent.isEndable(now)) {
            throw new BusinessException(ErrorCode.SALE_EVENT_NOT_ENDABLE);
        }
        saleEvent.end(now);
    }

    @Transactional
    public void delete(Long saleEventId) {
        SaleEvent saleEvent = getSaleEventForUpdate(saleEventId);
        if (!saleEvent.isDeletable(LocalDateTime.now(SaleEvent.ZONE))) {
            throw new BusinessException(ErrorCode.SALE_EVENT_NOT_DELETABLE);
        }
        saleEventItemRepository.deleteAll(saleEventItemRepository.findBySaleEventIdOrderById(saleEventId));
        saleEventRepository.delete(saleEvent);
    }

    // 이미 열린 행사가 있는 상품은 거절하고, 상품을 ID로 찾을 수 있게 모아 돌려준다.
    // 없는 상품과 판매 중이 아닌 상품은 퍼사드가 먼저 거른다.
    private Map<Long, Product> getAvailableProducts(List<Long> productIds, List<Product> products) {
        Set<Long> productIdsInSale = new HashSet<>(
                saleEventItemRepository.findProductIdsInOpenSaleEvent(
                        productIds, SaleEventStatus.OPEN, LocalDateTime.now(SaleEvent.ZONE)));
        products.stream()
                .filter(product -> productIdsInSale.contains(product.getId()))
                .findFirst()
                .ifPresent(product -> {
                    throw new BusinessException(ErrorCode.SALE_EVENT_PRODUCT_DUPLICATED,
                            "이미 행사가 있는 상품 · " + product.getName());
                });

        return products.stream().collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    private SaleEvent getSaleEventForUpdate(Long saleEventId) {
        return saleEventRepository.findByIdForUpdate(saleEventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SALE_EVENT_NOT_FOUND));
    }

    private void validateStartsAt(LocalDateTime startsAt) {
        if (startsAt.isBefore(LocalDateTime.now(SaleEvent.ZONE))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "시작 시각은 지금 이후여야 합니다.");
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

    // 상품을 넣거나 빼지 않으므로 행사에 든 상품과 보낸 상품이 정확히 같아야 한다.
    private void validateSameProducts(List<SaleEventItem> items, Map<Long, SaleEventItemUpdateRequest> requests) {
        boolean same = items.size() == requests.size()
                && items.stream().allMatch(item -> requests.containsKey(item.getProductId()));
        if (!same) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "행사에 든 상품과 보낸 상품이 다릅니다.");
        }
    }
}
