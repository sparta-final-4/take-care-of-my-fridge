package org.example.takecareofmyfridgebe.domain.product.repository;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.product.entity.MealkitProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.global.config.jpa.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.jpa.QueryDslConfig;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * MealkitProductRepository의 상태·상품 ID 조회(MySQL Testcontainers) 테스트.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
class MealkitProductRepositoryTest extends MySqlContainerSupport {

    @Autowired
    private MealkitProductRepository mealkitProductRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("상태로 밀키트를 찾으면 그 상태의 밀키트만 상품과 함께 레시피 ID까지 돌려준다")
    void findsMealkitsWithProductAndRecipe_byStatuses() {
        Product doenjang = mealkit("된장국", ProductStatus.ON_SALE, 544L);
        Product noRecipe = mealkit("모둠 전골", ProductStatus.ON_SALE, null);
        mealkit("품절 해장국", ProductStatus.SOLD_OUT, 543L);
        mealkit("판매 중지 찜", ProductStatus.STOPPED, 539L);
        em.flush();
        em.clear();

        List<MealkitProduct> found = mealkitProductRepository.findWithProductByStatusIn(List.of(ProductStatus.ON_SALE));

        assertThat(found).extracting(mealkit -> mealkit.getProduct().getId(), MealkitProduct::getRecipeId)
                .containsExactlyInAnyOrder(tuple(doenjang.getId(), 544L), tuple(noRecipe.getId(), null));
        assertThat(found).allSatisfy(mealkit -> assertThat(Hibernate.isInitialized(mealkit.getProduct())).isTrue());
    }

    @Test
    @DisplayName("상품 ID로 밀키트를 찾으면 고른 상태의 밀키트만 돌려주고 판매 중지는 뺀다")
    void findsMealkits_byProductIdsAndStatuses() {
        Product onSale = mealkit("된장국", ProductStatus.ON_SALE, 544L);
        Product soldOut = mealkit("황태해장국", ProductStatus.SOLD_OUT, 543L);
        Product stopped = mealkit("부추 콩가루 찜", ProductStatus.STOPPED, 539L);
        em.flush();
        em.clear();

        List<MealkitProduct> found = mealkitProductRepository.findWithProductByProductIdIn(
                List.of(onSale.getId(), soldOut.getId(), stopped.getId()),
                List.of(ProductStatus.ON_SALE, ProductStatus.SOLD_OUT));

        assertThat(found).extracting(mealkit -> mealkit.getProduct().getId())
                .containsExactlyInAnyOrder(onSale.getId(), soldOut.getId());
    }

    private Product mealkit(String name, ProductStatus status, Long recipeId) {
        Product product = Product.builder()
                .productType(ProductType.MEALKIT)
                .name(name)
                .price(12900)
                .stock(status == ProductStatus.SOLD_OUT ? 0 : 10)
                .storage(StorageType.REFRIGERATED)
                .shelfDays(3)
                .status(status)
                .build();
        em.persist(product);
        em.persist(MealkitProduct.builder()
                .product(product)
                .servings(2)
                .recipeId(recipeId)
                .build());
        return product;
    }
}
