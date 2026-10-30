package org.example.takecareofmyfridgebe.domain.product.repository;

import org.example.takecareofmyfridgebe.domain.product.entity.IngredientProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IngredientProductRepository extends JpaRepository<IngredientProduct, Long> {

    Optional<IngredientProduct> findByProductId(Long productId);

    List<IngredientProduct> findByProductIdIn(Collection<Long> productIds);
}
