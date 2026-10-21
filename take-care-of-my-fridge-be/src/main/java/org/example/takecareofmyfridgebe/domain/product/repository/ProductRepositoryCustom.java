package org.example.takecareofmyfridgebe.domain.product.repository;

import org.example.takecareofmyfridgebe.domain.product.dto.ProductListRequest;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;

public interface ProductRepositoryCustom {

    Page<Product> findProductsByCondition(ProductListRequest condition, Collection<ProductStatus> statuses, Pageable pageable);
}
