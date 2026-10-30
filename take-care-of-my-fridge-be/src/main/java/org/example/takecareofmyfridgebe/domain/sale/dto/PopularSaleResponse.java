package org.example.takecareofmyfridgebe.domain.sale.dto;

// 메인 화면 특가 카드 하나. 카드를 누르면 상품 상세로 가므로 상품 ID를 함께 내려 준다.
public record PopularSaleResponse(
        Long productId,
        String name,
        String imageUrl,
        // 정가(원)
        int price,
        SaleResponse sale
) {

    public static PopularSaleResponse from(PopularSaleProduct popular) {
        return new PopularSaleResponse(popular.product().getId(), popular.product().getName(), popular.product().getImageUrl(),
                popular.product().getPrice(), SaleResponse.from(popular.sale()));
    }
}
