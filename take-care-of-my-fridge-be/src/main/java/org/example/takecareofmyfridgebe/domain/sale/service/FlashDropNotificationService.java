package org.example.takecareofmyfridgebe.domain.sale.service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.example.takecareofmyfridgebe.domain.notification.entity.NotificationType;
import org.example.takecareofmyfridgebe.domain.notification.event.NotificationRequestedMessage;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventItem;
import org.example.takecareofmyfridgebe.domain.sale.event.FlashDropNotificationEvent.Moment;
import org.springframework.stereotype.Service;

// 번개 드롭 알림 메시지를 만든다. 받는 사람은 정지되지 않은 일반 회원 전체다.
// 읽기는 퍼사드가 하고, 여기서는 받은 값으로 받는 사람마다 메시지를 만든다.
@Service
public class FlashDropNotificationService {

    private static final DateTimeFormatter STARTS_AT_FORMAT = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    public List<NotificationRequestedMessage> createMessages(
            Moment moment, List<SaleEventItem> items, Map<Long, String> productNames, List<Long> memberIds) {
        return items.stream()
                .flatMap(item -> memberIds.stream()
                        .map(memberId -> toMessage(moment, item, productNames.get(item.getProductId()), memberId)))
                .toList();
    }

    // 같은 행사·상품의 같은 시점 알림은 한 회원에게 한 번만 남도록 중복 방지 키를 붙인다.
    private NotificationRequestedMessage toMessage(Moment moment, SaleEventItem item, String productName, Long memberId) {
        String content = switch (moment) {
            case CREATED -> "%s %d%% 번개 드롭 · %s 시작 · %d개 한정".formatted(
                    productName, item.getDiscountRate(),
                    item.getSaleEvent().getStartsAt().format(STARTS_AT_FORMAT), item.getLimitedQuantity());
            case STARTED -> "%s %d%% 번개 드롭 시작 · %d개 한정".formatted(
                    productName, item.getDiscountRate(), item.getLimitedQuantity());
        };
        String dedupeKey = "FLASH_DROP_%s:%d:%d".formatted(moment, item.getSaleEvent().getId(), item.getProductId());
        return new NotificationRequestedMessage(
                memberId, NotificationType.FLASH_DROP, content, "/products/" + item.getProductId(), dedupeKey);
    }
}
