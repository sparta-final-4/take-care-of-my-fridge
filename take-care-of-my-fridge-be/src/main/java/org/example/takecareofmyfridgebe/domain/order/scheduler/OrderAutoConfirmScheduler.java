package org.example.takecareofmyfridgebe.domain.order.scheduler;

import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.order.service.OrderAutoConfirmService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderAutoConfirmScheduler {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final OrderAutoConfirmService autoConfirmService;

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void confirmDueOrders() {
        confirmDueOrders(LocalDateTime.now(ZONE));
    }

    void confirmDueOrders(LocalDateTime now) {
        autoConfirmService.confirmDue(now.minusDays(7));
    }
}
