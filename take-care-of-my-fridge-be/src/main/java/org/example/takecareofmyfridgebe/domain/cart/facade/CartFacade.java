package org.example.takecareofmyfridgebe.domain.cart.facade;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.cart.dto.AddCartItemRequest;
import org.example.takecareofmyfridgebe.domain.cart.dto.AddCartItemResult;
import org.example.takecareofmyfridgebe.domain.cart.dto.CartItemsResponse;
import org.example.takecareofmyfridgebe.domain.cart.dto.CartQuantityLimit;
import org.example.takecareofmyfridgebe.domain.cart.dto.ChangeCartItemQuantityRequest;
import org.example.takecareofmyfridgebe.domain.cart.dto.ChangeCartItemQuantityResponse;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.example.takecareofmyfridgebe.domain.cart.service.CartService;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductDetail;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.service.ProductService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.domain.sale.dto.SellingPrice;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventReader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 장바구니 응답에 상품과 판매가를 붙이고, 담기·수량 변경에 필요한 상품 한도를 읽어 장바구니 서비스에 넘긴다.
// 장바구니 서비스는 장바구니 일만 하고, 상품과 판매가는 여기서 합친다. 삭제처럼 합칠 것이 없는 일은 컨트롤러가 서비스를 바로 부른다.
@Component
@RequiredArgsConstructor
public class CartFacade {

    private final CartService cartService;
    private final ProductService productService;
    private final SaleEventReader saleEventReader;

    // 항목·상품·판매가 조회를 한 읽기 전용 트랜잭션으로 묶는다. 묶지 않으면 서비스마다 트랜잭션이 열려 제어 문장이 세 번 나간다.
    // 클래스가 아니라 이 메서드에만 건다. 담기는 재시도하려면 트랜잭션 밖이어야 한다.
    @Transactional(readOnly = true)
    public CartItemsResponse getItems(Long memberId) {
        List<CartItem> items = cartService.getItems(memberId);
        List<Long> productIds = items.stream().map(CartItem::getProductId).toList();
        // 담아 둔 뒤 판매 중지된 상품도 목록에 보여야 해서 상태로 거르지 않는 조회를 쓴다.
        List<Product> products = productService.getProductDetailsByIds(productIds).stream()
                .map(ProductDetail::product)
                .toList();
        Map<Long, Product> productsById = products.stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        return CartItemsResponse.from(items, productsById, saleEventReader.findSellingPrices(products));
    }

    // 같은 회원의 담기가 겹쳐 장바구니나 항목이 먼저 생기면 뒤의 담기는 충돌로 실패한다. 이 퍼사드는 트랜잭션 밖이라
    // 한 번만 새 트랜잭션으로 다시 시도하면 이미 생긴 항목의 수량을 올려 성공한다. 다시 시도해도 충돌이면 그대로 던진다.
    public AddCartItemResult addItem(Long memberId, AddCartItemRequest request) {
        CartQuantityLimit limit = getQuantityLimit(request.productId());
        try {
            return cartService.addItem(memberId, request, limit);
        } catch (BusinessException e) {
            if (e.getErrorCode() != ErrorCode.CART_ADD_CONFLICT) {
                throw e;
            }
            return cartService.addItem(memberId, request, limit);
        }
    }

    // 장바구니 항목을 먼저 읽어야 어느 상품의 한도를 읽을지 안다.
    public ChangeCartItemQuantityResponse changeQuantity(Long memberId, Long cartItemId, ChangeCartItemQuantityRequest request) {
        CartItem item = cartService.getItem(memberId, cartItemId);
        return cartService.changeQuantity(memberId, cartItemId, request, getQuantityLimit(item.getProductId()));
    }

    private CartQuantityLimit getQuantityLimit(long productId) {
        Product product = productService.getAdminProduct(productId).product();
        validateSellable(product);
        SellingPrice price = saleEventReader.findSellingPrice(product);
        return new CartQuantityLimit(product.getName(), product.getStock(), price.isFlashDrop());
    }

    // 품절 상품은 재고가 0이라 담기와 수량 올리기가 뒤의 재고 수량 확인에서 걸린다. 여기서는 판매 중지만 거른다.
    private void validateSellable(Product product) {
        if (product.getStatus() == ProductStatus.STOPPED) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE, "판매 중지 · " + product.getName());
        }
    }
}
