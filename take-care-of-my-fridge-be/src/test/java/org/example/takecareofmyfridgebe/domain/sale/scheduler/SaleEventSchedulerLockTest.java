package org.example.takecareofmyfridgebe.domain.sale.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SaleEventSchedulerLockTest {

    @Test
    @DisplayName("할인 행사 열기·닫기 스케줄러는 서버 간 잠금을 건다")
    void locksOpenAndCloseSchedulerAcrossServers() throws NoSuchMethodException {
        SchedulerLock lock = SaleEventScheduler.class.getMethod("openAndCloseDueEvents")
                .getAnnotation(SchedulerLock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.name()).isEqualTo("saleEventOpenClose");
    }
}
