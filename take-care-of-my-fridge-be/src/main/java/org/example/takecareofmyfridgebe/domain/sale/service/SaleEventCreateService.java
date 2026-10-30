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
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.domain.sale.dto.FlashDropCreateRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.FlashDropItemRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventsCreateResponse;
import org.example.takecareofmyfridgebe.domain.sale.dto.SpecialSaleCreateRequest;
import org.example.takecareofmyfridgebe.domain.sale.dto.SpecialSaleItemRequest;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventStatus;
import org.example.takecareofmyfridgebe.domain.sale.event.FlashDropNotificationEvent;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventItemRepository;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SaleEventCreateService {


    private final SaleEventRepository saleEventRepository;
    private final SaleEventItemRepository saleEventItemRepository;
    private final ProductRepository productRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public SaleEventsCreateResponse createSpecialSale(SpecialSaleCreateRequest request) {
        validateStartsAt(request.startsAt());
        Map<Long, Product> productsById = lockAvailableProducts(request.productIds());

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
    public SaleEventsCreateResponse createFlashDrop(FlashDropCreateRequest request) {
        validateStartsAt(request.startsAt());
        Map<Long, Product> productsById = lockAvailableProducts(request.productIds());
        for (FlashDropItemRequest item : request.items()) {
            Product product = productsById.get(item.productId());
            if (item.limitedQuantity() > product.getStock()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "한정 수량이 재고보다 많습니다 · " + product.getName());
            }
        }

        List<SaleEvent> saleEvents = new ArrayList<>();
        for (FlashDropItemRequest item : request.items()) {
            SaleEvent saleEvent = saleEventRepository.save(request.toSaleEvent());
            saleEventItemRepository.save(item.toSaleEventItem(saleEvent, productsById.get(item.productId()).getPrice()));
            saleEvents.add(saleEvent);
        }
        // 실제 발행은 커밋 뒤에 한다
        eventPublisher.publishEvent(FlashDropNotificationEvent.created(
                saleEvents.stream().map(SaleEvent::getId).toList()));
        return SaleEventsCreateResponse.from(saleEvents);
    }

    private void validateStartsAt(LocalDateTime startsAt) {
        if (startsAt.isBefore(LocalDateTime.now(SaleEvent.ZONE))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "시작 시각은 지금 이후여야 합니다.");
        }
    }

    // 상품 행을 먼저 잠그고 행사 여부를 읽는다. 같은 상품을 동시에 등록하면 뒤 요청은 앞 요청이 끝날 때까지
    // 기다렸다가 앞 요청이 넣은 행사를 보고 거절된다. 정가도 잠긴 값으로 읽는다.
    private Map<Long, Product> lockAvailableProducts(List<Long> productIds) {
        List<Product> products = productRepository.findAllByIdInForUpdate(productIds);
        if (products.size() != productIds.size()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        products.stream()
                .filter(product -> product.getStatus() != ProductStatus.ON_SALE)
                .findFirst()
                .ifPresent(product -> {
                    throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE, notOnSaleMessage(product));
                });

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

    private String notOnSaleMessage(Product product) {
        String reason = product.getStatus() == ProductStatus.SOLD_OUT ? "품절" : "판매 중지";
        return reason + " · " + product.getName();
    }
}
