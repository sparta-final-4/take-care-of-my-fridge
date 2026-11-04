package org.example.takecareofmyfridgebe.domain.payment.repository;

import org.example.takecareofmyfridgebe.domain.payment.entity.BillingKey;
import org.example.takecareofmyfridgebe.domain.payment.entity.BillingKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BillingKeyRepository extends JpaRepository<BillingKey, Long> {

    long countByMemberIdAndStatus(Long memberId, BillingKeyStatus status);

    Optional<BillingKey> findByBillingKey(String billingKey);

    // 대표 카드 먼저, 그다음 최근 등록순
    @Query("select b from BillingKey b where b.memberId = :memberId and b.status = :status "
            + "order by b.isDefault desc, b.createdAt desc, b.id desc")
    List<BillingKey> findCards(@Param("memberId") Long memberId, @Param("status") BillingKeyStatus status);
}
