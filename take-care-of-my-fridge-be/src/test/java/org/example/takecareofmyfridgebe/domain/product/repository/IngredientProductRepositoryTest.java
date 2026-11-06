package org.example.takecareofmyfridgebe.domain.product.repository;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.product.entity.IngredientProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IngredientProductRepository의 재료 ID 조회(MySQL Testcontainers) 테스트.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
class IngredientProductRepositoryTest extends MySqlContainerSupport {

    private static final long GREEN_ONION = 3L;
    private static final long TOFU = 21L;
    private static final long CARROT = 16L;

    @Autowired
    private IngredientProductRepository ingredientProductRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("재료 ID로 찾으면 고른 상태의 상품만 상품과 함께 돌려주고, 다른 재료와 판매 중지 상품은 빼며 재료 하나의 상품 여러 개를 모두 돌려준다")
    void findsLinksWithProduct_byIngredientIdsAndStatuses() {
        Product greenOnion = link(GREEN_ONION, "대파 1단", ProductStatus.ON_SALE, "1", "단");
        Product bigGreenOnion = link(GREEN_ONION, "대파 2단", ProductStatus.SOLD_OUT, "2", "단");
        link(TOFU, "판매 중지 두부", ProductStatus.STOPPED, "1", "모");
        link(CARROT, "당근 3개입", ProductStatus.ON_SALE, "3", "개");
        em.flush();
        em.clear();

        List<IngredientProduct> found = ingredientProductRepository.findWithProductByIngredientIdIn(
                List.of(GREEN_ONION, TOFU), List.of(ProductStatus.ON_SALE, ProductStatus.SOLD_OUT));

        assertThat(found).extracting(link -> link.getProduct().getId())
                .containsExactlyInAnyOrder(greenOnion.getId(), bigGreenOnion.getId());
        assertThat(found).allSatisfy(link -> assertThat(Hibernate.isInitialized(link.getProduct())).isTrue());
        assertThat(found).extracting(IngredientProduct::getUnit).containsOnly("단");
    }

    private Product link(long ingredientId, String name, ProductStatus status, String amount, String unit) {
        Product product = Product.builder()
                .productType(ProductType.INGREDIENT)
                .name(name)
                .price(3000)
                .stock(status == ProductStatus.SOLD_OUT ? 0 : 10)
                .storage(StorageType.REFRIGERATED)
                .shelfDays(7)
                .status(status)
                .build();
        em.persist(product);
        em.persist(IngredientProduct.builder()
                .product(product)
                .ingredientId(ingredientId)
                .unitAmount(new BigDecimal(amount))
                .unit(unit)
                .baseQuantity(BigDecimal.ONE)
                .build());
        return product;
    }
}
