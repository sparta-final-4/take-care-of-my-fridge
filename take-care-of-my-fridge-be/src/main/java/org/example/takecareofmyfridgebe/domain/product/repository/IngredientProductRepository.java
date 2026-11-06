package org.example.takecareofmyfridgebe.domain.product.repository;

import org.example.takecareofmyfridgebe.domain.product.entity.IngredientProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IngredientProductRepository extends JpaRepository<IngredientProduct, Long> {

    Optional<IngredientProduct> findByProductId(Long productId);

    List<IngredientProduct> findByProductIdIn(Collection<Long> productIds);

    // 재료 ID로 상품 연결을 상품과 함께 읽는다 (상담 카드 장볼 재료의 상품). 상품 상태가 statuses인 것만
    @Query("SELECT ip FROM IngredientProduct ip JOIN FETCH ip.product p "
            + "WHERE ip.ingredientId IN :ingredientIds AND p.status IN :statuses")
    List<IngredientProduct> findWithProductByIngredientIdIn(
            @Param("ingredientIds") Collection<Long> ingredientIds,
            @Param("statuses") Collection<ProductStatus> statuses);
}
