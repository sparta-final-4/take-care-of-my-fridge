package org.example.takecareofmyfridgebe.domain.sale.service;

import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 행사 등록·수정이 상품 행을 잠그고 읽는 입구. 상품 서비스에는 잠금 읽기가 없어 할인 쪽에서 맡는다.
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SaleProductReader {

    private final ProductRepository productRepository;

    // 상품 행을 잠그고 읽는다. 호출하는 쪽의 쓰기 트랜잭션 안에서 부른다.
    @Transactional
    public List<Product> findAllByIdsForUpdate(Collection<Long> productIds) {
        return productRepository.findAllByIdInForUpdate(productIds);
    }
}
