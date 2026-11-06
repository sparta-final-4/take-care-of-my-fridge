-- 냉장고를 부탁해 · 밀키트 레시피 연결 칸(mealkit_products.recipe_id)
-- 상담 밀키트 추천이 밀키트를 연결된 레시피(분류·맛·이름·냉장고 재료)로 고른다. 연결이 없는 밀키트는 맨 뒤로 간다.
-- 레시피는 다른 도메인이라 ingredient_products.ingredient_id처럼 물리 FK 없이 ID만 둔다.
-- 상품 테이블 DDL 문서가 아직 없어 이 파일에 칸 변경과 dev 채우기를 함께 둔다.
-- 실행: 각 DB에 1번 → 2번 순서로 한 번. dev는 ddl-auto=validate라 앱을 새 코드로 띄우기 전에 1번을 먼저 실행한다.

-- 1. 칸·인덱스 추가
ALTER TABLE mealkit_products
    ADD COLUMN recipe_id BIGINT NULL COMMENT '이 밀키트로 만드는 레시피 ID(레시피 도메인, FK 없음). 없으면 NULL';

CREATE INDEX idx_mealkit_products_recipe_id ON mealkit_products (recipe_id);

-- 2. dev 채우기: 상품 ID → 레시피 ID를 직접 정해 연결한다
--    dev 상품 이름은 끝에 " 밀키트"가 붙어 있어("된장국 밀키트") 레시피 이름("된장국")과 이름으로 맞출 수 없다.
--    24 된장국 → 544, 25 황태해장국 → 543, 26 사과 새우 북엇국 → 542, 27 부추 콩가루 찜 → 539, 28 닭가슴살 브로콜리 만두 → 555
--    recipe_id가 이미 채워진 행은 건드리지 않아 다시 실행해도 결과가 같다.

-- 2-1. 짝 확인 (5행. 상품 이름과 레시피 이름이 같은 요리인지 눈으로 본다. recipe_name이 NULL이면 그 레시피가 없다)
SELECT m.product_id, p.name AS product_name, m.recipe_id, r.name AS recipe_name, mp.recipe_id AS current_recipe_id
FROM (
    SELECT 24 AS product_id, 544 AS recipe_id
    UNION ALL SELECT 25, 543
    UNION ALL SELECT 26, 542
    UNION ALL SELECT 27, 539
    UNION ALL SELECT 28, 555
) m
LEFT JOIN mealkit_products mp ON mp.product_id = m.product_id
LEFT JOIN products p ON p.id = m.product_id
LEFT JOIN recipes r ON r.id = m.recipe_id
ORDER BY m.product_id;

-- 2-2. 채우기 (밀키트 행과 레시피가 모두 있을 때만)
UPDATE mealkit_products mp
JOIN (
    SELECT 24 AS product_id, 544 AS recipe_id
    UNION ALL SELECT 25, 543
    UNION ALL SELECT 26, 542
    UNION ALL SELECT 27, 539
    UNION ALL SELECT 28, 555
) m ON m.product_id = mp.product_id
JOIN recipes r ON r.id = m.recipe_id
SET mp.recipe_id = m.recipe_id
WHERE mp.recipe_id IS NULL;

-- 2-3. 결과 확인 (연결 안 된 밀키트는 recipe_id가 NULL)
SELECT mp.id, p.name, mp.recipe_id
FROM mealkit_products mp
JOIN products p ON p.id = mp.product_id
ORDER BY mp.id;
