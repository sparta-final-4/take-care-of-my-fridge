package org.example.takecareofmyfridgebe.domain.payment.repository;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentPin;
import org.example.takecareofmyfridgebe.global.config.jpa.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.jpa.QueryDslConfig;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
class PaymentPinRepositoryTest extends MySqlContainerSupport {

    private final PaymentPinRepository paymentPinRepository;
    private final EntityManager entityManager;

    @Autowired
    PaymentPinRepositoryTest(PaymentPinRepository paymentPinRepository, EntityManager entityManager) {
        this.paymentPinRepository = paymentPinRepository;
        this.entityManager = entityManager;
    }

    @Test
    @DisplayName("결제 비밀번호를 저장하면 회원 ID로 찾을 수 있고 틀린 횟수·잠김이 그대로 읽힌다")
    void findByMemberId_success() {
        PaymentPin pin = PaymentPin.builder().memberId(1L).pinHash("hash-1").build();
        pin.recordFailure(LocalDateTime.of(2026, 11, 4, 12, 0));
        paymentPinRepository.save(pin);
        entityManager.flush();
        entityManager.clear();

        PaymentPin found = paymentPinRepository.findByMemberId(1L).orElseThrow();

        assertThat(found.getPinHash()).isEqualTo("hash-1");
        assertThat(found.getFailedCount()).isEqualTo(1);
        assertThat(found.isLocked()).isFalse();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(paymentPinRepository.findByMemberId(2L)).isEmpty();
    }

    @Test
    @DisplayName("같은 회원의 결제 비밀번호를 두 번 저장하면 UNIQUE 제약으로 거절된다")
    void save_throwException_whenMemberDuplicated() {
        paymentPinRepository.saveAndFlush(PaymentPin.builder().memberId(1L).pinHash("hash-1").build());

        assertThatThrownBy(() -> paymentPinRepository.saveAndFlush(
                PaymentPin.builder().memberId(1L).pinHash("hash-2").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
