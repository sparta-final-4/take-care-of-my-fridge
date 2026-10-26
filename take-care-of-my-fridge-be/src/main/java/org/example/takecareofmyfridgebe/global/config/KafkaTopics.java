package org.example.takecareofmyfridgebe.global.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Kafka 토픽 상수 및 이벤트 규칙
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KafkaTopics {

    /**
     * 구매 확정 이벤트 토픽 (결제 완료가 아니라 구매 확정 시점에 발행, 주문 -> 냉장고 자동 입고)
     */
    public static final String PURCHASE_CONFIRMED = "purchase-confirmed";

    /**
     * 알림 요청 이벤트 토픽 (여러 도메인 -> 알림 발송)
     */
    public static final String NOTIFICATION_REQUESTED = "notification-requested";
}
