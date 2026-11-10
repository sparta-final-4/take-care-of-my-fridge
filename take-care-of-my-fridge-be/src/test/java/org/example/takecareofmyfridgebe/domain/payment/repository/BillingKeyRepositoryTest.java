package org.example.takecareofmyfridgebe.domain.payment.repository;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.payment.entity.BillingKey;
import org.example.takecareofmyfridgebe.domain.payment.entity.BillingKeyStatus;
import org.example.takecareofmyfridgebe.domain.payment.entity.CardType;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.entity.PgProvider;
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
class BillingKeyRepositoryTest extends MySqlContainerSupport {

    private final BillingKeyRepository billingKeyRepository;
    private final PaymentRepository paymentRepository;
    private final EntityManager entityManager;

    @Autowired
    BillingKeyRepositoryTest(BillingKeyRepository billingKeyRepository, PaymentRepository paymentRepository,
                             EntityManager entityManager) {
        this.billingKeyRepository = billingKeyRepository;
        this.paymentRepository = paymentRepository;
        this.entityManager = entityManager;
    }

    @Test
    @DisplayName("등록 카드를 저장하면 표시용 정보와 상태가 그대로 읽힌다")
    void save_success() {
        BillingKey saved = billingKeyRepository.save(billingKey("billing-key-1"));
        flushAndClear();

        BillingKey found = billingKeyRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getMemberId()).isEqualTo(1L);
        assertThat(found.getBillingKey()).isEqualTo("billing-key-1");
        assertThat(found.getCardName()).isEqualTo("신한카드 Deep Dream");
        assertThat(found.getCardLast4()).isEqualTo("1234");
        assertThat(found.getCardType()).isEqualTo(CardType.CREDIT);
        assertThat(found.isDefault()).isTrue();
        assertThat(found.getStatus()).isEqualTo(BillingKeyStatus.ACTIVE);
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("같은 빌링키를 두 번 저장하면 UNIQUE 제약으로 거절된다")
    void save_throwException_whenBillingKeyDuplicated() {
        billingKeyRepository.saveAndFlush(billingKey("billing-key-1"));

        assertThatThrownBy(() -> billingKeyRepository.saveAndFlush(billingKey("billing-key-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("등록 카드로 한 결제는 그 카드를 가리키고, 결제창 결제는 카드가 비어 있다")
    void savePayment_linkBillingKey_whenPaidWithRegisteredCard() {
        Order order = Order.builder().orderNumber("20261104-000001").memberId(1L).totalAmount(10000L).build();
        entityManager.persist(order);
        BillingKey card = billingKeyRepository.save(billingKey("billing-key-1"));
        Payment cardPayment = paymentRepository.save(payment(order, card, "payment-1"));
        Payment windowPayment = paymentRepository.save(payment(order, null, "payment-2"));
        flushAndClear();

        assertThat(paymentRepository.findById(cardPayment.getId()).orElseThrow().getBillingKey().getId())
                .isEqualTo(card.getId());
        assertThat(paymentRepository.findById(windowPayment.getId()).orElseThrow().getBillingKey()).isNull();
    }

    private BillingKey billingKey(String key) {
        return BillingKey.builder().memberId(1L).billingKey(key).cardName("신한카드 Deep Dream").cardLast4("1234")
                .cardType(CardType.CREDIT).isDefault(true).build();
    }

    private Payment payment(Order order, BillingKey card, String portonePaymentId) {
        return Payment.builder().order(order).billingKey(card).portonePaymentId(portonePaymentId).amount(10000L)
                .pgProvider(PgProvider.KAKAOPAY).expiresAt(LocalDateTime.now().plusMinutes(10)).build();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
