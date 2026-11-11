-- 냉장고를 부탁해 · 환불 도메인 테이블 (2개)
-- 팀 "도메인 생성 규칙" 적용: 복수형 snake_case 테이블, PK id BIGINT AUTO_INCREMENT,
-- FK [단수형 참조테이블명]_id + 물리 외래키, created_at/updated_at DATETIME(6), 한글 COMMENT
-- 엔티티(Refund, RefundItem) 정의를 옮긴 것. 컬럼 설명은 data-dictionary.md

-- 1. 환불 (신청 한 번에 한 건. 한 결제에 부분 환불 여러 건 가능)
CREATE TABLE refunds (
    id                      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '환불 ID',
    payment_id              BIGINT       NOT NULL COMMENT '환불할 결제 ID(결제 완료 결제)',
    refund_amount           BIGINT       NOT NULL COMMENT '환불 금액(환불 상품 금액의 합)',
    reason_type             ENUM('CHANGE_OF_MIND','ORDER_MISTAKE','NO_LONGER_NEEDED','ETC') NOT NULL COMMENT '환불 사유 구분',
    reason_detail           VARCHAR(500) NULL COMMENT '상세 사유',
    status                  ENUM('REQUESTED','COMPLETED','FAILED') NOT NULL COMMENT '요청·완료·실패',
    portone_cancellation_id VARCHAR(200) NULL COMMENT 'PG 취소 건 ID',
    fail_reason             VARCHAR(255) NULL COMMENT '실패 사유(PG 오류 원문, 255자에서 자름)',
    refunded_at             DATETIME(6)  NULL COMMENT '환불 완료 시각(정산 기준 시각)',
    created_at              DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시(신청 시각)',
    updated_at              DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    INDEX idx_refunds_refunded_at (refunded_at),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '환불';

-- 2. 환불 상품 (환불 한 건에 포함된 주문 상품과 수량)
CREATE TABLE refund_items (
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '환불 상품 ID',
    refund_id     BIGINT      NOT NULL COMMENT '환불 ID',
    order_item_id BIGINT      NOT NULL COMMENT '환불할 주문 상품 ID',
    quantity      INT         NOT NULL COMMENT '환불 수량(1 이상)',
    unit_price    INT         NOT NULL COMMENT '주문 당시 단가(주문 상품에서 복사)',
    amount        BIGINT      NOT NULL COMMENT '환불 금액(단가 × 수량)',
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시',
    updated_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_refund_items_refund_id_order_item_id UNIQUE (refund_id, order_item_id),
    INDEX idx_refund_items_order_item_id (order_item_id),
    CONSTRAINT fk_refund_items_refund FOREIGN KEY (refund_id) REFERENCES refunds (id),
    CONSTRAINT fk_refund_items_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '환불 상품';
