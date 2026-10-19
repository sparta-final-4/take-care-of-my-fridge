package org.example.takecareofmyfridgebe.domain.fridge.repository;

import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FridgeRepository extends JpaRepository<Fridge, Long>, FridgeRepositoryCustom {

    @EntityGraph(attributePaths = "ingredient")
    Optional<Fridge> findByIdAndMemberId(Long id, Long memberId);

    @Query("select f.rawName from Fridge f where f.id = :fridgeId and f.memberId = :memberId")
    Optional<String> findRawNameByIdAndMemberId(
            @Param("fridgeId") Long fridgeId,
            @Param("memberId") Long memberId
    );
}
