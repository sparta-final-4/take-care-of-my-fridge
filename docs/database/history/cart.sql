-- 냉장고를 부탁해 · 장바구니 도메인 테이블 (2개)
-- 팀 "도메인 생성 규칙" 적용: 복수형 snake_case 테이블, PK id BIGINT AUTO_INCREMENT,
-- FK [단수형 참조테이블명]_id + 물리 외래키, created_at/updated_at DATETIME(6), 한글 COMMENT
-- member_id, product_id 는 다른 도메인(회원·상품) 소유라 물리 외래키를 걸지 않음
-- 상태: 초안. DB 제품·버전(Q-07) 확정 전에는 실행하지 않음. 컬럼 설명은 data-dictionary.md

-- 1. 장바구니 (회원당 하나, 처음 상품을 담을 때 생성)
CREATE TABLE carts (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '장바구니 ID',
    member_id  BIGINT      NOT NULL COMMENT '회원 ID(회원 한 명에 장바구니 하나)',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_carts_member_id UNIQUE (member_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '장바구니';

-- 2. 장바구니 상품 (가격·할인은 저장하지 않고 조회할 때 읽음)
CREATE TABLE cart_items (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '장바구니 상품 ID',
    cart_id    BIGINT      NOT NULL COMMENT '장바구니 ID',
    product_id BIGINT      NOT NULL COMMENT '상품 ID(상품 도메인 소유)',
    quantity   INT         NOT NULL COMMENT '담은 수량(1 이상)',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시(담은 순서)',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_cart_items_cart_id_product_id UNIQUE (cart_id, product_id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '장바구니 상품';
