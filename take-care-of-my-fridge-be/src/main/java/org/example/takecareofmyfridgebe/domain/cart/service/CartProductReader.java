package org.example.takecareofmyfridgebe.domain.cart.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 장바구니가 상품을 읽는 창구. 상품 저장소를 직접 읽는 곳을 여기 한 곳으로 모은다
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartProductReader {

    private final ProductRepository productRepository;

    // 품절 상품은 재고가 0이라 담기와 수량 올리기가 뒤의 재고 수량 확인에서 걸린다. 여기서는 판매 중지만 거른다
    public Product getSellableProduct(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (product.getStatus() == ProductStatus.STOPPED) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
        }
        return product;
    }

    // 줄마다 상품을 따로 읽지 않고 한 번에 읽는다
    public List<Product> findAllByIds(List<Long> productIds) {
        return productRepository.findAllById(productIds);
    }
}
