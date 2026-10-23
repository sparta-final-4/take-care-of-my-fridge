package org.example.takecareofmyfridgebe.domain.sale.service;

import java.time.LocalDateTime;
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
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventCreateResponse;
import org.example.takecareofmyfridgebe.domain.sale.dto.SpecialSaleCreateRequest;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventStatus;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventItemRepository;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SaleEventCreateService {

    // 한 상품은 예정·진행 중인 행사 하나에만 들어간다
    private static final List<SaleEventStatus> OPEN_STATUSES = List.of(SaleEventStatus.SCHEDULED, SaleEventStatus.ACTIVE);

    private final SaleEventRepository saleEventRepository;
    private final SaleEventItemRepository saleEventItemRepository;
    private final ProductRepository productRepository;

    @Transactional
    public SaleEventCreateResponse createSpecialSale(SpecialSaleCreateRequest request) {
        validateStartsAt(request.startsAt());
        Map<Long, Product> productsById = lockAvailableProducts(request.productIds());

        SaleEvent saleEvent = saleEventRepository.save(request.toSaleEvent());
        saleEventItemRepository.saveAll(request.items().stream()
                .map(item -> item.toSaleEventItem(saleEvent, productsById.get(item.productId()).getPrice()))
                .toList());
        return SaleEventCreateResponse.from(saleEvent);
    }

    @Transactional
    public SaleEventCreateResponse createFlashDrop(FlashDropCreateRequest request) {
        validateStartsAt(request.startsAt());
        Product product = lockAvailableProducts(List.of(request.productId())).get(request.productId());
        if (request.limitedQuantity() > product.getStock()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "한정 수량이 재고보다 많습니다 · " + product.getName());
        }

        SaleEvent saleEvent = saleEventRepository.save(request.toSaleEvent());
        saleEventItemRepository.save(request.toSaleEventItem(saleEvent, product.getPrice()));
        return SaleEventCreateResponse.from(saleEvent);
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
                        productIds, OPEN_STATUSES, LocalDateTime.now(SaleEvent.ZONE)));
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
