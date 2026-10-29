package org.example.takecareofmyfridgebe.domain.product.dto;

import org.example.takecareofmyfridgebe.domain.product.entity.IngredientProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.MealkitProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;

// 상품과 종류별 상세. 상품 종류에 따라 ingredient 또는 mealkit 중 하나만 채워지고, 상세 행이 없으면 둘 다 null이다.
// 상품 서비스가 돌려주고, 응답은 판매가와 합쳐 퍼사드가 만든다.
public record ProductDetail(
        Product product,
        IngredientProduct ingredient,
        MealkitProduct mealkit
) {

    public static ProductDetail ofIngredient(Product product, IngredientProduct ingredient) {
        return new ProductDetail(product, ingredient, null);
    }

    public static ProductDetail ofMealkit(Product product, MealkitProduct mealkit) {
        return new ProductDetail(product, null, mealkit);
    }
}
