package org.example.takecareofmyfridgebe.domain.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.product.entity.IngredientProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.MealkitProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;

import java.math.BigDecimal;

import static java.util.Objects.requireNonNullElse;

// 보낸 필드만 바꾼다(null = 기존 값 유지). description·imageUrl은 빈 문자열을 보내면 비운다.
// 문자열은 검증 전에 앞뒤 공백을 제거하므로, 공백만 보낸 name·unit은 @Size(min = 1)에서 거절된다.
// 상품 종류(productType)는 바꿀 수 없고, 상세는 상품 종류에 맞는 것만 보낼 수 있다.
public record ProductUpdateRequest(
        @Size(min = 1, max = 200, message = "상품명은 1자 이상 200자 이하여야 합니다.")
        String name,

        @Size(max = ProductCreateRequest.MAX_DESCRIPTION_LENGTH, message = "상품 설명은 2,000자 이하여야 합니다.")
        String description,

        @Size(max = 500, message = "이미지 URL은 500자 이하여야 합니다.")
        @Pattern(regexp = "^$|^https?://\\S+$", message = "이미지 URL은 http:// 또는 https://로 시작해야 합니다.")
        String imageUrl,

        @Min(value = ProductCreateRequest.MIN_PRICE, message = "가격은 1원 이상이어야 합니다.")
        @Max(value = ProductCreateRequest.MAX_PRICE, message = "가격은 1,000만 원 이하여야 합니다.")
        Integer price,

        @Min(value = 0, message = "재고는 0 이상이어야 합니다.")
        @Max(value = ProductCreateRequest.MAX_STOCK, message = "재고는 10만 개 이하여야 합니다.")
        Integer stock,

        StorageType storage,

        @Min(value = 1, message = "권장 보관 일수는 1 이상이어야 합니다.")
        Integer shelfDays,

        ProductStatus status,

        @Valid
        IngredientUpdate ingredient,

        @Valid
        MealkitUpdate mealkit
) {
    public ProductUpdateRequest {
        name = strip(name);
        description = strip(description);
        imageUrl = strip(imageUrl);
    }

    public record IngredientUpdate(
            Long ingredientId,

            @Positive(message = "용량은 0보다 커야 합니다.")
            @Digits(integer = 6, fraction = 2, message = "용량은 정수 6자리, 소수 2자리까지 입력할 수 있습니다.")
            BigDecimal unitAmount,

            @Size(min = 1, max = 20, message = "용량 단위는 1자 이상 20자 이하여야 합니다.")
            String unit,

            @Positive(message = "재료 기본 단위 수량은 0보다 커야 합니다.")
            @Digits(integer = 8, fraction = 2, message = "재료 기본 단위 수량은 정수 8자리, 소수 2자리까지 입력할 수 있습니다.")
            BigDecimal baseQuantity
    ) {
        public IngredientUpdate {
            unit = strip(unit);
        }
    }

    public record MealkitUpdate(
            @Min(value = 1, message = "구성 인분은 1 이상이어야 합니다.")
            Integer servings
    ) {
    }

    // 상품 종류와 다른 상세를 보냈는지 확인한다.
    public boolean hasDetailOtherThan(ProductType productType) {
        return switch (productType) {
            case INGREDIENT -> mealkit != null;
            case MEALKIT -> ingredient != null;
        };
    }

    public void applyTo(Product product) {
        product.changeInfo(
                requireNonNullElse(name, product.getName()),
                patchText(description, product.getDescription()),
                patchText(imageUrl, product.getImageUrl())
        );
        product.changeSales(
                requireNonNullElse(price, product.getPrice()),
                requireNonNullElse(stock, product.getStock()),
                requireNonNullElse(status, product.getStatus())
        );
        product.changeStorage(
                requireNonNullElse(storage, product.getStorage()),
                requireNonNullElse(shelfDays, product.getShelfDays())
        );
    }

    public void applyTo(IngredientProduct detail) {
        detail.update(
                requireNonNullElse(ingredient.ingredientId(), detail.getIngredientId()),
                requireNonNullElse(ingredient.unitAmount(), detail.getUnitAmount()),
                requireNonNullElse(ingredient.unit(), detail.getUnit()),
                requireNonNullElse(ingredient.baseQuantity(), detail.getBaseQuantity())
        );
    }

    public void applyTo(MealkitProduct detail) {
        detail.changeServings(requireNonNullElse(mealkit.servings(), detail.getServings()));
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    // null이면 기존 값 유지, 빈 문자열이면 비운다.
    private static String patchText(String value, String current) {
        if (value == null) {
            return current;
        }
        return value.isEmpty() ? null : value;
    }
}
