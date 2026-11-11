-- =========================================================
-- 냉장고를 부탁해 전체 스키마
-- 1) DROP: 참조하는 쪽부터 역순으로 삭제
-- 2) CREATE: 참조되는 쪽부터 순서대로 생성
--
-- 변경 이력
--   2026-10-19 orders.confirmed_at 추가 (구매 확정 일시)
--   2026-10-21 결제-주문의 연관관계(1:1 -> 1:N) 변경
--   2026-10-21 orders.idempotency_key 추가, UNIQUE (member_id, idempotency_key) 추가 (중복 주문 방지)
--   2026-10-21 order_items.product_image_url 추가 (주문 당시 상품 이미지)
--   2026-10-23 ingredients.unit_weight_g 추가 (기본 단위 1개당 무게, 레시피·상품 g 수량 변환용)
--   2026-10-25 sale_event_items.list_price 추가 (등록 당시 정가)
--   2026-10-29 chat_sessions.active_member_id 계산 컬럼 + UNIQUE 추가 (회원당 진행 중 상담 1개)
--   2026-11-03 recipe_tastes 추가 (레시피 맛, AI 상담 추천에서 회원 맛 선호와 비교)
--   2026-11-04 chat_recommendations.description 추가 (추천 카드 아래 AI 설명)
--   2026-11-06 mealkit_products.recipe_id 추가 (밀키트와 같은 레시피, AI 상담 밀키트 추천에서 분류·맛·재료 비교)
--   2026-11-06 fridge.product_id 추가 (구매 확정으로 들어온 줄의 상품 ID, 같은 밀키트 판단·다시 사기용)
--   2026-11-07 chat_messages.filtered 추가 (안전 장치로 걸러진 메시지, AI 문맥·상담 내역 미리보기에서 제외)
--   2026-11-11 search_click_histories, search_recipe_click_histories, search_zero_result_histories 추가 (통합검색 클릭·0건 기록)
--   2026-11-11 실제 DB(dev)에 맞춤: forecast_batch_runs 추가, members.phone·admin_audit_logs.actor_role·notifications.deleted_at·order_items.confirmed_quantity·sale_events.ended_at·prediction_runs.synthetic_data·deal_analyses.synthetic_data·billing_keys.billing_key(암호화 전 평문, 지금은 NULL) 추가
-- =========================================================

SET NAMES utf8mb4;

-- =========================================================
-- 1. DROP (역순)
-- =========================================================
DROP TABLE IF EXISTS search_zero_result_histories;
DROP TABLE IF EXISTS search_recipe_click_histories;
DROP TABLE IF EXISTS search_click_histories;

DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS recipe_reviews;
DROP TABLE IF EXISTS fridge_item_logs;
DROP TABLE IF EXISTS cooking_logs;
DROP TABLE IF EXISTS chat_recommendations;
DROP TABLE IF EXISTS chat_messages;
DROP TABLE IF EXISTS chat_sessions;
DROP TABLE IF EXISTS fridge_commerce_histories;
DROP TABLE IF EXISTS fridge;

DROP TABLE IF EXISTS daily_sales;
DROP TABLE IF EXISTS webhook_events;
DROP TABLE IF EXISTS refund_items;
DROP TABLE IF EXISTS refunds;
DROP TABLE IF EXISTS payment_histories;
DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS payment_pins;
DROP TABLE IF EXISTS billing_keys;

DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS cart_items;
DROP TABLE IF EXISTS carts;
DROP TABLE IF EXISTS sale_event_items;
DROP TABLE IF EXISTS sale_events;

DROP TABLE IF EXISTS recipe_tastes;
DROP TABLE IF EXISTS recipe_steps;
DROP TABLE IF EXISTS recipe_ingredients;
DROP TABLE IF EXISTS recipes;
DROP TABLE IF EXISTS mealkit_products;
DROP TABLE IF EXISTS ingredient_products;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS ingredients;

DROP TABLE IF EXISTS admin_audit_logs;
DROP TABLE IF EXISTS deal_candidates;
DROP TABLE IF EXISTS deal_analyses;
DROP TABLE IF EXISTS sales_forecasts;
DROP TABLE IF EXISTS forecast_batch_runs;
DROP TABLE IF EXISTS prediction_runs;
DROP TABLE IF EXISTS refresh_sessions;
DROP TABLE IF EXISTS member_tastes;
DROP TABLE IF EXISTS members;

-- =========================================================
-- 2. CREATE (참조 순서)
-- =========================================================

-- ---------------------------------------------------------
-- [상윤] 회원
-- ---------------------------------------------------------
CREATE TABLE members (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '회원 ID',
    email         VARCHAR(255) NOT NULL COMMENT '로그인 이메일',
    password_hash VARCHAR(255) NOT NULL COMMENT '비밀번호 해시(복원 불가)',
    nickname      VARCHAR(30)  NOT NULL COMMENT '닉네임(후기 표시 이름)',
    phone         VARCHAR(11)  NULL COMMENT '전화번호 숫자 11자리(가입 필수. 필수 이전 회원·첫 관리자는 NULL)',
    role          ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER' COMMENT '역할(가입 시 USER, 관리자 승격 시 ADMIN)',
    status        ENUM('ACTIVE', 'SUSPENDED') NOT NULL DEFAULT 'ACTIVE' COMMENT '계정 상태(정상/정지)',
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '가입일시',
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_members_email UNIQUE (email),
    CONSTRAINT uk_members_nickname UNIQUE (nickname),
    CONSTRAINT uk_members_phone UNIQUE (phone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '회원';

CREATE TABLE member_tastes (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '맛 선호 ID',
    member_id  BIGINT      NOT NULL COMMENT '회원 ID',
    taste      ENUM('SALTY', 'SWEET', 'SPICY', 'UMAMI', 'MILD') NOT NULL COMMENT '선호 맛(짠맛/단맛/매운맛/감칠맛/슴슴한맛)',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_member_tastes_member_taste UNIQUE (member_id, taste),
    CONSTRAINT fk_member_tastes_member FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '회원 맛 선호';

CREATE TABLE refresh_sessions (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '세션 ID',
    member_id      BIGINT       NOT NULL COMMENT '회원 ID',
    token_hash     VARCHAR(255) NOT NULL COMMENT '갱신 토큰 해시',
    keep_logged_in BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '로그인 상태 유지 체크 여부',
    device_info    VARCHAR(255) NULL COMMENT '접속 기기 정보(기기별 로그아웃용)',
    last_used_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '마지막 사용일시(30분 미사용 시 만료)',
    expires_at     DATETIME(6)  NOT NULL COMMENT '만료일시',
    revoked_at     DATETIME(6)  NULL COMMENT '폐기일시(로그아웃·비밀번호 변경 시)',
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '로그인일시',
    updated_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_sessions_member FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '로그인 세션';

-- ---------------------------------------------------------
-- [상윤] 판매 예측 · 할인 분석 · 운영 기록
-- ---------------------------------------------------------
CREATE TABLE prediction_runs (
    id                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '예측 실행 ID',
    target_week_start DATE        NOT NULL COMMENT '예측 대상 주 시작일(월요일)',
    model_version     VARCHAR(50) NOT NULL COMMENT '예측 모델 버전(예: fc-v0.3)',
    data_cutoff_at    DATETIME(6) NOT NULL COMMENT '입력 데이터 기준 시각',
    confirmed_at      DATETIME(6) NULL COMMENT '실제 판매량 확정일시(다음 주 월요일 06:00)',
    synthetic_data    BIT(1)      NOT NULL COMMENT '가상(시연) 데이터 여부',
    created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '예측 생성일시(일요일 22:00)',
    updated_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_prediction_runs_target_week_start UNIQUE (target_week_start)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '주간 판매 예측 실행';

CREATE TABLE sales_forecasts (
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '상품별 예측 ID',
    prediction_run_id BIGINT       NOT NULL COMMENT '예측 실행 ID',
    product_id        BIGINT       NOT NULL COMMENT '상품 ID(상품 도메인, 외래키 없음)',
    product_name      VARCHAR(100) NOT NULL COMMENT '예측 시점 상품명',
    predicted_rank    INT          NULL COMMENT '예상 순위(예상 TOP 10 밖이었다가 실제 진입한 상품은 NULL)',
    predicted_qty     INT          NULL COMMENT '예상 판매 수량',
    baseline_qty      INT          NOT NULL DEFAULT 0 COMMENT '기준선 예측(전주 판매량)',
    actual_rank       INT          NULL COMMENT '실제 순위(확정 후 입력)',
    actual_qty        INT          NULL COMMENT '실제 판매 수량(취소·환불 제외, 확정 후 입력)',
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시',
    updated_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_sales_forecasts_run_product UNIQUE (prediction_run_id, product_id),
    CONSTRAINT fk_sales_forecasts_prediction_run FOREIGN KEY (prediction_run_id) REFERENCES prediction_runs (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '상품별 판매 예측과 실제 판매량';

CREATE TABLE forecast_batch_runs
(
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '배치 시도 기록 ID',
    target_week_start DATE         NOT NULL COMMENT '예측 대상 주 시작일(월요일)',
    status            ENUM('CREATED', 'ALREADY_EXISTS', 'NO_PRODUCTS', 'NO_TRAINING_SAMPLES', 'FAILED') NOT NULL COMMENT '마지막 시도 결과',
    attempted_at      DATETIME(6)  NOT NULL COMMENT '마지막 시도 시각',
    failure_type      VARCHAR(100) NULL COMMENT '실패 종류(예외 이름 등)',
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시',
    updated_at        DATETIME(6)  NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_forecast_batch_runs_target_week_start UNIQUE (target_week_start)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '주간 판매 예측 배치 시도 기록(대상 주마다 한 행, 최신 시도로 덮어씀)';

CREATE TABLE deal_analyses (
    id                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '할인 후보 분석 ID',
    member_id         BIGINT      NOT NULL COMMENT '분석한 관리자 회원 ID',
    target_week_start DATE        NOT NULL COMMENT '대상 주 시작일(월요일)',
    day_of_week       ENUM('MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN') NOT NULL COMMENT '적용 요일',
    discount_rate     INT         NOT NULL DEFAULT 0 COMMENT '가상 할인율(%, 10~40)',
    min_stock         INT         NOT NULL DEFAULT 50 COMMENT '최소 재고 기준(개)',
    model_version     VARCHAR(50) NOT NULL COMMENT '분석 모델 버전',
    data_cutoff_at    DATETIME(6) NOT NULL COMMENT '입력 데이터 기준 시각',
    last_saved_at     DATETIME(6) NULL COMMENT '검토 결과 마지막 저장일시',
    synthetic_data    BIT(1)      NOT NULL COMMENT '가상(시연) 데이터 여부',
    created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '분석일시',
    updated_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT fk_deal_analyses_member FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '요일별 할인 후보 분석';

CREATE TABLE deal_candidates (
    id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '분석 결과 상품 ID',
    deal_analysis_id      BIGINT       NOT NULL COMMENT '할인 후보 분석 ID',
    product_id            BIGINT       NOT NULL COMMENT '상품 ID(상품 도메인, 외래키 없음)',
    product_name          VARCHAR(100) NOT NULL COMMENT '분석 시점 상품명',
    regular_price         INT          NOT NULL DEFAULT 0 COMMENT '분석 당시 정상가(원)',
    discount_price        INT          NOT NULL DEFAULT 0 COMMENT '가상 할인가(원)',
    current_stock         INT          NOT NULL DEFAULT 0 COMMENT '분석 당시 재고(개)',
    avg_sales_on_day      INT          NOT NULL DEFAULT 0 COMMENT '해당 요일 평균 판매량',
    search_clicks         INT          NOT NULL DEFAULT 0 COMMENT '최근 4주 같은 요일 검색 클릭 수',
    purchases             INT          NOT NULL DEFAULT 0 COMMENT '최근 4주 같은 요일 구매 수',
    is_excluded           BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '후보 제외 여부(TRUE면 제외된 상품)',
    exclude_reason        ENUM('TOP_SELLER_ON_DAY', 'LOW_STOCK') NULL COMMENT '제외 사유(평소 그 요일 판매 상위/재고 부족)',
    candidate_reason      VARCHAR(255) NULL COMMENT '후보 사유(예: 검색 클릭 대비 구매 적음)',
    expected_qty_regular  INT          NULL COMMENT '정상가일 때 예상 판매 수량(후보만)',
    expected_qty_discount INT          NULL COMMENT '할인가일 때 예상 판매 수량(후보만)',
    expected_increase     INT          NULL COMMENT '예상 판매 증가량(후보만)',
    review_status         ENUM('UNREVIEWED', 'SELECTED', 'HELD') NULL COMMENT '관리자 검토 상태(미검토/선택/보류, 후보만)',
    event_request_status  ENUM('NONE', 'REQUESTED', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'NONE' COMMENT '할인 행사 등록 요청 상태',
    event_requested_at    DATETIME(6)  NULL COMMENT '할인 행사 등록 요청일시',
    promotion_id          BIGINT       NULL COMMENT '등록된 할인 행사 ID(할인 행사 도메인, 외래키 없음)',
    created_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시',
    updated_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_deal_candidates_analysis_product UNIQUE (deal_analysis_id, product_id),
    CONSTRAINT fk_deal_candidates_deal_analysis FOREIGN KEY (deal_analysis_id) REFERENCES deal_analyses (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '할인 후보 분석 결과 상품(후보·제외)';

CREATE TABLE admin_audit_logs (
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '작업 이력 ID',
    member_id    BIGINT      NOT NULL COMMENT '작업한 회원 ID(권한 없이 시도한 일반 회원 포함)',
    action       ENUM('PRODUCT_CREATE', 'PRODUCT_DELETE', 'PRICE_UPDATE', 'DEAL_ANALYSIS', 'DEAL_REVIEW', 'EVENT_REQUEST', 'ROLE_CHANGE') NOT NULL COMMENT '작업 종류',
    target_type  VARCHAR(50) NOT NULL COMMENT '작업 대상 종류(Product, DealCandidate, Member 등)',
    target_id    BIGINT      NULL COMMENT '작업 대상 ID(등록 실패처럼 대상이 없으면 NULL)',
    result       ENUM('SUCCESS', 'FAILED', 'DENIED') NOT NULL COMMENT '결과(성공/실패/권한 거부)',
    actor_role   ENUM('ADMIN', 'USER') NULL COMMENT '작업 당시 역할(이 칸 도입 전 이력은 NULL, 조회 때 지금 역할로 대신)',
    before_value JSON        NULL COMMENT '변경 전 값',
    after_value  JSON        NULL COMMENT '변경 후 값',
    created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '작업일시',
    updated_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT fk_admin_audit_logs_member FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '관리자 작업 이력';

-- ---------------------------------------------------------
-- [현정] 표준 재료 사전 (준모 상품·레시피가 참조)
-- ---------------------------------------------------------
CREATE TABLE ingredients (
    id                  BIGINT        NOT NULL AUTO_INCREMENT                    COMMENT '표준 재료 ID',
    ingredient_code     VARCHAR(50)   NOT NULL                                   COMMENT '표준 재료 코드 (예: ING_RCP_0003. 특수 재료: ING_CUSTOM 사전에 없는 재료, ING_MEALKIT 밀키트)',
    name                VARCHAR(100)  NOT NULL                                   COMMENT '대표 명칭 (예: 대파. 다른 표현은 동의어로 관리)',
    base_unit           VARCHAR(20)   NOT NULL                                   COMMENT '기본 단위 (예: 개, 대, 모, 장, 마리, g, ml. 냉장고·레시피·상품 수량은 모두 이 단위 기준)',
    unit_weight_g       DECIMAL(8, 2) NULL                                       COMMENT '기본 단위 1개당 무게(g) (예: 양파 1개 180g). 기본 단위가 g·ml이면 NULL. 레시피·상품의 g 수량을 기본 단위로 바꿀 때 사용',
    default_storage     ENUM('REFRIGERATED', 'FROZEN', 'ROOM_TEMP') NOT NULL     COMMENT '권장 보관 방식 (냉장/냉동/실온)',
    default_shelf_days  INT           NOT NULL DEFAULT 0                         COMMENT '권장 보관 일수 (등록일 + 일수 = 소비기한)',
    is_basic_seasoning  BOOLEAN       NOT NULL DEFAULT FALSE                     COMMENT '기본 양념 여부 (요리 완료 차감·임박 재료 추천에서 제외)',
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)      COMMENT '생성일시',
    updated_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ingredients_ingredient_code (ingredient_code),
    UNIQUE KEY uk_ingredients_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='표준 재료 사전 (동의어는 Elasticsearch에서 관리)';

-- ---------------------------------------------------------
-- [준모] 상품
-- ---------------------------------------------------------
CREATE TABLE products (
    id           BIGINT                                   NOT NULL AUTO_INCREMENT COMMENT '상품 ID',
    product_type ENUM ('INGREDIENT', 'MEALKIT')           NOT NULL COMMENT '상품 종류 (INGREDIENT: 재료 / MEALKIT: 밀키트)',
    name         VARCHAR(200)                             NOT NULL COMMENT '상품명',
    description  TEXT                                     NULL     COMMENT '상품 설명',
    image_url    VARCHAR(500)                             NULL     COMMENT '상품 이미지 URL',
    price        INT                                      NOT NULL DEFAULT 0 COMMENT '판매가(원)',
    stock        INT                                      NOT NULL DEFAULT 0 COMMENT '재고 수량',
    storage      ENUM('REFRIGERATED', 'FROZEN', 'ROOM_TEMP') NOT NULL        COMMENT '보관 방식 (냉장/냉동/실온)',
    shelf_days   INT                                      NOT NULL DEFAULT 0 COMMENT '권장 보관 일수 (구매 확정일 + 일수 = 냉장고 소비기한)',
    status       ENUM ('ON_SALE', 'SOLD_OUT', 'STOPPED')  NOT NULL DEFAULT 'ON_SALE' COMMENT '판매 상태 (ON_SALE: 판매 중 / SOLD_OUT: 품절 / STOPPED: 판매 중지)',
    created_at   DATETIME(6)                              NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시',
    updated_at   DATETIME(6)                              NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    KEY idx_products_status (status),
    CONSTRAINT chk_products_price CHECK (price >= 0),
    CONSTRAINT chk_products_stock CHECK (stock >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '판매 상품 공통 정보';

CREATE TABLE ingredient_products (
    id            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '재료 상품 ID',
    product_id    BIGINT         NOT NULL COMMENT '상품 ID',
    ingredient_id BIGINT         NOT NULL COMMENT '재료 ID',
    unit_amount   DECIMAL(8, 2)  NOT NULL COMMENT '용량 수치 (300)',
    unit          VARCHAR(20)    NOT NULL COMMENT '용량 단위 (g, ml, 개, 팩…)',
    base_quantity DECIMAL(10, 2) NOT NULL COMMENT '그 상품이 그 단위로 몇 개인지 (단위당 갯수)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ingredient_products_product_id (product_id),
    KEY idx_ingredient_products_ingredient_id (ingredient_id),
    CONSTRAINT fk_ingredient_products_product    FOREIGN KEY (product_id)    REFERENCES products (id),
    CONSTRAINT fk_ingredient_products_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients (id),
    CONSTRAINT chk_ingredient_products_unit_amount CHECK (unit_amount > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '재료 상품 상세. 재료 마스터 1개에 여러 상품(용량/산지별)이 붙는다';

CREATE TABLE mealkit_products (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '밀키트 상품 ID',
    product_id BIGINT      NOT NULL COMMENT '상품 ID',
    servings   INT         NOT NULL DEFAULT 1 COMMENT '구성 인분',
    recipe_id  BIGINT      NULL     COMMENT '같은 요리의 레시피 ID (레시피 도메인, 외래키 없음. 연결할 레시피가 없으면 NULL)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mealkit_products_product_id (product_id),
    KEY idx_mealkit_products_recipe_id (recipe_id),
    CONSTRAINT fk_mealkit_products_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_mealkit_products_servings CHECK (servings > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '밀키트 상품 상세';

-- ---------------------------------------------------------
-- [준모] 레시피
-- ---------------------------------------------------------
CREATE TABLE recipes (
    id                       BIGINT                 NOT NULL AUTO_INCREMENT COMMENT '레시피 ID',
    source                   VARCHAR(20)            NOT NULL COMMENT '출처 (MAFRA: 농식품 레시피 / MFDS: 식약처 COOKRCP01)',
    source_id                VARCHAR(20)            NOT NULL COMMENT '원본 레시피 코드',
    name                     VARCHAR(200)           NOT NULL COMMENT '레시피 이름',
    summary                  TEXT                   NULL     COMMENT '간략 소개',
    cuisine_type             VARCHAR(20)            NULL     COMMENT '음식 종류 (한식/서양/중국…)',
    category                 VARCHAR(50)            NULL     COMMENT '요리 분류 (밥/국/반찬…)',
    cook_method              VARCHAR(20)            NULL     COMMENT '조리 방법 (끓이기/굽기…)',
    main_ingredient_category VARCHAR(30)            NULL     COMMENT '재료별 분류',
    cook_time_min            INT                    NULL     COMMENT '조리시간(분)',
    servings                 INT                    NULL     COMMENT '분량(인분)',
    difficulty               TINYINT                NOT NULL COMMENT '난이도 1(쉬움)~10(어려움). 단계 수/재료 수/조리시간/기법으로 계산',
    weight_g                 DECIMAL(8, 2)          NULL     COMMENT '1인분 중량(g)',
    calories                 DECIMAL(8, 2)          NULL     COMMENT '열량(kcal)',
    carbs_g                  DECIMAL(8, 2)          NULL     COMMENT '탄수화물(g)',
    protein_g                DECIMAL(8, 2)          NULL     COMMENT '단백질(g)',
    fat_g                    DECIMAL(8, 2)          NULL     COMMENT '지방(g)',
    sodium_mg                DECIMAL(8, 2)          NULL     COMMENT '나트륨(mg)',
    tags                     VARCHAR(255)           NULL     COMMENT '해시태그',
    tip                      TEXT                   NULL     COMMENT '저감 조리법 팁',
    tip_summary              TEXT                   NULL     COMMENT '후기 요약',
    image_url                VARCHAR(500)           NULL     COMMENT '대표 이미지 URL',
    ingredients_raw          TEXT                   NULL     COMMENT '재료 원문 (파싱 전)',
    created_at               DATETIME(6)            NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시',
    updated_at               DATETIME(6)            NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_recipes_source_source_id (source, source_id),
    KEY idx_recipes_name (name),
    KEY idx_recipes_category (category),
    KEY idx_recipes_difficulty (difficulty),
    CONSTRAINT chk_recipes_difficulty CHECK (difficulty BETWEEN 1 AND 10)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '레시피 기본 정보';

CREATE TABLE recipe_ingredients (
    id            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '레시피 재료 ID',
    recipe_id     BIGINT         NOT NULL COMMENT '레시피 ID',
    ingredient_id BIGINT         NOT NULL COMMENT '재료 ID',
    seq           INT            NOT NULL COMMENT '레시피 내 순서 (1부터)',
    display_name  VARCHAR(100)   NULL     COMMENT '레시피에 적힌 재료명 그대로 (다진 마늘)',
    amount        VARCHAR(100)   NULL     COMMENT '원문 용량 (1/2큰술, 약간, 100g(3개)…)',
    quantity      DECIMAL(10, 2) NULL     COMMENT '수량 (기본단위 당)',
    role          VARCHAR(20)    NULL     COMMENT '재료 역할 (주재료/부재료/양념)',
    group_name    VARCHAR(50)    NULL     COMMENT '원문 그룹명 (양념장, 고명…)',
    raw_text      VARCHAR(255)   NULL     COMMENT '파싱 전 항목 원문',
    created_at    DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시',
    updated_at    DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_recipe_ingredients_recipe_id_seq (recipe_id, seq),
    KEY idx_recipe_ingredients_ingredient_id (ingredient_id),
    CONSTRAINT fk_recipe_ingredients_recipe     FOREIGN KEY (recipe_id)     REFERENCES recipes (id) ON DELETE CASCADE,
    CONSTRAINT fk_recipe_ingredients_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredients (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '레시피-재료 연결 (N:M). 레시피별 재료 목록과 용량';

CREATE TABLE recipe_steps (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '조리 단계 ID',
    recipe_id   BIGINT       NOT NULL COMMENT '레시피 ID',
    step_no     INT          NOT NULL COMMENT '조리 순서 (1부터)',
    description TEXT         NOT NULL COMMENT '조리 설명',
    image_url   VARCHAR(500) NULL     COMMENT '단계 이미지 URL',
    tip         TEXT         NULL     COMMENT '단계별 팁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_recipe_steps_recipe_id_step_no (recipe_id, step_no),
    CONSTRAINT fk_recipe_steps_recipe FOREIGN KEY (recipe_id) REFERENCES recipes (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '레시피 조리 단계';

CREATE TABLE recipe_tastes (
    id         BIGINT      NOT NULL AUTO_INCREMENT                    COMMENT '레시피 맛 ID',
    recipe_id  BIGINT      NOT NULL                                   COMMENT '레시피 ID',
    taste      ENUM('SALTY', 'SWEET', 'SPICY', 'UMAMI', 'MILD') NOT NULL COMMENT '레시피 맛(짠맛/단맛/매운맛/감칠맛/슴슴한맛). member_tastes와 같은 값',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)      COMMENT '생성일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_recipe_tastes_recipe_id_taste (recipe_id, taste),
    KEY idx_recipe_tastes_taste_recipe_id (taste, recipe_id),
    CONSTRAINT fk_recipe_tastes_recipe
        FOREIGN KEY (recipe_id) REFERENCES recipes (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '레시피 맛 (N:M). AI 상담 추천에서 회원 맛 선호와 비교';

-- ---------------------------------------------------------
-- [준혁] 할인 행사
-- ---------------------------------------------------------
CREATE TABLE sale_events (
    id          BIGINT       NOT NULL AUTO_INCREMENT                    COMMENT '할인 행사 ID',
    type        ENUM('SPECIAL', 'FLASH_DROP') NOT NULL                  COMMENT '구분 (특가/번개 드롭)',
    starts_at   DATETIME(6)  NOT NULL                                   COMMENT '시작 시각',
    ends_at     DATETIME(6)  NOT NULL                                   COMMENT '종료 시각',
    status      ENUM('SCHEDULED', 'ACTIVE', 'ENDED') NOT NULL DEFAULT 'SCHEDULED' COMMENT '상태 (예정/진행 중/종료)',
    ended_at    DATETIME(6)  NULL                                       COMMENT '진행 중에 중간에 닫은 시각 (등록한 종료 시각까지 연 행사는 NULL)',
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)      COMMENT '생성일시',
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    KEY idx_sale_events_status_starts_at (status, starts_at),
    KEY idx_sale_events_status_ends_at (status, ends_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='할인 행사 (특가·번개 드롭, 기간과 상태는 행사 단위)';

CREATE TABLE sale_event_items (
    id                  BIGINT       NOT NULL AUTO_INCREMENT                    COMMENT '할인 행사 상품 ID',
    sale_event_id       BIGINT       NOT NULL                                   COMMENT '할인 행사 ID',
    product_id          BIGINT       NOT NULL                                   COMMENT '상품 ID',
    list_price          INT          NOT NULL DEFAULT 0                         COMMENT '등록 당시 정가',
    sale_price          INT          NOT NULL DEFAULT 0                         COMMENT '할인가 (번개 드롭은 번개 드롭가)',
    discount_rate       INT          NOT NULL DEFAULT 0                         COMMENT '할인율 (%, 정가 대비. 관리자가 입력하고 서버가 이 값으로 할인가를 계산해 저장)',
    limited_quantity    INT          NULL                                       COMMENT '한정 수량 (번개 드롭만, 특가 NULL)',
    remaining_quantity  INT          NULL                                       COMMENT '남은 수량 (번개 드롭만, 특가 NULL)',
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)      COMMENT '생성일시',
    updated_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    KEY idx_sale_event_items_product_id (product_id),
    CONSTRAINT fk_sale_event_items_sale_event
        FOREIGN KEY (sale_event_id) REFERENCES sale_events (id),
    CONSTRAINT fk_sale_event_items_product
        FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='할인 행사 상품 (등록 당시 정가 포함)';

-- ---------------------------------------------------------
-- [준혁] 장바구니 · 주문
-- ---------------------------------------------------------
CREATE TABLE carts (
    id          BIGINT       NOT NULL AUTO_INCREMENT                    COMMENT '장바구니 ID',
    member_id   BIGINT       NOT NULL                                   COMMENT '회원 ID (회원 한 명에 장바구니 하나)',
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)      COMMENT '생성일시',
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_carts_member_id (member_id),
    CONSTRAINT fk_carts_member
        FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='장바구니 (회원이 처음 상품을 담을 때 생성)';

CREATE TABLE cart_items (
    id          BIGINT       NOT NULL AUTO_INCREMENT                    COMMENT '장바구니 상품 ID',
    cart_id     BIGINT       NOT NULL                                   COMMENT '장바구니 ID',
    product_id  BIGINT       NOT NULL                                   COMMENT '상품 ID',
    quantity    INT          NOT NULL DEFAULT 1                         COMMENT '수량 (1 이상)',
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)      COMMENT '생성일시',
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_items_cart_id_product_id (cart_id, product_id),
    CONSTRAINT fk_cart_items_cart
        FOREIGN KEY (cart_id) REFERENCES carts (id),
    CONSTRAINT fk_cart_items_product
        FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='장바구니 상품 (가격·재고는 저장하지 않고 조회할 때 읽음)';

CREATE TABLE orders (
    id               BIGINT       NOT NULL AUTO_INCREMENT                    COMMENT '주문 ID',
    order_number     VARCHAR(20)  NOT NULL                                   COMMENT '주문번호 (날짜 + 일련번호, 예: 20261013-000123)',
    member_id        BIGINT       NOT NULL                                   COMMENT '회원 ID',
    idempotency_key  VARCHAR(64)  NULL                                       COMMENT '중복 주문 방지 키 (주문하기 클릭마다 프론트가 만든 값, 같은 값이면 새 주문을 만들지 않음, 기존 주문은 NULL)',
    status           ENUM('PENDING', 'PAID', 'CANCELLED', 'CONFIRMED', 'REFUNDED') NOT NULL DEFAULT 'PENDING' COMMENT '주문 상태 (결제 대기/결제 완료/취소/구매 확정/전액 환불)',
    total_amount     BIGINT       NOT NULL DEFAULT 0                         COMMENT '결제할 금액 (주문 생성 때 확정)',
    paid_at          DATETIME(6)  NULL                                       COMMENT '결제 완료 일시 (구매 확정 스케줄러 기준, 결제 전에는 NULL)',
    confirmed_at     DATETIME(6)  NULL                                       COMMENT '구매 확정 일시 (확정 전에는 NULL)',
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)      COMMENT '생성일시 (5분 자동 취소 기준)',
    updated_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_number (order_number),
    UNIQUE KEY uk_orders_member_id_idempotency_key (member_id, idempotency_key),
    KEY idx_orders_member_id_status (member_id, status),
    KEY idx_orders_status_created_at (status, created_at),
    KEY idx_orders_status_paid_at (status, paid_at),
    CONSTRAINT fk_orders_member
        FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='주문';

CREATE TABLE order_items (
    id                    BIGINT        NOT NULL AUTO_INCREMENT                    COMMENT '주문 상품 ID',
    order_id              BIGINT        NOT NULL                                   COMMENT '주문 ID',
    product_id            BIGINT        NOT NULL                                   COMMENT '상품 ID',
    product_name          VARCHAR(200)  NOT NULL                                   COMMENT '주문 당시 상품명',
    product_image_url     VARCHAR(500)  NULL                                       COMMENT '주문 당시 상품 이미지 (상품에 이미지가 없으면 NULL)',
    quantity              INT           NOT NULL DEFAULT 1                         COMMENT '수량',
    confirmed_quantity    INT           NULL                                       COMMENT '구매 확정 수량 (주문 수량 − 완료된 환불 수량, 확정 전 NULL)',
    list_price            INT           NOT NULL DEFAULT 0                         COMMENT '주문 당시 정가',
    unit_price            INT           NOT NULL DEFAULT 0                         COMMENT '실제 단가 (할인가 또는 정가)',
    sale_type             ENUM('NONE', 'SPECIAL', 'FLASH_DROP') NOT NULL DEFAULT 'NONE' COMMENT '할인 구분 (일반/특가/번개 드롭, 번개 드롭은 환불 불가)',
    sale_event_item_id    BIGINT        NULL                                       COMMENT '할인 행사 상품 ID (특가·번개 드롭으로 샀을 때만, 일반은 NULL)',
    flash_drop_member_id  BIGINT        NULL                                       COMMENT '번개 드롭 구매 회원 ID (번개 드롭 줄만, 1인 1개 확인용. 취소하면 NULL)',
    created_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)      COMMENT '생성일시',
    updated_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_items_sale_event_item_id_flash_drop_member_id (sale_event_item_id, flash_drop_member_id),
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_items_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_order_items_sale_event_item
        FOREIGN KEY (sale_event_item_id) REFERENCES sale_event_items (id),
    CONSTRAINT fk_order_items_member
        FOREIGN KEY (flash_drop_member_id) REFERENCES members (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='주문 상품 (주문 당시 가격 기록)';

-- ---------------------------------------------------------
-- [용범] 결제 · 환불 · 정산
-- ---------------------------------------------------------
-- 빌링키는 암호문과 지문으로만 저장한다. 암호화 전 DB에 남은 평문 billing_key 컬럼은 서버 기동 때
-- LegacyBillingKeyEncryptor가 NULL 허용으로 바꾸고 비운다(컬럼 삭제는 이후 따로 한다). 새로 만드는 DB에는 없다
CREATE TABLE billing_keys (
    id                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '등록 카드 ID',
    member_id              BIGINT       NOT NULL COMMENT '카드 주인 회원 ID',
    billing_key_ciphertext VARCHAR(512) NOT NULL COMMENT 'PortOne 빌링키 AES-256-GCM 암호문(v1:{키ID}:{Base64}). 응답으로 내려주지 않음',
    billing_key_hash       CHAR(64)     NOT NULL COMMENT 'PortOne 빌링키 HMAC-SHA256 지문(16진수 64자). 같은 빌링키 중복 저장 방지·조회용',
    card_name              VARCHAR(100) NULL COMMENT '카드 상품명(PortOne 빌링키 조회 card.name)',
    card_last4             CHAR(4)      NULL COMMENT '카드 번호 끝 4자리(PortOne 마스킹 카드 번호 card.number에서 추출)',
    card_number_masked     VARCHAR(30)  NULL COMMENT '마스킹된 카드 번호(PortOne card.number). 같은 카드 중복 등록 확인용',
    card_issuer_code       VARCHAR(30)  NULL COMMENT '카드 발급사 코드(PortOne card.issuer). 같은 카드 중복 등록 확인용',
    card_type              ENUM('CREDIT','DEBIT','GIFT') NULL COMMENT '신용·체크·기프트. PortOne이 주지 않으면 NULL',
    is_default             BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '대표 카드 여부(회원당 사용 중 카드 중 하나)',
    status                 ENUM('ACTIVE','DELETED') NOT NULL DEFAULT 'ACTIVE' COMMENT '사용 중·삭제',
    deleted_at             DATETIME(6)  NULL COMMENT '삭제 시각',
    pg_deleted_at          DATETIME(6)  NULL COMMENT 'PortOne 빌링키 삭제 확인 시각. 삭제 상태인데 NULL이면 재시도 대상',
    billing_key            VARCHAR(200) NULL COMMENT '암호화 전 평문 빌링키(엔티티 매핑 없음). 서버 기동 때 암호화하고 NULL로 비움',
    created_at             DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시(등록 시각)',
    updated_at             DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_billing_keys_billing_key_hash UNIQUE (billing_key_hash),
    INDEX idx_billing_keys_member_status (member_id, status),
    INDEX idx_billing_keys_status_pg_deleted_at (status, pg_deleted_at),
    CONSTRAINT fk_billing_keys_member FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '등록 카드';

CREATE TABLE payment_pins (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '결제 비밀번호 ID',
    member_id    BIGINT       NOT NULL COMMENT '회원 ID',
    pin_hash     VARCHAR(100) NOT NULL COMMENT '결제 비밀번호 해시(BCrypt). 원래 값은 저장하지 않음',
    failed_count INT          NOT NULL DEFAULT 0 COMMENT '연속으로 틀린 횟수. 맞히거나 재설정하면 0',
    locked_at    DATETIME(6)  NULL COMMENT '5번 연속 틀려 잠긴 시각. 잠기지 않았으면 NULL',
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시(처음 설정한 시각)',
    updated_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_pins_member_id UNIQUE (member_id),
    CONSTRAINT fk_payment_pins_member FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '등록 카드 결제 비밀번호';

CREATE TABLE payments (
    id                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT '결제 ID',
    order_id           BIGINT        NOT NULL COMMENT '주문 ID (결제 회원은 주문으로 확인, 한 주문에 결제 시도 여러 건)',
    billing_key_id     BIGINT        NULL     COMMENT '등록 카드 결제면 그 카드 ID (결제창 결제는 NULL)',
    portone_payment_id VARCHAR(100)  NOT NULL COMMENT '서버가 발급해 포트원에 넘기는 결제 ID (payment- + UUID 32자, 멱등 기준)',
    amount             BIGINT        NOT NULL DEFAULT 0 COMMENT '결제 금액 (서버 계산, 주문 금액)',
    pg_provider        ENUM('KAKAOPAY', 'KICC', 'INICIS', 'TOSSPAYMENTS', 'TOSSPAY') NOT NULL COMMENT '결제대행사 (고른 값, 승인 때 PG에서 확인한 실제 값으로 고침)',
    status             ENUM('READY', 'PAID', 'FAILED') NOT NULL DEFAULT 'READY' COMMENT '결제 상태 (결제 대기·결제 완료·실패)',
    fail_reason        ENUM('PG_FAILED', 'USER_CANCELLED', 'AMOUNT_MISMATCH', 'TIMEOUT', 'ORDER_NOT_PAYABLE', 'SUPERSEDED') NULL COMMENT '결제 실패 사유',
    expires_at         DATETIME(6)   NOT NULL COMMENT '결제 대기 만료 시각 (결제 생성 시각 + 10분, 결제 시도마다 따로 정함)',
    paid_at            DATETIME(6)   NULL     COMMENT '결제 완료 시각',
    created_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_portone_payment_id (portone_payment_id),
    KEY idx_payments_order_id (order_id),
    KEY idx_payments_billing_key_id (billing_key_id),
    KEY idx_payments_status_expires_at (status, expires_at),
    KEY idx_payments_paid_at (paid_at),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_payments_billing_key FOREIGN KEY (billing_key_id) REFERENCES billing_keys (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '결제';

CREATE TABLE payment_histories (
    id                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT '결제 이력 ID',
    payment_id         BIGINT        NOT NULL COMMENT '결제 ID',
    event_type         ENUM('READY', 'PAID', 'FAILED', 'PARTIAL_CANCELLED', 'CANCELLED') NOT NULL COMMENT '결제에 일어난 일 (payments.status와 별개, 취소는 PG 기준)',
    amount             BIGINT        NULL     COMMENT '이번 사건 금액 (결제·취소 금액, 실패는 NULL)',
    cancelled_amount   BIGINT        NOT NULL DEFAULT 0 COMMENT '이 사건 직후 누적 취소 금액',
    refund_id          BIGINT        NULL     COMMENT '환불로 생긴 취소면 환불 ID (보상·콘솔 취소는 NULL)',
    pg_cancellation_id VARCHAR(200)  NULL     COMMENT 'PG 취소 건 ID',
    changed_by         ENUM('USER', 'WEBHOOK', 'SCHEDULER') NOT NULL COMMENT '사건을 일으킨 주체',
    reason             VARCHAR(255)  NULL     COMMENT '사유 (결제 대기 만료, 금액 불일치, PG 거절, 보상 취소, 단순 변심 환불 등)',
    created_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시 (사건 시각)',
    updated_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    KEY idx_payment_histories_payment_id (payment_id, created_at),
    KEY idx_payment_histories_refund_id (refund_id),
    KEY idx_payment_histories_created_at (created_at),
    CONSTRAINT fk_payment_histories_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '결제 이력 (상태 변경과 PG 취소 사건)';

CREATE TABLE refunds (
    id                       BIGINT        NOT NULL AUTO_INCREMENT COMMENT '환불 ID',
    payment_id               BIGINT        NOT NULL COMMENT '결제 ID',
    refund_amount            BIGINT        NOT NULL DEFAULT 0 COMMENT '환불 금액',
    reason_type              ENUM('CHANGE_OF_MIND', 'ORDER_MISTAKE', 'NO_LONGER_NEEDED', 'ETC') NOT NULL COMMENT '환불 사유 종류',
    reason_detail            VARCHAR(500)  NULL     COMMENT '상세 사유 (선택 입력)',
    status                   ENUM('REQUESTED', 'COMPLETED', 'FAILED') NOT NULL DEFAULT 'REQUESTED' COMMENT '환불 처리 상태',
    portone_cancellation_id  VARCHAR(200)  NULL     COMMENT '포트원 취소 ID',
    fail_reason              VARCHAR(255)  NULL     COMMENT '포트원 취소 실패 사유',
    refunded_at              DATETIME(6)   NULL     COMMENT '환불 완료 시각',
    created_at               DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at               DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    KEY idx_refunds_refunded_at (refunded_at),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '환불';

CREATE TABLE refund_items (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '환불 상품 ID',
    refund_id      BIGINT       NOT NULL COMMENT '환불 ID',
    order_item_id  BIGINT       NOT NULL COMMENT '주문 상품 ID',
    quantity       INT          NOT NULL DEFAULT 0 COMMENT '환불 수량',
    unit_price     INT          NOT NULL DEFAULT 0 COMMENT '주문 당시 단가 (주문 상품 스냅샷)',
    amount         BIGINT       NOT NULL DEFAULT 0 COMMENT '환불 금액 (단가 × 수량)',
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    updated_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_items_refund_id_order_item_id (refund_id, order_item_id),
    KEY idx_refund_items_order_item_id (order_item_id),
    CONSTRAINT fk_refund_items_refund     FOREIGN KEY (refund_id)     REFERENCES refunds (id),
    CONSTRAINT fk_refund_items_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '환불 상품';

CREATE TABLE webhook_events (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '웹훅 이벤트 ID',
    payment_id      BIGINT        NULL     COMMENT '결제 ID (위변조·매칭 실패 시 NULL)',
    webhook_id      VARCHAR(200)  NOT NULL COMMENT '포트원 웹훅 ID (중복 수신 차단)',
    event_type      VARCHAR(50)   NOT NULL COMMENT '포트원 이벤트 종류 (예: Transaction.Paid)',
    process_status  ENUM('RECEIVED', 'PROCESSED', 'FAILED', 'IGNORED')
                                  NOT NULL DEFAULT 'RECEIVED' COMMENT '처리 상태',
    raw_payload     TEXT          NOT NULL COMMENT '수신한 원문',
    fail_reason     VARCHAR(500)  NULL     COMMENT '처리 실패 사유',
    processed_at    DATETIME(6)   NULL     COMMENT '처리 완료 시각',
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시 (수신 시각)',
    updated_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_webhook_events_webhook_id (webhook_id),
    KEY idx_webhook_events_payment_id (payment_id),
    KEY idx_webhook_events_process_status (process_status, created_at),
    CONSTRAINT fk_webhook_events_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '포트원 웹훅 수신 이력';

CREATE TABLE daily_sales (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '일별 매출 ID',
    sales_date       DATE         NOT NULL COMMENT '매출 날짜 (한국 시간 기준)',
    paid_amount      BIGINT       NOT NULL DEFAULT 0 COMMENT '결제 금액 합계 (결제 완료일 기준)',
    refunded_amount  BIGINT       NOT NULL DEFAULT 0 COMMENT '환불 금액 합계 (환불 완료일 기준, 부분 환불 포함)',
    net_amount BIGINT GENERATED ALWAYS AS (paid_amount - refunded_amount) STORED COMMENT '순매출 (결제 금액 − 환불 금액, 자동 계산)',
    paid_count       INT          NOT NULL DEFAULT 0 COMMENT '결제 건수',
    refunded_count   INT          NOT NULL DEFAULT 0 COMMENT '환불 건수 (환불·콘솔 취소, 부분 환불도 1건)',
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시 (최초 집계 시각)',
    updated_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 일시 (마지막 집계 시각)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_daily_sales_sales_date (sales_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '일별 매출 정산';

-- ---------------------------------------------------------
-- [현정] 냉장고
-- ---------------------------------------------------------
CREATE TABLE fridge (
    id               BIGINT          NOT NULL AUTO_INCREMENT                    COMMENT '냉장고 재료 ID',
    member_id        BIGINT          NOT NULL                                   COMMENT '회원 ID',
    ingredient_id    BIGINT          NOT NULL                                   COMMENT '표준 재료 ID (사전에 없으면 ING_CUSTOM, 밀키트는 ING_MEALKIT)',
    order_item_id    BIGINT          NULL                                       COMMENT '주문 상품 ID (커머스 자동 입고일 때만, 직접 등록은 NULL)',
    product_id       BIGINT          NULL                                       COMMENT '구매 확정으로 들어온 줄의 상품 ID (상품 도메인, 외래키 없음. 직접 등록은 NULL)',
    raw_name         VARCHAR(200)    NOT NULL                                   COMMENT '사용자 입력 원문 (커머스 입고는 상품명)',
    quantity         DECIMAL(10, 2)  NOT NULL DEFAULT 0                         COMMENT '수량 (단위는 ingredients.base_unit)',
    storage          ENUM('REFRIGERATED', 'FROZEN', 'ROOM_TEMP') NOT NULL       COMMENT '보관 방식 (냉장/냉동/실온)',
    expiration_date  DATE            NOT NULL                                   COMMENT '소비기한',
    source           ENUM('MANUAL', 'COMMERCE') NOT NULL DEFAULT 'MANUAL'       COMMENT '등록 경로 (직접 등록/커머스 자동 입고)',
    created_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)      COMMENT '생성일시',
    updated_at       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    KEY idx_fridge_expiration_date_member_id (expiration_date, member_id),
    KEY idx_fridge_member_id_expiration_date (member_id, expiration_date),
    CONSTRAINT fk_fridge_member
        FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_fridge_ingredient
        FOREIGN KEY (ingredient_id) REFERENCES ingredients (id),
    CONSTRAINT fk_fridge_order_item
        FOREIGN KEY (order_item_id) REFERENCES order_items (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='냉장고 보관 재료 (현재 보관 중인 재료만 저장)';

CREATE TABLE fridge_commerce_histories (
    id             BIGINT       NOT NULL AUTO_INCREMENT                    COMMENT '커머스 입고 이력 ID',
    member_id      BIGINT       NOT NULL                                   COMMENT '회원 ID',
    order_item_id  BIGINT       NOT NULL                                   COMMENT '주문 상품 ID (구매 확정된 주문 상품)',
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)      COMMENT '생성일시 (구매 확정 이벤트 수신 시각)',
    updated_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_fridge_commerce_histories_order_item_id (order_item_id),
    CONSTRAINT fk_fridge_commerce_histories_member
        FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_fridge_commerce_histories_order_item
        FOREIGN KEY (order_item_id) REFERENCES order_items (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='커머스 구매 확정에 따른 냉장고 자동 입고 이력 (중복 입고 방지)';

-- ---------------------------------------------------------
-- [현정] AI 상담
-- ---------------------------------------------------------
CREATE TABLE chat_sessions (
    id               BIGINT       NOT NULL AUTO_INCREMENT                    COMMENT '상담 세션 ID',
    member_id        BIGINT       NOT NULL                                   COMMENT '회원 ID',
    status           ENUM('ACTIVE', 'COMPLETED', 'EXPIRED') NOT NULL DEFAULT 'ACTIVE' COMMENT '세션 상태 (진행 중/요리 완료로 종료/시간 초과로 만료. 종료된 세션은 다시 열리지 않음)',
    last_message_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)      COMMENT '마지막 대화 일시',
    active_member_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN member_id END) STORED
                                                                             COMMENT '진행 중일 때만 회원 ID (회원당 진행 중 상담 1개 보장용 계산 컬럼, 아니면 NULL)',
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)      COMMENT '생성일시 (상담 시작 시각)',
    updated_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_sessions_active_member_id (active_member_id),
    KEY idx_chat_sessions_member_id_status (member_id, status),
    KEY idx_chat_sessions_member_id_last_message_at (member_id, last_message_at),
    KEY idx_chat_sessions_status_last_message_at (status, last_message_at),
    CONSTRAINT fk_chat_sessions_member
        FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 냉장고 상담 세션';

CREATE TABLE chat_messages (
    id               BIGINT       NOT NULL AUTO_INCREMENT                    COMMENT '메시지 ID',
    chat_session_id  BIGINT       NOT NULL                                   COMMENT '상담 세션 ID',
    sequence         INT          NOT NULL DEFAULT 0                         COMMENT '세션 안 메시지 순서 (1부터 증가)',
    role             ENUM('USER', 'ASSISTANT') NOT NULL                      COMMENT '보낸 쪽 (사용자/AI)',
    content          TEXT         NOT NULL                                   COMMENT '메시지 원문 (AI는 상세 조리법 전체)',
    filtered         BOOLEAN      NOT NULL DEFAULT FALSE                     COMMENT '안전 장치로 걸러진 메시지 여부 (TRUE면 AI 문맥·상담 내역 미리보기에서 제외, 대화 화면에는 표시)',
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)      COMMENT '생성일시',
    updated_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_messages_chat_session_id_sequence (chat_session_id, sequence),
    CONSTRAINT fk_chat_messages_chat_session
        FOREIGN KEY (chat_session_id) REFERENCES chat_sessions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 상담 메시지 내역';

CREATE TABLE chat_recommendations (
    id               BIGINT       NOT NULL AUTO_INCREMENT                    COMMENT '추천 ID',
    chat_message_id  BIGINT       NOT NULL                                   COMMENT '추천이 나온 AI 메시지 ID',
    recipe_id        BIGINT       NULL                                       COMMENT '추천 레시피 ID (레시피 추천일 때만)',
    product_id       BIGINT       NULL                                       COMMENT '추천 상품 ID (재료 상품·밀키트 추천일 때만. 종류는 products.product_type)',
    display_order    INT          NOT NULL DEFAULT 0                         COMMENT '메시지 안 추천 순서',
    description      TEXT         NULL                                       COMMENT '카드 아래 AI 설명 (레시피 추천만, 상품 추천·표시를 빼먹은 답은 NULL)',
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)      COMMENT '생성일시',
    updated_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_recommendations_chat_message_id_display_order (chat_message_id, display_order),
    CONSTRAINT fk_chat_recommendations_chat_message
        FOREIGN KEY (chat_message_id) REFERENCES chat_messages (id),
    CONSTRAINT fk_chat_recommendations_recipe
        FOREIGN KEY (recipe_id) REFERENCES recipes (id),
    CONSTRAINT fk_chat_recommendations_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_chat_recommendations_one_target CHECK (
        (recipe_id IS NOT NULL) + (product_id IS NOT NULL) = 1
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 상담 추천 결과 (레시피 또는 상품 중 하나)';

-- ---------------------------------------------------------
-- [현정] 요리 기록 · 재료 이력 · 후기 · 알림
-- ---------------------------------------------------------
CREATE TABLE cooking_logs (
    id               BIGINT        NOT NULL AUTO_INCREMENT                    COMMENT '요리 기록 ID',
    member_id        BIGINT        NOT NULL                                   COMMENT '회원 ID',
    recipe_id        BIGINT        NOT NULL                                   COMMENT '레시피 ID',
    chat_session_id  BIGINT        NULL                                       COMMENT 'AI 상담 세션 ID (상담에서 추천받아 요리했을 때만, 아니면 NULL)',
    result           ENUM('SUCCESS', 'FAILED') NOT NULL                       COMMENT '요리 결과 (성공/실패)',
    memo             VARCHAR(50)   NULL                                       COMMENT '요리 메모 (선택, 본인만 열람)',
    created_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)      COMMENT '생성일시 (요리 완료 시각)',
    updated_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    KEY idx_cooking_logs_member_id_recipe_id_created_at (member_id, recipe_id, created_at),
    KEY idx_cooking_logs_member_id_created_at (member_id, created_at),
    KEY idx_cooking_logs_recipe_id_result_member_id (recipe_id, result, member_id),
    CONSTRAINT fk_cooking_logs_member
        FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_cooking_logs_recipe
        FOREIGN KEY (recipe_id) REFERENCES recipes (id),
    CONSTRAINT fk_cooking_logs_chat_session
        FOREIGN KEY (chat_session_id) REFERENCES chat_sessions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='요리 기록 (요리 완료할 때마다 1건)';

CREATE TABLE fridge_item_logs (
    id              BIGINT          NOT NULL AUTO_INCREMENT                    COMMENT '재료 이력 ID',
    member_id       BIGINT          NOT NULL                                   COMMENT '회원 ID',
    ingredient_id   BIGINT          NOT NULL                                   COMMENT '표준 재료 ID',
    cooking_log_id  BIGINT          NULL                                       COMMENT '요리 기록 ID (요리 완료로 사용했을 때만, 그냥 먹거나 버리면 NULL)',
    fridge_id       BIGINT          NOT NULL                                   COMMENT '원래 냉장고 재료 ID (참고용, FK 아님. 원본 행은 삭제될 수 있음)',
    raw_name        VARCHAR(200)    NOT NULL                                   COMMENT '사용자 입력 원문 (냉장고 재료에서 복사)',
    quantity        DECIMAL(10, 2)  NOT NULL DEFAULT 0                         COMMENT '빠진 수량 (단위는 ingredients.base_unit)',
    action          ENUM('CONSUMED', 'DISCARDED') NOT NULL                     COMMENT '처리 구분 (소진/폐기)',
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)      COMMENT '생성일시 (재료가 빠진 시각)',
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    KEY idx_fridge_item_logs_member_id_created_at (member_id, created_at),
    CONSTRAINT fk_fridge_item_logs_member
        FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_fridge_item_logs_ingredient
        FOREIGN KEY (ingredient_id) REFERENCES ingredients (id),
    CONSTRAINT fk_fridge_item_logs_cooking_log
        FOREIGN KEY (cooking_log_id) REFERENCES cooking_logs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='냉장고 재료 소진·폐기 이력';

CREATE TABLE recipe_reviews (
    id          BIGINT        NOT NULL AUTO_INCREMENT                    COMMENT '후기 ID',
    member_id   BIGINT        NOT NULL                                   COMMENT '회원 ID',
    recipe_id   BIGINT        NOT NULL                                   COMMENT '레시피 ID',
    rating      INT           NOT NULL DEFAULT 0                         COMMENT '별점 (1~5)',
    content     VARCHAR(100)  NOT NULL                                   COMMENT '한 줄 후기 (최대 100자)',
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)      COMMENT '생성일시 (처음 작성한 시각)',
    updated_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시 (마지막으로 고친 시각)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_recipe_reviews_member_id_recipe_id (member_id, recipe_id),
    KEY idx_recipe_reviews_recipe_id_created_at (recipe_id, created_at),
    KEY idx_recipe_reviews_created_at (created_at),
    CONSTRAINT fk_recipe_reviews_member
        FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_recipe_reviews_recipe
        FOREIGN KEY (recipe_id) REFERENCES recipes (id),
    CONSTRAINT chk_recipe_reviews_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='레시피 후기 (회원당 레시피 1개, 수정 가능)';

CREATE TABLE notifications (
    id                 BIGINT        NOT NULL AUTO_INCREMENT                    COMMENT '알림 ID',
    member_id          BIGINT        NOT NULL                                   COMMENT '받는 회원 ID',
    notification_type  ENUM('EXPIRATION', 'FLASH_DROP', 'COMMERCE_STOCKED') NOT NULL COMMENT '알림 종류 (소비기한 임박/번개 드롭/커머스 자동 입고)',
    content            VARCHAR(200)  NOT NULL                                   COMMENT '알림 문구 (예: 두부 소비기한이 3일 남았어요)',
    link_url           VARCHAR(500)  NOT NULL                                   COMMENT '알림 클릭 시 이동 경로 (예: /fridge)',
    dedupe_key         VARCHAR(150)  NOT NULL                                   COMMENT '중복 방지 키 (예: EXPIRATION:123:2026-10-14)',
    is_read            BOOLEAN       NOT NULL DEFAULT FALSE                     COMMENT '읽음 여부',
    deleted_at         DATETIME(6)   NULL                                       COMMENT '삭제일시 (지운 알림도 행을 남겨 같은 중복 방지 키로 되살아나지 않게 함)',
    created_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)      COMMENT '생성일시',
    updated_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_notifications_member_id_dedupe_key (member_id, dedupe_key),
    KEY idx_notifications_member_id_is_read (member_id, is_read),
    KEY idx_notifications_member_id_notification_type_created_at (member_id, notification_type, created_at),
    CONSTRAINT fk_notifications_member
        FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='인앱 알림 (모든 도메인 공통)';

-- ---------------------------------------------------------
-- [준모] 통합검색 기록 (로그 테이블이라 FK를 걸지 않는다)
-- ---------------------------------------------------------
CREATE TABLE search_click_histories (
    id          BIGINT        NOT NULL AUTO_INCREMENT                    COMMENT '상품 클릭 기록 ID',
    product_id  BIGINT        NOT NULL                                   COMMENT '클릭한 상품 ID (참고용, FK 아님)',
    member_id   BIGINT        NULL                                       COMMENT '클릭한 회원 ID (비로그인 클릭은 NULL, FK 아님)',
    keyword     VARCHAR(100)  NOT NULL                                   COMMENT '검색어',
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)      COMMENT '생성일시 (클릭 시각)',
    updated_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    KEY idx_search_click_histories_created_at_product_id (created_at, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='통합검색 결과 상품 클릭 기록 (할인 분석이 읽음)';

CREATE TABLE search_recipe_click_histories (
    id          BIGINT        NOT NULL AUTO_INCREMENT                    COMMENT '레시피 클릭 기록 ID',
    recipe_id   BIGINT        NOT NULL                                   COMMENT '클릭한 레시피 ID (참고용, FK 아님)',
    member_id   BIGINT        NULL                                       COMMENT '클릭한 회원 ID (비로그인 클릭은 NULL, FK 아님)',
    keyword     VARCHAR(100)  NOT NULL                                   COMMENT '검색어',
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)      COMMENT '생성일시 (클릭 시각)',
    updated_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    KEY idx_search_recipe_click_histories_created_at_recipe_id (created_at, recipe_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='통합검색 결과 레시피 클릭 기록 (상품 클릭과 분리)';

CREATE TABLE search_zero_result_histories (
    id          BIGINT        NOT NULL AUTO_INCREMENT                    COMMENT '0건 검색 기록 ID',
    target      ENUM('RECIPE', 'PRODUCT') NOT NULL                       COMMENT '검색 대상 (레시피/상품)',
    keyword     VARCHAR(50)   NOT NULL                                   COMMENT '검색어',
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)      COMMENT '생성일시 (검색 시각)',
    updated_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    KEY idx_search_zero_result_histories_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='결과가 0건이었던 통합검색 기록 (동의어·사전 보강용)';
