package org.example.takecareofmyfridgebe.domain.product.dto;

import lombok.Builder;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.product.entity.IngredientProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.MealkitProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;

import java.math.BigDecimal;

// 상품 타입에 따라 ingredient 또는 mealkit 중 하나만 채워진다
@Builder
public record ProductDetailResponse(
        Long productId,
        ProductType productType,
        String name,
        String description,
        String imageUrl,
        int price,
        int stock,
        StorageType storage,
        int shelfDays,
        ProductStatus status,
        IngredientInfo ingredient,
        MealkitInfo mealkit
) {
    public record IngredientInfo(
            BigDecimal unitAmount,
            String unit
    ) {
        public static IngredientInfo from(IngredientProduct ingredientProduct) {
            return new IngredientInfo(ingredientProduct.getUnitAmount(), ingredientProduct.getUnit());
        }
    }

    public record MealkitInfo(
            int servings
    ) {
        public static MealkitInfo from(MealkitProduct mealkitProduct) {
            return new MealkitInfo(mealkitProduct.getServings());
        }
    }

    // 상세 행이 없으면(null) 해당 정보 없이 기본 정보만 반환
    public static ProductDetailResponse ofIngredient(Product product, IngredientProduct ingredientProduct) {
        return of(product, ingredientProduct == null ? null : IngredientInfo.from(ingredientProduct), null);
    }

    public static ProductDetailResponse ofMealkit(Product product, MealkitProduct mealkitProduct) {
        return of(product, null, mealkitProduct == null ? null : MealkitInfo.from(mealkitProduct));
    }

    private static ProductDetailResponse of(Product product, IngredientInfo ingredient, MealkitInfo mealkit) {
        return ProductDetailResponse.builder()
                .productId(product.getId())
                .productType(product.getProductType())
                .name(product.getName())
                .description(product.getDescription())
                .imageUrl(product.getImageUrl())
                .price(product.getPrice())
                .stock(product.getStock())
                .storage(product.getStorage())
                .shelfDays(product.getShelfDays())
                .status(product.getStatus())
                .ingredient(ingredient)
                .mealkit(mealkit)
                .build();
    }
}
