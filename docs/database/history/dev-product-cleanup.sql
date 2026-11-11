-- 냉장고를 부탁해 · dev 상품 데이터 정리
-- 상담 장볼 재료 상품(재료 → 상품)을 붙이면서 확인한 dev 데이터 문제를 고친다.
-- 실행: dev DB에 한 번. 각 UPDATE는 다시 실행해도 결과가 같다.

-- 1. "감자" 상품(12)이 재료 콩비지(1)에 연결되어 있다 → 재료 감자로 바꾼다
-- 1-1. 확인 (product 12 / 지금 재료 콩비지 / 바꿀 재료 감자)
SELECT ip.product_id, p.name AS product_name, ip.ingredient_id AS current_ingredient_id, cur.name AS current_ingredient,
       (SELECT id FROM ingredients WHERE name = '감자') AS potato_ingredient_id
FROM ingredient_products ip
JOIN products p ON p.id = ip.product_id
JOIN ingredients cur ON cur.id = ip.ingredient_id
WHERE ip.product_id = 12;

-- 1-2. 바꾸기 (감자 재료가 없으면 바뀌지 않는다)
UPDATE ingredient_products ip
JOIN ingredients potato ON potato.name = '감자'
SET ip.ingredient_id = potato.id
WHERE ip.product_id = 12;

-- 2. 부하 테스트 상품(29·30) 판매 중지
SELECT id, name, status FROM products WHERE id IN (29, 30);

UPDATE products
SET status = 'STOPPED', updated_at = NOW(6)
WHERE id IN (29, 30) AND status <> 'STOPPED';

-- 3. 배추·돼지고기 상품 추가 예시 (실행하지 않음. 값을 정한 뒤 주석을 풀어 쓴다)
--    상품 1개 = products 1행 + ingredient_products 1행. 재료 ID는 이름으로 찾는다.
--    - unit_amount·unit: 상품 용량 (예: 1 포기, 500 g)
--    - base_quantity: 상품 1개가 재료 기본 단위(ingredients.base_unit)로 몇인지. 구매 확정 때 냉장고에 이만큼 들어간다
--      (예: 배추 기본 단위가 "개"면 1, 돼지고기 기본 단위가 "g"이면 500)
--    - SQL로 넣은 상품은 상품 검색(ES) 색인에 바로 들어가지 않을 수 있다. 관리자 화면 등록이 가장 안전하다.
--
-- INSERT INTO products (product_type, name, description, image_url, price, stock, storage, shelf_days, status, created_at, updated_at)
-- VALUES ('INGREDIENT', '배추 1포기', '김치·국거리용 배추', NULL, 4500, 100, 'REFRIGERATED', 7, 'ON_SALE', NOW(6), NOW(6));
-- INSERT INTO ingredient_products (product_id, ingredient_id, unit_amount, unit, base_quantity)
-- SELECT LAST_INSERT_ID(), id, 1, '포기', 1 FROM ingredients WHERE name = '배추';
--
-- INSERT INTO products (product_type, name, description, image_url, price, stock, storage, shelf_days, status, created_at, updated_at)
-- VALUES ('INGREDIENT', '돼지고기 앞다리살 500g', '찌개·볶음용 돼지고기', NULL, 9900, 100, 'REFRIGERATED', 4, 'ON_SALE', NOW(6), NOW(6));
-- INSERT INTO ingredient_products (product_id, ingredient_id, unit_amount, unit, base_quantity)
-- SELECT LAST_INSERT_ID(), id, 500, 'g', 500 FROM ingredients WHERE name = '돼지고기';
--
-- 넣은 뒤 확인: 재료 이름 · 기본 단위 · 상품
-- SELECT i.name, i.base_unit, p.id, p.name, ip.unit_amount, ip.unit, ip.base_quantity
-- FROM ingredient_products ip JOIN ingredients i ON i.id = ip.ingredient_id JOIN products p ON p.id = ip.product_id
-- WHERE i.name IN ('배추', '돼지고기');
