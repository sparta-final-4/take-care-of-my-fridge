package org.example.takecareofmyfridgebe.domain.sale.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;

class SaleEventSchedulerLockTest {

    @Test
    void 할인_행사_열기_닫기_스케줄러는_서버_간_잠금을_건다() throws NoSuchMethodException {
        SchedulerLock lock = SaleEventScheduler.class.getMethod("openAndCloseDueEvents")
                .getAnnotation(SchedulerLock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.name()).isEqualTo("saleEventOpenClose");
    }
}
