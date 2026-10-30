package org.example.takecareofmyfridgebe.domain.order.scheduler;

import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.example.takecareofmyfridgebe.domain.order.service.OrderAutoConfirmService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderAutoConfirmScheduler {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final OrderAutoConfirmService autoConfirmService;

    // 서버가 여러 대여도 한 서버만 실행한다. 주문 행 잠금 덕에 함께 돌아도 결과는 같고, 같은 주문을 두 번 처리하는 일만 막는다
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "orderAutoConfirm", lockAtMostFor = "30m")
    public void confirmDueOrders() {
        confirmDueOrders(LocalDateTime.now(ZONE));
    }

    void confirmDueOrders(LocalDateTime now) {
        autoConfirmService.confirmDue(now.minusDays(7));
    }
}
