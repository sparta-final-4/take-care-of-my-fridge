package org.example.takecareofmyfridgebe.domain.product.repository;

import jakarta.persistence.LockModeType;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, ProductRepositoryCustom {

    Optional<Product> findByIdAndStatusIn(Long id, Collection<ProductStatus> statuses);

    // 상품들을 SELECT ... FOR UPDATE로 잠근다. 여러 주문이 잠그는 순서가 엇갈리지 않도록 ID 순으로 읽는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);

    // 관리자 수정과 주문의 재고 차감이 겹쳐 재고가 덮어써지지 않도록 같은 행 잠금을 건다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}
