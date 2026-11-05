package org.example.takecareofmyfridgebe.domain.payment.service;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentPinSetRequest;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentPin;
import org.example.takecareofmyfridgebe.domain.payment.repository.PaymentPinRepository;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.PasswordEncoderConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 틀린 횟수가 예외와 함께 롤백되지 않고 커밋되는지, 동시에 틀려도 5번 제한을 넘지 못하는지 실제 MySQL에서 확인한다.
// 테스트 자체 트랜잭션을 끄고(NOT_SUPPORTED) 서비스의 트랜잭션 경계로 커밋된 상태를 다시 읽어 확인한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, PasswordEncoderConfig.class, PaymentPinService.class, MemberRowLocker.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentPinServiceConcurrencyTest extends MySqlContainerSupport {

    private static final String PIN = "271828";
    private static final String WRONG_PIN = "000123";
    private static final int THREADS = 8;

    private final PaymentPinService paymentPinService;
    private final PaymentPinRepository paymentPinRepository;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    PaymentPinServiceConcurrencyTest(PaymentPinService paymentPinService, PaymentPinRepository paymentPinRepository,
                                     EntityManager entityManager, PlatformTransactionManager transactionManager) {
        this.paymentPinService = paymentPinService;
        this.paymentPinRepository = paymentPinRepository;
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    private Long memberId;

    // 공유 MySQL 컨테이너의 다른 테스트 데이터는 건드리지 않고 이 테스트가 만든 회원과 결제 비밀번호만 지운다
    @AfterEach
    void cleanUp() {
        if (memberId == null) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> List.of(
                        "delete from PaymentPin p where p.memberId = :memberId",
                        "delete from Member m where m.id = :memberId")
                .forEach(jpql -> entityManager.createQuery(jpql).setParameter("memberId", memberId).executeUpdate()));
    }

    @Test
    @DisplayName("비밀번호가 틀려 예외가 나도 틀린 횟수는 커밋된다")
    void verify_commitFailedCount_whenWrong() {
        Long memberId = saveMember();
        paymentPinService.set(memberId, new PaymentPinSetRequest(PIN));

        assertThatThrownBy(() -> paymentPinService.verify(memberId, WRONG_PIN))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_PIN_MISMATCH);

        assertThat(savedPin(memberId).getFailedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("틀린 비밀번호가 동시에 여러 번 들어와도 틀린 횟수가 빠짐없이 쌓여 5번에서 잠기고 더 늘지 않는다")
    void verify_lockAtFive_whenWrongConcurrently() throws Exception {
        Long memberId = saveMember();
        paymentPinService.set(memberId, new PaymentPinSetRequest(PIN));

        List<ErrorCode> results = runConcurrently(() -> paymentPinService.verify(memberId, WRONG_PIN));

        PaymentPin pin = savedPin(memberId);
        assertThat(pin.getFailedCount()).isEqualTo(5);
        assertThat(pin.isLocked()).isTrue();
        assertThat(results).filteredOn(code -> code == ErrorCode.PAYMENT_PIN_MISMATCH).hasSize(4);
        assertThat(results).filteredOn(code -> code == ErrorCode.PAYMENT_PIN_LOCKED).hasSize(THREADS - 4);
    }

    private List<ErrorCode> runConcurrently(Runnable task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ErrorCode>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        task.run();
                        return null;
                    } catch (BusinessException e) {
                        return e.getErrorCode();
                    }
                }));
            }
            start.countDown();
            List<ErrorCode> results = new ArrayList<>();
            for (Future<ErrorCode> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private PaymentPin savedPin(Long memberId) {
        return transactionTemplate.execute(status -> paymentPinRepository.findByMemberId(memberId).orElseThrow());
    }

    private Long saveMember() {
        memberId = transactionTemplate.execute(status -> {
            Member member = Member.builder().email("pin-concurrency@example.com").passwordHash("hash")
                    .nickname("동시성회원").build();
            entityManager.persist(member);
            return member.getId();
        });
        return memberId;
    }
}
