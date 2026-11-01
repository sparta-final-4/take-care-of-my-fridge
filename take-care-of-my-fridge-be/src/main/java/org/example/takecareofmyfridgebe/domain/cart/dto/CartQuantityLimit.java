package org.example.takecareofmyfridgebe.domain.cart.dto;

// 담거나 수량을 바꿀 때 상품에서 알아야 하는 것. 퍼사드가 상품과 판매가를 읽어 만들어 장바구니 서비스에 넘긴다.
public record CartQuantityLimit(String productName, int stock, boolean flashDrop) {
}
