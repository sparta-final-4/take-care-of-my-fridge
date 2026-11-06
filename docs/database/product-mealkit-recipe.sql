-- 냉장고를 부탁해 · 밀키트 레시피 연결 칸(mealkit_products.recipe_id)
-- 상담 밀키트 추천이 밀키트를 연결된 레시피(분류·맛·이름·냉장고 재료)로 고른다. 연결이 없는 밀키트는 맨 뒤로 간다.
-- 레시피는 다른 도메인이라 ingredient_products.ingredient_id처럼 물리 FK 없이 ID만 둔다.
-- 상품 테이블 DDL 문서가 아직 없어 이 파일에 칸 변경과 dev 채우기를 함께 둔다.
-- 실행: 각 DB에 1번 → 2번 순서로 한 번. dev는 ddl-auto=validate라 앱을 새 코드로 띄우기 전에 1번을 먼저 실행한다.

-- 1. 칸·인덱스 추가
ALTER TABLE mealkit_products
    ADD COLUMN recipe_id BIGINT NULL COMMENT '이 밀키트로 만드는 레시피 ID(레시피 도메인, FK 없음). 없으면 NULL';

CREATE INDEX idx_mealkit_products_recipe_id ON mealkit_products (recipe_id);

-- 2. dev 채우기: 상품 이름이 레시피 이름과 정확히 같은 밀키트만 연결한다
--    된장국 544, 황태해장국 543, 사과 새우 북엇국 542, 부추 콩가루 찜 539, 닭가슴살 브로콜리 만두 555
--    이미 채워진 행은 건드리지 않아 다시 실행해도 결과가 같다.

-- 2-1. 연결될 행 확인 (5행이 나와야 한다)
SELECT mp.id AS mealkit_product_id, p.id AS product_id, p.name AS product_name, r.id AS recipe_id, mp.recipe_id AS current_recipe_id
FROM mealkit_products mp
JOIN products p ON p.id = mp.product_id
JOIN recipes r ON r.name = p.name
WHERE r.id IN (544, 543, 542, 539, 555);

-- 2-2. 채우기
UPDATE mealkit_products mp
JOIN products p ON p.id = mp.product_id
JOIN recipes r ON r.name = p.name
SET mp.recipe_id = r.id
WHERE r.id IN (544, 543, 542, 539, 555)
  AND mp.recipe_id IS NULL;

-- 2-3. 결과 확인 (연결 안 된 밀키트는 recipe_id가 NULL)
SELECT mp.id, p.name, mp.recipe_id
FROM mealkit_products mp
JOIN products p ON p.id = mp.product_id
ORDER BY mp.id;
