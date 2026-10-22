package org.example.takecareofmyfridgebe.domain.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

        @Size(max = MAX_DESCRIPTION_LENGTH, message = "상품 설명은 2,000자 이하여야 합니다.")
        String description,

        @Size(max = 500, message = "이미지 URL은 500자 이하여야 합니다.")
        @Pattern(regexp = "^https?://\\S+$", message = "이미지 URL은 http:// 또는 https://로 시작해야 합니다.")
        String imageUrl,

        @NotNull(message = "가격을 입력해 주세요.")
        @Min(value = MIN_PRICE, message = "가격은 1원 이상이어야 합니다.")
        @Max(value = MAX_PRICE, message = "가격은 1,000만 원 이하여야 합니다.")
        Integer price,

        @NotNull(message = "재고를 입력해 주세요.")
        @Min(value = 0, message = "재고는 0 이상이어야 합니다.")
        @Max(value = MAX_STOCK, message = "재고는 10만 개 이하여야 합니다.")
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
    // 주문 금액(가격 x 수량) 계산 시 int 오버플로가 나지 않도록 상한을 둔다
    // 정상가는 1원 이상 (기획 Q-03 결정)
    public static final int MIN_PRICE = 1;
    public static final int MAX_PRICE = 10_000_000;
    public static final int MAX_STOCK = 100_000;
    public static final int MAX_DESCRIPTION_LENGTH = 2_000;

    // 검증 전에 앞뒤 공백을 제거하고, 빈 이미지 URL은 null로 저장한다
    public ProductCreateRequest {
        name = strip(name);
        imageUrl = blankToNull(strip(imageUrl));
    }

    public record IngredientDetail(
            @NotNull(message = "연결할 재료를 선택해 주세요.")
            Long ingredientId,

            @NotNull(message = "용량을 입력해 주세요.")
            @Positive(message = "용량은 0보다 커야 합니다.")
            @Digits(integer = 6, fraction = 2, message = "용량은 정수 6자리, 소수 2자리까지 입력할 수 있습니다.")
            BigDecimal unitAmount,

            @NotBlank(message = "용량 단위를 입력해 주세요.")
            @Size(max = 20, message = "용량 단위는 20자 이하여야 합니다.")
            String unit,

            @NotNull(message = "재료 기본 단위 수량을 입력해 주세요.")
            @Positive(message = "재료 기본 단위 수량은 0보다 커야 합니다.")
            @Digits(integer = 8, fraction = 2, message = "재료 기본 단위 수량은 정수 8자리, 소수 2자리까지 입력할 수 있습니다.")
            BigDecimal baseQuantity
    ) {
        public IngredientDetail {
            unit = strip(unit);
        }
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
                .name(name)
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
                .unit(ingredient.unit())
                .baseQuantity(ingredient.baseQuantity())
                .build();
    }

    public MealkitProduct toMealkitProduct(Product product) {
        return MealkitProduct.builder()
                .product(product)
                .servings(mealkit.servings())
                .build();
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
