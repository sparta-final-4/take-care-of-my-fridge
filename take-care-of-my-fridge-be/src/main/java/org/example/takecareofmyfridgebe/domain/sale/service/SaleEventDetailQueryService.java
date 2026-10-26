package org.example.takecareofmyfridgebe.domain.sale.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventDetailResponse;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventItem;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventItemRepository;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 행사 수정 화면에 보여 줄 행사 하나와 그 상품들
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SaleEventDetailQueryService {

    private final SaleEventRepository saleEventRepository;
    private final SaleEventItemRepository saleEventItemRepository;
    private final ProductRepository productRepository;

    public SaleEventDetailResponse getSaleEvent(Long saleEventId) {
        SaleEvent saleEvent = saleEventRepository.findById(saleEventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SALE_EVENT_NOT_FOUND));
        List<SaleEventItem> items = saleEventItemRepository.findBySaleEventIdOrderById(saleEventId);
        Map<Long, Product> productsById = productRepository
                .findAllById(items.stream().map(SaleEventItem::getProductId).toList()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return SaleEventDetailResponse.from(saleEvent, items, productsById);
    }
}
