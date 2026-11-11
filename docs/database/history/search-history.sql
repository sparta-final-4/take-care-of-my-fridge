-- 냉장고를 부탁해 · 통합검색 기록 테이블 (3개)
-- 검색 결과 상품 클릭, 레시피 클릭, 결과가 0건이었던 검색을 남긴다.
-- 상품 클릭은 할인 분석이 읽으므로 레시피 클릭과 섞지 않고 따로 둔다.
-- 로그 테이블이라 product_id·recipe_id·member_id에 물리 FK를 걸지 않고 ID만 둔다. 비로그인 클릭은 member_id가 NULL이다.
-- 실행: 각 DB에 한 번. dev는 ddl-auto=validate라 앱을 새 코드로 띄우기 전에 먼저 실행한다.
--       IF NOT EXISTS라 이미 테이블이 있는 DB(로컬 ddl-auto=update)에서 다시 실행해도 결과가 같다.

-- 1. 상품 클릭 기록
CREATE TABLE IF NOT EXISTS search_click_histories (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '상품 클릭 기록 ID',
    product_id  BIGINT        NOT NULL COMMENT '클릭한 상품 ID(상품 도메인, FK 없음)',
    member_id   BIGINT        NULL COMMENT '클릭한 회원 ID(비로그인 클릭은 NULL, FK 없음)',
    keyword     VARCHAR(100)  NOT NULL COMMENT '검색어',
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시(클릭 시각)',
    updated_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    KEY idx_search_click_histories_created_at_product_id (created_at, product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '통합검색 결과 상품 클릭 기록(할인 분석이 읽음)';

-- 2. 레시피 클릭 기록
CREATE TABLE IF NOT EXISTS search_recipe_click_histories (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '레시피 클릭 기록 ID',
    recipe_id   BIGINT        NOT NULL COMMENT '클릭한 레시피 ID(레시피 도메인, FK 없음)',
    member_id   BIGINT        NULL COMMENT '클릭한 회원 ID(비로그인 클릭은 NULL, FK 없음)',
    keyword     VARCHAR(100)  NOT NULL COMMENT '검색어',
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시(클릭 시각)',
    updated_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    KEY idx_search_recipe_click_histories_created_at_recipe_id (created_at, recipe_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '통합검색 결과 레시피 클릭 기록(상품 클릭과 분리)';

-- 3. 결과 0건 검색 기록 (동의어·사전에 넣을 검색어를 찾는 데 쓴다)
CREATE TABLE IF NOT EXISTS search_zero_result_histories (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '0건 검색 기록 ID',
    target      ENUM('RECIPE','PRODUCT') NOT NULL COMMENT '검색 대상(레시피·상품)',
    keyword     VARCHAR(50)   NOT NULL COMMENT '검색어',
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시(검색 시각)',
    updated_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    KEY idx_search_zero_result_histories_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '결과가 0건이었던 통합검색 기록(동의어·사전 보강용)';
