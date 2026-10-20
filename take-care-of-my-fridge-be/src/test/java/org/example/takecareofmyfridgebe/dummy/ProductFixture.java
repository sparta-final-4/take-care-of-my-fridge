package org.example.takecareofmyfridgebe.dummy;

import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.product.entity.IngredientProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.MealkitProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

public class ProductFixture {

    public static Product createProduct(Long id, String name, int price) {
        return createProduct(id, ProductType.INGREDIENT, name, price);
    }

    public static Product createMealkitProduct(Long id, String name, int price) {
        return createProduct(id, ProductType.MEALKIT, name, price);
    }

    public static IngredientProduct createIngredientDetail(Product product, BigDecimal unitAmount, String unit) {
        return IngredientProduct.builder()
                .product(product)
                .ingredientId(1L)
                .unitAmount(unitAmount)
                .unit(unit)
                .baseQuantity(BigDecimal.ONE)
                .build();
    }

    public static MealkitProduct createMealkitDetail(Product product, int servings) {
        return MealkitProduct.builder()
                .product(product)
                .servings(servings)
                .build();
    }

    private static Product createProduct(Long id, ProductType productType, String name, int price) {
        Product product = Product.builder()
                .productType(productType)
                .name(name)
                .price(price)
                .stock(10)
                .storage(StorageType.ROOM_TEMP)
                .shelfDays(14)
                .build();
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

}
