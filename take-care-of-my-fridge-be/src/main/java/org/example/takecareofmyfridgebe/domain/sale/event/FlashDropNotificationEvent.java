package org.example.takecareofmyfridgebe.domain.sale.event;

import java.util.List;

// 번개 드롭이 시작됐다는 사실만 싣는다. 받는 사람과 문구는 커밋 뒤에 정한다.
public record FlashDropNotificationEvent(
        List<Long> saleEventIds
) {
}
