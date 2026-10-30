package org.example.takecareofmyfridgebe.domain.product.repository;

import org.example.takecareofmyfridgebe.domain.product.entity.MealkitProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MealkitProductRepository extends JpaRepository<MealkitProduct, Long> {

    Optional<MealkitProduct> findByProductId(Long productId);

    List<MealkitProduct> findByProductIdIn(Collection<Long> productIds);
}
