-- 냉장고를 부탁해 · 할인 행사 도메인 테이블 (2개)
-- 팀 "도메인 생성 규칙" 적용: 복수형 snake_case 테이블, PK id BIGINT AUTO_INCREMENT,
-- FK [단수형 참조테이블명]_id + 물리 외래키, created_at/updated_at DATETIME(6), 한글 COMMENT
-- product_id 는 상품 도메인 소유라 물리 외래키를 걸지 않음
-- 상태: 초안. DB 제품·버전(Q-07) 확정 전에는 실행하지 않음. 컬럼 설명은 data-dictionary.md

-- 1. 할인 행사 (상품 하나에 대한 한 번의 할인 기간)
CREATE TABLE sale_events (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '할인 행사 ID',
    type       ENUM('SPECIAL','FLASH_DROP') NOT NULL COMMENT '특가·번개 드롭',
    starts_at  DATETIME(6) NOT NULL COMMENT '시작 시각',
    ends_at    DATETIME(6) NOT NULL COMMENT '종료 시각(중간에 종료해도 바뀌지 않음)',
    status     ENUM('SCHEDULED','ACTIVE','ENDED') NOT NULL COMMENT '예정·진행 중·종료',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    INDEX idx_sale_events_status_starts_at (status, starts_at),
    INDEX idx_sale_events_status_ends_at (status, ends_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '할인 행사';

-- 2. 할인 행사 항목 (할인 상품과 가격, 번개 드롭 수량)
CREATE TABLE sale_event_items (
    id                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT '행사 항목 ID',
    sale_event_id      BIGINT      NOT NULL COMMENT '할인 행사 ID',
    product_id         BIGINT      NOT NULL COMMENT '상품 ID(상품 도메인 소유)',
    list_price         INT         NOT NULL COMMENT '등록(수정) 때 읽은 정가(원)',
    sale_price         INT         NOT NULL COMMENT '할인가(원, 원 아래 버림)',
    discount_rate      INT         NOT NULL COMMENT '할인율(1~99)',
    limited_quantity   INT         NULL COMMENT '번개 드롭 한정 수량(특가는 NULL)',
    remaining_quantity INT         NULL COMMENT '번개 드롭 남은 수량(특가는 NULL)',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    INDEX idx_sale_event_items_product_id (product_id),
    FOREIGN KEY (sale_event_id) REFERENCES sale_events (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '할인 행사 항목';
