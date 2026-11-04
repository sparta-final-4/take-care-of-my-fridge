package org.example.takecareofmyfridgebe.domain.payment.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.example.takecareofmyfridgebe.domain.payment.facade.BillingKeyFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BillingKeyPgDeleteSchedulerTest {

    @Mock
    private BillingKeyFacade billingKeyFacade;

    @InjectMocks
    private BillingKeyPgDeleteScheduler scheduler;

    @Test
    @DisplayName("서버가 여러 대여도 한 대만 실행하도록 서버 간 잠금을 건다")
    void retryPgDeletes_haveSchedulerLock() throws NoSuchMethodException {
        SchedulerLock lock = BillingKeyPgDeleteScheduler.class.getMethod("retryPgDeletes")
                .getAnnotation(SchedulerLock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.name()).isEqualTo("billingKeyPgDelete");
    }

    @Test
    @DisplayName("실행하면 PortOne 빌링키 삭제 재시도를 맡긴다")
    void retryPgDeletes_delegateToFacade() {
        scheduler.retryPgDeletes();

        verify(billingKeyFacade).retryPgDeletes();
    }

    @Test
    @DisplayName("재시도 중 예외가 나도 스케줄러 밖으로 던지지 않는다")
    void retryPgDeletes_notThrowException_whenRetryFails() {
        willThrow(new RuntimeException("DB 오류")).given(billingKeyFacade).retryPgDeletes();

        assertThatCode(() -> scheduler.retryPgDeletes()).doesNotThrowAnyException();
    }
}
