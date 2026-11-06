package org.example.takecareofmyfridgebe.domain.product.repository;

import org.example.takecareofmyfridgebe.domain.product.entity.MealkitProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MealkitProductRepository extends JpaRepository<MealkitProduct, Long> {

    Optional<MealkitProduct> findByProductId(Long productId);

    List<MealkitProduct> findByProductIdIn(Collection<Long> productIds);

    // 상품 상태로 고른 밀키트를 상품과 함께 (상담 밀키트 추천)
    @Query("SELECT mp FROM MealkitProduct mp JOIN FETCH mp.product p WHERE p.status IN :statuses")
    List<MealkitProduct> findWithProductByStatusIn(@Param("statuses") Collection<ProductStatus> statuses);

    // 상품 ID와 상태로 고른 밀키트를 상품과 함께 (저장된 상담 밀키트 카드 다시 보여 주기)
    @Query("SELECT mp FROM MealkitProduct mp JOIN FETCH mp.product p "
            + "WHERE p.id IN :productIds AND p.status IN :statuses")
    List<MealkitProduct> findWithProductByProductIdIn(
            @Param("productIds") Collection<Long> productIds,
            @Param("statuses") Collection<ProductStatus> statuses);
}
