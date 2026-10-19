package org.example.takecareofmyfridgebe.domain.product.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListItemResponse;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductSort;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    // 판매 중지(STOPPED) 상품은 공개 목록에서 제외
    private static final List<ProductStatus> PUBLIC_STATUSES = List.of(ProductStatus.ON_SALE, ProductStatus.SOLD_OUT);

    private final ProductRepository productRepository;

    public PageResponse<ProductListItemResponse> getProducts(ProductSort sort, int page, int size) {
        PageRequest pageable = PageRequest.of(page - 1, size, sort.toSort());

        Page<ProductListItemResponse> response = productRepository.findByStatusIn(PUBLIC_STATUSES, pageable)
                .map(ProductListItemResponse::from);

        return PageResponse.of(response);
    }
}
