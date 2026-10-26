package org.example.takecareofmyfridgebe.domain.payment.repository;

import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, Long> {

    boolean existsByPaymentIdAndEventType(Long paymentId, PaymentEventType eventType);
}
