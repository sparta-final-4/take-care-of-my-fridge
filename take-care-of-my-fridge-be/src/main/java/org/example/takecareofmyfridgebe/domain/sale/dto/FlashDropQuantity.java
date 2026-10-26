package org.example.takecareofmyfridgebe.domain.sale.dto;

// 번개 드롭 행사 상품 하나의 수량 변경 요청. 상품 이름은 부족할 때 알릴 메시지에 쓴다.
public record FlashDropQuantity(
        Long saleEventItemId,
        int quantity,
        String productName
) {
}
