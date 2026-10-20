package org.example.takecareofmyfridgebe.domain.product.repository;

import jakarta.persistence.LockModeType;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByStatusIn(Collection<ProductStatus> statuses, Pageable pageable);

    Optional<Product> findByIdAndStatusIn(Long id, Collection<ProductStatus> statuses);

    // 상품들을 SELECT ... FOR UPDATE로 잠근다. 여러 주문이 잠그는 순서가 엇갈리지 않도록 ID 순으로 읽는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);
}
