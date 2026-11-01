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
import org.example.takecareofmyfridgebe.domain.cart.service.CartProductReader;
import org.example.takecareofmyfridgebe.domain.cart.service.CartService;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.sale.dto.SellingPrice;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventReader;
import org.springframework.stereotype.Component;

// 장바구니 응답에 상품과 판매가를 붙이고, 담기·수량 변경에 필요한 상품 한도를 읽어 장바구니 서비스에 넘긴다.
// 장바구니 서비스는 장바구니 일만 하고, 상품과 판매가는 여기서 합친다. 삭제처럼 합칠 것이 없는 일은 컨트롤러가 서비스를 바로 부른다
@Component
@RequiredArgsConstructor
public class CartFacade {

    private final CartService cartService;
    private final CartProductReader cartProductReader;
    private final SaleEventReader saleEventReader;

    public CartItemsResponse getItems(Long memberId) {
        List<CartItem> items = cartService.getItems(memberId);
        List<Long> productIds = items.stream().map(CartItem::getProductId).toList();
        List<Product> products = cartProductReader.findAllByIds(productIds);
        Map<Long, Product> productsById = products.stream().collect(Collectors.toMap(Product::getId, Function.identity()));
        return CartItemsResponse.from(items, productsById, saleEventReader.findSellingPrices(products));
    }

    public AddCartItemResult addItem(Long memberId, AddCartItemRequest request) {
        return cartService.addItem(memberId, request, getQuantityLimit(request.productId()));
    }

    public ChangeCartItemQuantityResponse changeQuantity(Long memberId, Long cartItemId, ChangeCartItemQuantityRequest request) {
        return cartService.changeQuantity(memberId, cartItemId, request, this::getQuantityLimit);
    }

    private CartQuantityLimit getQuantityLimit(long productId) {
        Product product = cartProductReader.getSellableProduct(productId);
        SellingPrice price = saleEventReader.findSellingPrice(product);
        return new CartQuantityLimit(product.getName(), product.getStock(), price.isFlashDrop());
    }
}
