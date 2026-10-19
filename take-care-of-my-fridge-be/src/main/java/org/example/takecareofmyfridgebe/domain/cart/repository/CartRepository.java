package org.example.takecareofmyfridgebe.domain.cart.repository;

import java.util.Optional;
import org.example.takecareofmyfridgebe.domain.cart.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByMemberId(Long memberId);
}
