package org.example.takecareofmyfridgebe.domain.order.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import org.example.takecareofmyfridgebe.domain.order.service.OrderAutoConfirmService;
import org.junit.jupiter.api.Test;

class OrderAutoConfirmSchedulerTest {

    @Test
    void usesExactlySevenDaysAfterPaymentAsCutoff() {
        OrderAutoConfirmService service = mock(OrderAutoConfirmService.class);
        OrderAutoConfirmScheduler scheduler = new OrderAutoConfirmScheduler(service);

        scheduler.confirmDueOrders(LocalDateTime.of(2026, 10, 29, 10, 0));

        verify(service).confirmDue(LocalDateTime.of(2026, 10, 22, 10, 0));
    }
}
