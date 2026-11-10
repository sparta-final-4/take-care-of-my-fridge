package org.example.takecareofmyfridgebe.domain.sale.service;

import java.util.List;
import java.util.Map;
import org.example.takecareofmyfridgebe.domain.notification.entity.NotificationType;
import org.example.takecareofmyfridgebe.domain.notification.event.NotificationRequestedMessage;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventItem;
import org.springframework.stereotype.Service;

// 번개 드롭 시작 알림 메시지를 만든다. 받는 사람은 정지되지 않은 일반 회원 전체다.
// 읽기는 퍼사드가 하고, 여기서는 받은 값으로 받는 사람마다 메시지를 만든다.
@Service
public class FlashDropNotificationService {

    public List<NotificationRequestedMessage> createMessages(
            List<SaleEventItem> items, Map<Long, String> productNames, List<Long> memberIds) {
        return items.stream()
                .flatMap(item -> memberIds.stream()
                        .map(memberId -> toMessage(item, productNames.get(item.getProductId()), memberId)))
                .toList();
    }

    // 같은 행사·상품의 시작 알림은 한 회원에게 한 번만 남도록 중복 방지 키를 붙인다.
    // 줄바꿈으로 세 줄을 나눈다. 번개 드롭 시작, 할인율·한정 수량, 상품명 순서다.
    private NotificationRequestedMessage toMessage(SaleEventItem item, String productName, Long memberId) {
        String content = "번개 드롭 시작\n%d%% 할인 · %d개 한정\n%s".formatted(
                item.getDiscountRate(), item.getLimitedQuantity(), productName);
        String dedupeKey = "FLASH_DROP_STARTED:%d:%d".formatted(item.getSaleEvent().getId(), item.getProductId());
        return new NotificationRequestedMessage(
                memberId, NotificationType.FLASH_DROP, content, "/products/" + item.getProductId(), dedupeKey);
    }
}
