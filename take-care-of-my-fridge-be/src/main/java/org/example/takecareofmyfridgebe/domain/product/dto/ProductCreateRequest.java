package org.example.takecareofmyfridgebe.domain.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.product.entity.IngredientProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.MealkitProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;

import java.math.BigDecimal;

// 상품 타입에 맞는 상세(ingredient 또는 mealkit) 하나만 받는다
public record ProductCreateRequest(
        @NotNull(message = "상품 종류를 선택해 주세요.")
        ProductType productType,

        @NotBlank(message = "상품명을 입력해 주세요.")
        @Size(max = 200, message = "상품명은 200자 이하여야 합니다.")
        String name,

        String description,

        @Size(max = 500, message = "이미지 URL은 500자 이하여야 합니다.")
        String imageUrl,

        @NotNull(message = "가격을 입력해 주세요.")
        @Min(value = 0, message = "가격은 0 이상이어야 합니다.")
        Integer price,

        @NotNull(message = "재고를 입력해 주세요.")
        @Min(value = 0, message = "재고는 0 이상이어야 합니다.")
        Integer stock,

        @NotNull(message = "보관 방법을 선택해 주세요.")
        StorageType storage,

        @NotNull(message = "권장 보관 일수를 입력해 주세요.")
        @Min(value = 1, message = "권장 보관 일수는 1 이상이어야 합니다.")
        Integer shelfDays,

        @Valid
        IngredientDetail ingredient,

        @Valid
        MealkitDetail mealkit
) {
    public record IngredientDetail(
            @NotNull(message = "연결할 재료를 선택해 주세요.")
            Long ingredientId,

            @NotNull(message = "용량을 입력해 주세요.")
            @Positive(message = "용량은 0보다 커야 합니다.")
            BigDecimal unitAmount,

            @NotBlank(message = "용량 단위를 입력해 주세요.")
            @Size(max = 20, message = "용량 단위는 20자 이하여야 합니다.")
            String unit,

            @NotNull(message = "재료 기본 단위 수량을 입력해 주세요.")
            @Positive(message = "재료 기본 단위 수량은 0보다 커야 합니다.")
            BigDecimal baseQuantity
    ) {
    }

    public record MealkitDetail(
            @NotNull(message = "구성 인분을 입력해 주세요.")
            @Min(value = 1, message = "구성 인분은 1 이상이어야 합니다.")
            Integer servings
    ) {
    }

    @AssertTrue(message = "상품 종류에 맞는 상세 정보를 하나만 입력해 주세요.")
    public boolean isDetailMatchingType() {
        if (productType == null) {
            // 위 productType Bean Valid 에서 처리
            return true;
        }

        return switch (productType) {
            case INGREDIENT -> ingredient != null && mealkit == null;
            case MEALKIT -> mealkit != null && ingredient == null;
        };
    }

    public Product toProduct() {
        return Product.builder()
                .productType(productType)
                .name(name.strip())
                .description(description)
                .imageUrl(imageUrl)
                .price(price)
                .stock(stock)
                .storage(storage)
                .shelfDays(shelfDays)
                .build();
    }

    public IngredientProduct toIngredientProduct(Product product) {
        return IngredientProduct.builder()
                .product(product)
                .ingredientId(ingredient.ingredientId())
                .unitAmount(ingredient.unitAmount())
                .unit(ingredient.unit().strip())
                .baseQuantity(ingredient.baseQuantity())
                .build();
    }

    public MealkitProduct toMealkitProduct(Product product) {
        return MealkitProduct.builder()
                .product(product)
                .servings(mealkit.servings())
                .build();
    }
}
