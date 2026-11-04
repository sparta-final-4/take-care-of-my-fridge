package org.example.takecareofmyfridgebe.domain.payment.service;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.payment.entity.BillingKeyStatus;
import org.example.takecareofmyfridgebe.domain.payment.entity.CardType;
import org.example.takecareofmyfridgebe.domain.payment.port.BillingKeyInfo;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

// 같은 회원의 카드 변경이 동시에 들어와도 회원 행 잠금으로 하나씩 처리돼 대표 카드가 하나만 남는지 실제 MySQL에서 확인한다.
// 테스트 자체 트랜잭션을 끄고(NOT_SUPPORTED) 서비스의 트랜잭션 경계로 커밋된 상태를 다시 읽어 확인한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, BillingKeyService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BillingKeyServiceConcurrencyTest extends MySqlContainerSupport {

    private static final int THREADS = 4;

    private final BillingKeyService billingKeyService;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    BillingKeyServiceConcurrencyTest(BillingKeyService billingKeyService, EntityManager entityManager,
                                     PlatformTransactionManager transactionManager) {
        this.billingKeyService = billingKeyService;
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> List.of("delete from BillingKey", "delete from Member")
                .forEach(jpql -> entityManager.createQuery(jpql).executeUpdate()));
    }

    @Test
    @DisplayName("두 카드를 동시에 번갈아 대표로 지정해도 대표 카드는 하나만 남는다")
    void setDefault_keepSingleDefault_whenCalledConcurrently() throws Exception {
        Long memberId = saveMember();
        Long first = billingKeyService.register(memberId, info(memberId, "billing-key-1")).cardId();
        Long second = billingKeyService.register(memberId, info(memberId, "billing-key-2")).cardId();

        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            Long cardId = i % 2 == 0 ? first : second;
            tasks.add(() -> billingKeyService.setDefault(memberId, cardId));
        }
        runConcurrently(tasks);

        assertThat(defaultCount(memberId)).isEqualTo(1L);
    }

    @Test
    @DisplayName("대표 카드 삭제와 다른 카드의 대표 지정이 겹쳐도 남은 카드 중 대표 카드는 하나다")
    void deleteAndSetDefault_keepSingleDefault_whenOverlapping() throws Exception {
        Long memberId = saveMember();
        Long first = billingKeyService.register(memberId, info(memberId, "billing-key-1")).cardId();
        Long second = billingKeyService.register(memberId, info(memberId, "billing-key-2")).cardId();
        billingKeyService.register(memberId, info(memberId, "billing-key-3"));

        runConcurrently(List.of(
                () -> billingKeyService.delete(memberId, first, LocalDateTime.now()),
                () -> billingKeyService.setDefault(memberId, second)));

        assertThat(defaultCount(memberId)).isEqualTo(1L);
    }

    private long defaultCount(Long memberId) {
        return transactionTemplate.execute(status -> entityManager.createQuery(
                        "select count(b) from BillingKey b where b.memberId = :memberId and b.status = :status "
                                + "and b.isDefault = true", Long.class)
                .setParameter("memberId", memberId)
                .setParameter("status", BillingKeyStatus.ACTIVE)
                .getSingleResult());
    }

    private Long saveMember() {
        return transactionTemplate.execute(status -> {
            Member member = Member.builder().email("concurrency@example.com").passwordHash("hash")
                    .nickname("동시성회원").build();
            entityManager.persist(member);
            return member.getId();
        });
    }

    private BillingKeyInfo info(Long memberId, String billingKey) {
        return new BillingKeyInfo(billingKey, true, "member-" + memberId, "신한카드", "53275012****1234",
                CardType.CREDIT);
    }

    private <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = tasks.stream()
                    .map(task -> executor.submit(() -> {
                        start.await();
                        return task.call();
                    }))
                    .toList();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
