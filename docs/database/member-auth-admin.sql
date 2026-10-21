-- 냉장고를 부탁해 · 회원/인증/관리자 도메인 테이블 (8개)
-- 팀 "도메인 생성 규칙" 적용: 복수형 snake_case 테이블, PK id BIGINT AUTO_INCREMENT,
-- FK [단수형 참조테이블명]_id + 물리 외래키, ENUM 상태값, created_at/updated_at DATETIME(6), 한글 COMMENT
-- product_id, promotion_id 는 다른 도메인(상품·할인 행사) 소유라 물리 외래키를 걸지 않음
-- 상태: 초안. DB 제품·버전(Q-07)과 감사 시각 관리 주체(Q-06) 확정 전에는 실행하지 않음. 컬럼 설명은 data-dictionary.md

-- 1. 회원
CREATE TABLE members (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '회원 ID',
    email         VARCHAR(255) NOT NULL COMMENT '로그인 이메일',
    password_hash VARCHAR(255) NOT NULL COMMENT '비밀번호 해시(복원 불가)',
    nickname      VARCHAR(30)  NOT NULL COMMENT '닉네임(후기 표시 이름)',
    role          ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER' COMMENT '역할(가입 시 USER, 관리자 승격 시 ADMIN)',
    status        ENUM('ACTIVE', 'SUSPENDED') NOT NULL DEFAULT 'ACTIVE' COMMENT '계정 상태(정상/정지)',
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '가입일시',
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_members_email UNIQUE (email),
    CONSTRAINT uk_members_nickname UNIQUE (nickname)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '회원';

-- 2. 맛 선호 (회원 1명이 여러 개 선택)
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

-- 3. 로그인 세션 (갱신 토큰)
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

-- 4. 주간 예측 실행
CREATE TABLE prediction_runs (
    id                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '예측 실행 ID',
    target_week_start DATE        NOT NULL COMMENT '예측 대상 주 시작일(월요일)',
    model_version     VARCHAR(50) NOT NULL COMMENT '예측 모델 버전(예: fc-v0.3)',
    data_cutoff_at    DATETIME(6) NOT NULL COMMENT '입력 데이터 기준 시각',
    confirmed_at      DATETIME(6) NULL COMMENT '실제 판매량 확정일시(다음 주 월요일 06:00)',
    synthetic_data    BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '가상 데이터 사용 여부',
    created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '예측 생성일시(일요일 22:00)',
    updated_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT uk_prediction_runs_target_week_start UNIQUE (target_week_start)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '주간 판매 예측 실행';

-- 5. 상품별 예측 · 실제 판매량
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

-- 6. 할인 후보 분석 ("후보 분석" 버튼 1번 = 1건)
CREATE TABLE deal_analyses (
    id                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '할인 후보 분석 ID',
    member_id         BIGINT      NOT NULL COMMENT '분석한 관리자 회원 ID',
    target_week_start DATE        NOT NULL COMMENT '대상 주 시작일(월요일)',
    day_of_week       ENUM('MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN') NOT NULL COMMENT '적용 요일',
    discount_rate     INT         NOT NULL DEFAULT 0 COMMENT '가상 할인율(%, 10~40)',
    min_stock         INT         NOT NULL DEFAULT 50 COMMENT '최소 재고 기준(개)',
    model_version     VARCHAR(50) NOT NULL COMMENT '분석 모델 버전',
    data_cutoff_at    DATETIME(6) NOT NULL COMMENT '입력 데이터 기준 시각',
    synthetic_data    BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '가상 데이터 사용 여부',
    last_saved_at     DATETIME(6) NULL COMMENT '검토 결과 마지막 저장일시',
    created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '분석일시',
    updated_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT fk_deal_analyses_member FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '요일별 할인 후보 분석';

-- 7. 분석 결과 상품 (후보 + 제외 상품을 한 테이블에 저장, is_excluded 로 구분)
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

-- 8. 관리자 작업 이력
CREATE TABLE admin_audit_logs (
    id           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '작업 이력 ID',
    member_id    BIGINT      NOT NULL COMMENT '작업한 회원 ID(권한 없이 시도한 일반 회원 포함)',
    actor_role   ENUM('USER', 'ADMIN') NULL COMMENT '작업 당시 역할',
    action       ENUM('PRODUCT_CREATE', 'PRODUCT_DELETE', 'PRICE_UPDATE', 'DEAL_REVIEW', 'EVENT_REQUEST', 'ROLE_CHANGE') NOT NULL COMMENT '작업 종류',
    target_type  VARCHAR(50) NOT NULL COMMENT '작업 대상 종류(Product, DealCandidate, Member 등)',
    target_id    BIGINT      NULL COMMENT '작업 대상 ID(등록 실패처럼 대상이 없으면 NULL)',
    result       ENUM('SUCCESS', 'FAILED', 'DENIED') NOT NULL COMMENT '결과(성공/실패/권한 거부)',
    before_value JSON        NULL COMMENT '변경 전 값',
    after_value  JSON        NULL COMMENT '변경 후 값',
    created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '작업일시',
    updated_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    PRIMARY KEY (id),
    CONSTRAINT fk_admin_audit_logs_member FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '관리자 작업 이력';
