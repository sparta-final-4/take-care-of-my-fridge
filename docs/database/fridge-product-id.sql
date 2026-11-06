-- 냉장고를 부탁해 · 냉장고 줄 상품 ID(fridge.product_id)
-- 구매 확정으로 들어온 줄에 상품 ID를 남긴다. 이름(raw_name)이 아니라 ID로 같은 상품(밀키트 등)을 가릴 수 있게 한다.
-- 상품은 다른 도메인이라 order_item_id와 같이 물리 FK 없이 ID만 둔다. 직접 등록한 줄은 NULL이다.
-- 실행: 각 DB에 1번 → 2번 순서로 한 번. dev는 ddl-auto=validate라 앱을 새 코드로 띄우기 전에 1번을 먼저 실행한다.

-- 1. 칸 추가
ALTER TABLE fridge
    ADD COLUMN product_id BIGINT NULL COMMENT '구매 확정으로 들어온 줄의 상품 ID(상품 도메인, FK 없음). 직접 등록한 줄은 NULL'
    AFTER order_item_id;

-- 2. 기존 커머스 입고 줄 채우기: 주문 상품(order_items)의 product_id로 채운다
--    product_id가 비어 있는 줄만 채워 다시 실행해도 결과가 같다.

-- 2-1. 채울 줄 확인 (채울 줄 수와, 주문 상품을 찾지 못한 줄 수)
SELECT COUNT(*) AS to_fill
FROM fridge f
JOIN order_items oi ON oi.id = f.order_item_id
WHERE f.order_item_id IS NOT NULL
  AND f.product_id IS NULL;

SELECT COUNT(*) AS commerce_rows_without_order_item
FROM fridge f
LEFT JOIN order_items oi ON oi.id = f.order_item_id
WHERE f.order_item_id IS NOT NULL
  AND oi.id IS NULL;

-- 2-2. 채우기
UPDATE fridge f
JOIN order_items oi ON oi.id = f.order_item_id
SET f.product_id = oi.product_id
WHERE f.order_item_id IS NOT NULL
  AND f.product_id IS NULL;

-- 2-3. 결과 확인 (커머스 입고 줄 중 product_id가 비어 있는 줄은 0이어야 한다)
SELECT f.source, COUNT(*) AS total_rows, SUM(f.product_id IS NULL) AS null_product_id
FROM fridge f
GROUP BY f.source;
