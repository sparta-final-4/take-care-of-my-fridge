package org.example.takecareofmyfridgebe.domain.product.repository;

import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByStatusIn(Collection<ProductStatus> statuses, Pageable pageable);
}
