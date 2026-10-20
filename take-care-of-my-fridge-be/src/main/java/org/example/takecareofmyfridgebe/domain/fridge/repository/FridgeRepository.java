package org.example.takecareofmyfridgebe.domain.fridge.repository;

import jakarta.persistence.LockModeType;
import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FridgeRepository extends JpaRepository<Fridge, Long>, FridgeRepositoryCustom {

    @EntityGraph(attributePaths = "ingredient")
    Optional<Fridge> findByIdAndMemberId(Long id, Long memberId);

    // 같은 재료를 동시에 사용·삭제할 때 수량을 덮어쓰지 않도록 행을 잠근다.
    // 공용 재료(ingredients) 행까지 잠그지 않도록 ingredient는 fetch join하지 않는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Fridge f where f.id = :fridgeId and f.memberId = :memberId")
    Optional<Fridge> findForUpdateByIdAndMemberId(
            @Param("fridgeId") Long fridgeId,
            @Param("memberId") Long memberId
    );

    // 요리 완료 시 특정 재료를 소비기한 빠른 순(같으면 id 순)으로 잠금 조회한다.
    // 공용 재료(ingredients) 행까지 잠그지 않도록 ingredient는 fetch join하지 않는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Fridge f where f.memberId = :memberId and f.ingredient.id = :ingredientId order by f.expirationDate asc, f.id asc")
    List<Fridge> findForUpdateByMemberIdAndIngredientId(
            @Param("memberId") Long memberId,
            @Param("ingredientId") Long ingredientId
    );

    @Query("select f.rawName from Fridge f where f.id = :fridgeId and f.memberId = :memberId")
    Optional<String> findRawNameByIdAndMemberId(
            @Param("fridgeId") Long fridgeId,
            @Param("memberId") Long memberId
    );
}
