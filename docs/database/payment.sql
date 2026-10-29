-- 냉장고를 부탁해 · 결제 도메인 테이블 (3개)
-- 팀 "도메인 생성 규칙" 적용: 복수형 snake_case 테이블, PK id BIGINT AUTO_INCREMENT,
-- FK [단수형 참조테이블명]_id + 물리 외래키, created_at/updated_at DATETIME(6), 한글 COMMENT
-- 엔티티(Payment, PaymentHistory, WebhookEvent) 정의를 옮긴 것. 컬럼 설명은 data-dictionary.md
-- payment_histories.refund_id는 결제가 환불에 의존하지 않도록 물리 외래키를 걸지 않음

-- 1. 결제 (결제하기를 누를 때마다 한 건. 한 주문에 여러 건 가능)
CREATE TABLE payments (
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '결제 ID',
    order_id           BIGINT       NOT NULL COMMENT '주문 ID',
    portone_payment_id VARCHAR(100) NOT NULL COMMENT 'PortOne 결제 ID(서버가 만든 값, payment- + UUID 32자)',
    amount             BIGINT       NOT NULL COMMENT '결제 금액(주문 금액)',
    status             ENUM('READY','PAID','FAILED') NOT NULL COMMENT '결제 대기·결제 완료·실패',
    paid_at            DATETIME(6)  NULL COMMENT '결제 완료 시각(서버 승인 시각)',
    expires_at         DATETIME(6)  NOT NULL COMMENT '결제 대기 만료 시각(생성 10분 뒤)',
    fail_reason        ENUM('PG_FAILED','USER_CANCELLED','AMOUNT_MISMATCH','TIMEOUT','ORDER_NOT_PAYABLE','SUPERSEDED') NULL COMMENT '실패 사유',
    created_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시',
    updated_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_payments_portone_payment_id UNIQUE (portone_payment_id),
    INDEX idx_payments_status_expires_at (status, expires_at),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '결제';

-- 2. 결제 이력 (결제 한 건에 일어난 사건을 추가만 함)
CREATE TABLE payment_histories (
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '결제 이력 ID',
    payment_id         BIGINT       NOT NULL COMMENT '결제 ID',
    event_type         ENUM('READY','PAID','FAILED','PARTIAL_CANCELLED','CANCELLED') NOT NULL COMMENT '사건 종류',
    amount             BIGINT       NULL COMMENT '이번 사건 금액. 실패는 NULL',
    cancelled_amount   BIGINT       NOT NULL COMMENT '사건 직후 누적 취소 금액',
    refund_id          BIGINT       NULL COMMENT '환불로 생긴 취소면 환불 ID(환불 도메인 소유)',
    pg_cancellation_id VARCHAR(200) NULL COMMENT 'PG 취소 건 ID',
    changed_by         ENUM('USER','WEBHOOK','SCHEDULER') NOT NULL COMMENT '사건을 일으킨 주체',
    reason             VARCHAR(255) NULL COMMENT '사유(255자에서 자름)',
    created_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시(사건 시각)',
    updated_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    INDEX idx_payment_histories_payment_id (payment_id, created_at),
    INDEX idx_payment_histories_refund_id (refund_id),
    CONSTRAINT fk_payment_histories_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '결제 이력';

-- 3. PortOne 웹훅 수신 기록 (서명 검증을 통과한 웹훅만)
CREATE TABLE webhook_events (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '웹훅 이벤트 ID',
    payment_id     BIGINT       NULL COMMENT '매칭된 결제 ID. 매칭 실패·처리 대상 아님은 NULL',
    webhook_id     VARCHAR(200) NOT NULL COMMENT 'PortOne webhook-id 헤더(중복 수신 차단)',
    event_type     VARCHAR(50)  NOT NULL COMMENT '웹훅 type 원문(예: Transaction.Paid)',
    process_status ENUM('RECEIVED','PROCESSED','FAILED','IGNORED') NOT NULL COMMENT '처리 상태',
    raw_payload    TEXT         NOT NULL COMMENT '웹훅 본문 원문',
    fail_reason    VARCHAR(500) NULL COMMENT '처리 실패 사유(500자에서 자름)',
    processed_at   DATETIME(6)  NULL COMMENT '처리 완료 시각(PROCESSED·IGNORED)',
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시(수신 시각)',
    updated_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_webhook_events_webhook_id UNIQUE (webhook_id),
    CONSTRAINT fk_webhook_events_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'PortOne 웹훅 수신 기록';
