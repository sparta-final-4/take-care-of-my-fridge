package org.example.takecareofmyfridgebe.domain.fridge.repository;

import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FridgeRepository extends JpaRepository<Fridge, Long>, FridgeRepositoryCustom {

    @EntityGraph(attributePaths = "ingredient")
    Optional<Fridge> findByIdAndMemberId(Long id, Long memberId);
}
