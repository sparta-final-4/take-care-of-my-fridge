package org.example.takecareofmyfridgebe.domain.payment.repository;

import jakarta.persistence.LockModeType;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("select p from Payment p join fetch p.order where p.portonePaymentId = :portonePaymentId")
    Optional<Payment> findWithOrderByPortonePaymentId(@Param("portonePaymentId") String portonePaymentId);

    // 확정 요청과 웹훅이 동시에 들어와도 한 번만 상태가 바뀌도록 행 락을 건다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p join fetch p.order where p.portonePaymentId = :portonePaymentId")
    Optional<Payment> findWithOrderByPortonePaymentIdForUpdate(@Param("portonePaymentId") String portonePaymentId);
}
