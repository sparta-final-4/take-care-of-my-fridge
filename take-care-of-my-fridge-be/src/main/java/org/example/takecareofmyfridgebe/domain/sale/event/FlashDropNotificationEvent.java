package org.example.takecareofmyfridgebe.domain.sale.event;

import java.util.List;

// 번개 드롭이 등록되거나 시작됐다는 사실만 싣는다. 받는 사람과 문구는 커밋 뒤에 정한다
public record FlashDropNotificationEvent(
        Moment moment,
        List<Long> saleEventIds
) {

    public static FlashDropNotificationEvent created(List<Long> saleEventIds) {
        return new FlashDropNotificationEvent(Moment.CREATED, saleEventIds);
    }

    public static FlashDropNotificationEvent started(List<Long> saleEventIds) {
        return new FlashDropNotificationEvent(Moment.STARTED, saleEventIds);
    }

    public enum Moment {
        CREATED,
        STARTED
    }
}
