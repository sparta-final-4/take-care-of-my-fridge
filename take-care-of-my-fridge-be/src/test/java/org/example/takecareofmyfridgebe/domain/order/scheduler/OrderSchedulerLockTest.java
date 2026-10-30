package org.example.takecareofmyfridgebe.domain.order.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;

class OrderSchedulerLockTest {

    @Test
    void 주문_만료_스케줄러는_서버_간_잠금을_건다() throws NoSuchMethodException {
        SchedulerLock lock = OrderExpireScheduler.class.getMethod("expireAbandonedOrders")
                .getAnnotation(SchedulerLock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.name()).isEqualTo("orderExpire");
    }

    @Test
    void 자동_구매_확정_스케줄러는_서버_간_잠금을_건다() throws NoSuchMethodException {
        SchedulerLock lock = OrderAutoConfirmScheduler.class.getMethod("confirmDueOrders")
                .getAnnotation(SchedulerLock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.name()).isEqualTo("orderAutoConfirm");
    }
}
