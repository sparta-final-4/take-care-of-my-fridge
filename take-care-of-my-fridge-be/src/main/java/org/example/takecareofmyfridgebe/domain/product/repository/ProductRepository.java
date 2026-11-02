package org.example.takecareofmyfridgebe.domain.product.repository;

import jakarta.persistence.LockModeType;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, ProductRepositoryCustom {

    List<Product> findByStatusOrderByIdAsc(ProductStatus status, Pageable pageable);

    Optional<Product> findByIdAndStatusIn(Long id, Collection<ProductStatus> statuses);

    // 상품들을 SELECT ... FOR UPDATE로 잠근다. 여러 주문이 잠그는 순서가 엇갈리지 않도록 ID 순으로 읽는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);

    // 관리자 수정과 주문의 재고 차감이 겹쳐 재고가 덮어써지지 않도록 같은 행 잠금을 건다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    // 상태를 UPDATE 한 번으로 바꾼다. 주문 생성이 잡은 상품 행 잠금이 풀린 뒤 실행되므로 그 뒤 주문은 바뀐 상태를 본다.
    // 벌크 UPDATE는 @LastModifiedDate를 거치지 않아 updatedAt을 직접 넣는다. 같은 상태로 다시 바꿔도 updatedAt이 바뀌어 1을 반환한다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Product p set p.status = :status, p.updatedAt = :now where p.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") ProductStatus status, @Param("now") LocalDateTime now);
}
