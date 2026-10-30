package org.example.takecareofmyfridgebe.domain.forecast.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;

class ForecastSchedulerLockTest {

    @Test
    void 예측_생성_스케줄러는_서버_간_잠금을_건다() throws NoSuchMethodException {
        SchedulerLock lock = ForecastPredictionScheduler.class.getMethod("predictDueWeek")
                .getAnnotation(SchedulerLock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.name()).isEqualTo("forecastPrediction");
    }

    @Test
    void 실적_확정_스케줄러는_서버_간_잠금을_건다() throws NoSuchMethodException {
        SchedulerLock lock = ForecastActualScheduler.class.getMethod("confirmDueWeeks")
                .getAnnotation(SchedulerLock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.name()).isEqualTo("forecastActual");
    }
}
