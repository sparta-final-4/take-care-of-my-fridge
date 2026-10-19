package org.example.takecareofmyfridgebe.dummy;

import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.springframework.test.util.ReflectionTestUtils;

public final class ProductFixture {

    private ProductFixture() {}

    public static Product createProduct(Long id, String name, int price) {
        Product product = Product.builder()
                .productType(ProductType.INGREDIENT)
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
