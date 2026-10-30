-- 냉장고를 부탁해 · 알림 도메인 테이블 (1개)
-- 팀 "도메인 생성 규칙" 적용: 복수형 snake_case 테이블, PK id BIGINT AUTO_INCREMENT,
-- created_at/updated_at DATETIME(6), 한글 COMMENT
-- member_id 는 회원 도메인 소유라 물리 외래키를 걸지 않음
-- 상태: 초안. DB 제품·버전(Q-07) 확정 전에는 실행하지 않음. 컬럼 설명은 data-dictionary.md

-- 1. 알림 (회원 한 명에게 보이는 알림 하나)
CREATE TABLE notifications (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '알림 ID(클수록 나중에 저장)',
    member_id         BIGINT       NOT NULL COMMENT '받는 회원 ID(회원 도메인 소유)',
    notification_type ENUM('EXPIRATION','FLASH_DROP','COMMERCE_STOCKED') NOT NULL COMMENT '유통기한 임박·번개 드롭·입고',
    content           VARCHAR(200) NOT NULL COMMENT '알림 문구',
    link_url          VARCHAR(500) NOT NULL COMMENT '누르면 이동할 화면 경로',
    dedupe_key        VARCHAR(150) NOT NULL COMMENT '중복 방지 키(같은 회원에게 하나)',
    is_read           BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '읽음 여부',
    deleted_at        DATETIME(6)  NULL COMMENT '회원이 지운 시각(행은 남겨 같은 알림 요청이 다시 와도 되살아나지 않게 함)',
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시',
    updated_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_notifications_member_id_dedupe_key UNIQUE (member_id, dedupe_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '알림';
