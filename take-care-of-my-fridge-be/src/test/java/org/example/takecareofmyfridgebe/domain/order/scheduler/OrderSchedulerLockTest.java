package org.example.takecareofmyfridgebe.domain.order.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderSchedulerLockTest {

    @Test
    @DisplayName("주문 만료 스케줄러는 서버 간 잠금을 건다")
    void locksExpireSchedulerAcrossServers() throws NoSuchMethodException {
        SchedulerLock lock = OrderExpireScheduler.class.getMethod("expireAbandonedOrders")
                .getAnnotation(SchedulerLock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.name()).isEqualTo("orderExpire");
    }

    @Test
    @DisplayName("자동 구매 확정 스케줄러는 서버 간 잠금을 건다")
    void locksAutoConfirmSchedulerAcrossServers() throws NoSuchMethodException {
        SchedulerLock lock = OrderAutoConfirmScheduler.class.getMethod("confirmDueOrders")
                .getAnnotation(SchedulerLock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.name()).isEqualTo("orderAutoConfirm");
    }
}
