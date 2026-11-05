package org.example.takecareofmyfridgebe.domain.payment.repository;

import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentPin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentPinRepository extends JpaRepository<PaymentPin, Long> {

    Optional<PaymentPin> findByMemberId(Long memberId);
}
