# 냉장고를 부탁해 ERD (전체 도메인 물리 설계)

> 기준: 최신 전체 스키마 DDL ([schema.sql](schema.sql))  
> 총 43개 테이블에 대한 도메인별 데이터 개체, 외래키(FK) 및 연관 관계를 정의합니다.

---

## 1. 전체 도메인 개요

| 도메인 | 테이블 목록 (총 42개) | 주요 설명 |
|---|---|---|
| **회원 · 인증** (3) | `members`, `member_tastes`, `refresh_sessions` | 회원 계정, 선호 맛, 로그인 세션 |
| **운영 · 분석** (5) | `prediction_runs`, `sales_forecasts`, `deal_analyses`, `deal_candidates`, `admin_audit_logs` | PyTorch 판매 예측, 요일별 할인 후보 분석, 관리자 작업 감사 로그 |
| **표준 재료 · 상품** (4) | `ingredients`, `products`, `ingredient_products`, `mealkit_products` | 표준 재료 사전, 판매 상품 공통, 재료 상품 상세, 밀키트 상품 상세 |
| **레시피** (4) | `recipes`, `recipe_ingredients`, `recipe_steps`, `recipe_tastes` | 레시피 기본 정보, 레시피-재료 연결, 조리 단계, 레시피 맛 |
| **할인 행사** (2) | `sale_events`, `sale_event_items` | 특가 및 번개 드롭 행사, 행사 대상 상품 |
| **장바구니 · 주문** (4) | `carts`, `cart_items`, `orders`, `order_items` | 회원 장바구니 및 품목, 주문 정보 스냅샷 및 주문 상품 |
| **결제 · 환불 · 정산** (8) | `payments`, `payment_histories`, `refunds`, `refund_items`, `webhook_events`, `daily_sales`, `billing_keys`, `payment_pins` | PortOne 결제, 결제 원장 이력, 부분/전체 환불, 포트원 웹훅, 일별 정산 집계, 등록 카드 및 결제 PIN |
| **냉장고** (2) | `fridge`, `fridge_commerce_histories` | 회원 냉장고 재료 및 소비기한, 커머스 구매 확정 자동 입고 이력 |
| **AI 상담** (3) | `chat_sessions`, `chat_messages`, `chat_recommendations` | AI 상담 세션, 대화 메시지 내역, 레시피/상품 추천 결과 |
| **요리 · 후기** (3) | `cooking_logs`, `fridge_item_logs`, `recipe_reviews` | 요리 완료 기록, 재료 소진/폐기 이력, 레시피 별점 및 후기 |
| **알림** (1) | `notifications` | 인앱 실시간/비동기 알림 (소비기한, 번개드롭, 입고) |
| **통합검색 기록** (3) | `search_click_histories`, `search_recipe_click_histories`, `search_zero_result_histories` | 검색 결과 상품·레시피 클릭 기록, 결과 0건 검색어 기록 |

---

## 2. 통합 서비스 핵심 관계도 (Core Architecture)

핵심 도메인(회원, 냉장고, 레시피, 상품, 주문, 결제) 간의 유기적인 참조 관계입니다.

```mermaid
erDiagram
    members ||--o{ fridge : "보유 재료"
    members ||--o{ orders : "주문"
    members ||--o{ cooking_logs : "요리 기록"
    members ||--o{ chat_sessions : "AI 상담"
    
    ingredients ||--o{ fridge : "표준 재료 매핑"
    ingredients ||--o{ recipe_ingredients : "필요 재료"
    ingredients ||--o{ ingredient_products : "원재료 매핑"

    recipes ||--o{ recipe_ingredients : "포함"
    recipes ||--o{ recipe_steps : "조리 단계"
    recipes ||--o{ cooking_logs : "요리 대상"
    recipes ||--o{ recipe_reviews : "후기"
    recipes |o--o{ mealkit_products : "동일 요리 연결"

    products ||--o| ingredient_products : "재료 상품 상세"
    products ||--o| mealkit_products : "밀키트 상품 상세"
    products ||--o{ order_items : "주문 대상"

    orders ||--o{ order_items : "주문 품목"
    orders ||--o{ payments : "결제 시도"
    order_items |o--o| fridge : "구매 확정 자동 입고"

    payments ||--o{ refunds : "환불"
    payments ||--o{ payment_histories : "상태 변경 원장"
```

---

## 3. 도메인별 세부 ERD

### 3.1 회원 · 인증 도메인
회원 계정 정보와 회원의 선호 맛(1:N), 로그인 세션(1:N)을 관리합니다.

```mermaid
erDiagram
    members ||--o{ member_tastes : "선호 맛 선택"
    members ||--o{ refresh_sessions : "로그인 세션 발급"

    members {
        bigint id PK "회원 ID"
        varchar email UK "로그인 이메일"
        varchar password_hash "비밀번호 해시"
        varchar nickname UK "닉네임"
        varchar phone UK "전화번호 숫자 11자리"
        enum role "USER / ADMIN"
        enum status "ACTIVE / SUSPENDED"
        datetime created_at
        datetime updated_at
    }

    member_tastes {
        bigint id PK "맛 선호 ID"
        bigint member_id FK "회원 ID"
        enum taste "SALTY, SWEET, SPICY, UMAMI, MILD"
    }

    refresh_sessions {
        bigint id PK "세션 ID"
        bigint member_id FK "회원 ID"
        varchar token_hash UK "갱신 토큰 해시"
        boolean keep_logged_in "로그인 유지 여부"
        varchar device_info "기기 정보 (엔티티 매핑 없음, 항상 NULL)"
        datetime last_used_at "마지막 사용일시"
        datetime expires_at "만료일시"
        datetime revoked_at "폐기일시"
    }
```

---

### 3.2 표준 재료 & 상품 도메인
표준 재료는 다양한 상품명을 하나의 표준 명칭으로 통합하여 레시피 및 냉장고와 연계하는 기준점입니다. 상품은 재료 상품과 밀키트 상품으로 나뉩니다.

```mermaid
erDiagram
    ingredients ||--o{ ingredient_products : "기본 재료"
    products ||--o| ingredient_products : "재료 상품 상세 (1:1)"
    products ||--o| mealkit_products : "밀키트 상품 상세 (1:1)"
    recipes ||--o{ mealkit_products : "관련 레시피 (논리적 연결)"

    ingredients {
        bigint id PK "표준 재료 ID"
        varchar ingredient_code UK "표준 코드 (ING_RCP_xxx)"
        varchar name UK "대표 명칭 (대파, 마늘 등)"
        varchar base_unit "기본 단위 (개, g, ml 등)"
        decimal unit_weight_g "1개당 무게(g)"
        enum default_storage "REFRIGERATED, FROZEN, ROOM_TEMP"
        int default_shelf_days "권장 보관일수"
        boolean is_basic_seasoning "기본 양념 여부"
    }

    products {
        bigint id PK "상품 ID"
        enum product_type "INGREDIENT / MEALKIT"
        varchar name "상품명"
        text description "상품 설명"
        varchar image_url "상품 이미지"
        int price "판매가(원)"
        int stock "재고 수량"
        enum storage "보관 방식"
        int shelf_days "권장 보관일수"
        enum status "ON_SALE, SOLD_OUT, STOPPED"
    }

    ingredient_products {
        bigint id PK
        bigint product_id FK, UK "상품 ID"
        bigint ingredient_id FK "표준 재료 ID"
        decimal unit_amount "용량 수치"
        varchar unit "용량 단위"
        decimal base_quantity "단위당 갯수"
    }

    mealkit_products {
        bigint id PK
        bigint product_id FK, UK "상품 ID"
        int servings "인분"
        bigint recipe_id "동일 요리 레시피 ID"
    }
```

---

### 3.3 레시피 도메인
레시피 기본 정보와 필요 재료(N:M), 조리 단계(1:N 순서), 그리고 AI 추천 매칭을 위한 레시피 맛(1:N)을 관리합니다.

```mermaid
erDiagram
    recipes ||--o{ recipe_ingredients : "필요 재료 목록"
    recipes ||--o{ recipe_steps : "조리 단계"
    recipes ||--o{ recipe_tastes : "맛 특성"
    ingredients ||--o{ recipe_ingredients : "표준 재료 참조"

    recipes {
        bigint id PK "레시피 ID"
        varchar source "출처 (MAFRA / MFDS)"
        varchar source_id "원본 레시피 코드"
        varchar name "레시피 이름"
        text summary "간략 소개"
        varchar cuisine_type "음식 종류"
        varchar category "요리 분류"
        varchar main_ingredient_category "주재료 분류"
        varchar cook_method "조리 방법"
        int cook_time_min "조리시간(분)"
        int servings "인분"
        tinyint difficulty "난이도 (1~10)"
        decimal weight_g "1인분 중량(g)"
        decimal calories "열량(kcal)"
        decimal carbs_g "탄수화물(g)"
        decimal protein_g "단백질(g)"
        decimal fat_g "지방(g)"
        decimal sodium_mg "나트륨(mg)"
        text tip "조리 팁"
        text tip_summary "AI 후기 요약"
        varchar image_url "대표 이미지 URL"
        varchar tags "해시태그"
        text ingredients_raw "재료 원문"
    }

    recipe_ingredients {
        bigint id PK
        bigint recipe_id FK
        bigint ingredient_id FK
        int seq "순서"
        varchar display_name "표기 재료명"
        varchar amount "원문 용량 (1/2큰술 등)"
        varchar group_name "원문 그룹명 (양념장 등)"
        varchar raw_text "재료 항목 원문"
        decimal quantity "기본단위 수량"
        varchar role "주재료/부재료/양념"
    }

    recipe_steps {
        bigint id PK
        bigint recipe_id FK
        int step_no "조리 순서"
        text description "조리 설명"
        varchar image_url "단계 이미지"
        text tip "단계별 팁"
    }

    recipe_tastes {
        bigint id PK
        bigint recipe_id FK
        enum taste "SALTY, SWEET, SPICY, UMAMI, MILD"
    }
```

---

### 3.4 냉장고 도메인
회원의 현재 냉장고 보관 재료와 커머스 구매 확정에 따른 자동 입고 중복 방지 이력을 관리합니다.

```mermaid
erDiagram
    members ||--o{ fridge : "소유"
    ingredients ||--o{ fridge : "표준 재료 매핑"
    order_items |o--o| fridge : "주문 상품 연계 (커머스 입고 시)"
    members ||--o{ fridge_commerce_histories : "이력 소유"
    order_items ||--o| fridge_commerce_histories : "입고 중복 방지 (UK)"

    fridge {
        bigint id PK "냉장고 재료 ID"
        bigint member_id FK "회원 ID"
        bigint ingredient_id FK "표준 재료 ID"
        bigint order_item_id FK "주문 상품 ID (커머스 자동입고 시)"
        bigint product_id "상품 ID (재구매/밀키트 판별용)"
        varchar raw_name "재료명 원문"
        decimal quantity "수량"
        enum storage "보관 방식"
        date expiration_date "소비기한"
        enum source "MANUAL (직접등록) / COMMERCE (자동입고)"
    }

    fridge_commerce_histories {
        bigint id PK "입고 이력 ID"
        bigint member_id FK "회원 ID"
        bigint order_item_id FK, UK "구매 확정된 주문 상품 ID"
        datetime created_at "입고 시각"
    }
```

---

### 3.5 장바구니 · 주문 · 할인 행사 도메인
특가 및 번개 드롭 프로모션과 장바구니, 그리고 주문 생성 시점의 상품 스냅샷을 보존하는 주문 모델입니다.

```mermaid
erDiagram
    members ||--o| carts : "보유 (1:1)"
    carts ||--o{ cart_items : "담은 상품"
    products ||--o{ cart_items : "상품 참조"

    sale_events ||--o{ sale_event_items : "행사 대상 상품"
    products ||--o{ sale_event_items : "대상 상품"

    members ||--o{ orders : "주문자"
    orders ||--o{ order_items : "주문 품목"
    products ||--o{ order_items : "상품 참조"
    sale_event_items |o--o{ order_items : "적용된 할인 행사 (선택)"
    members |o--o{ order_items : "번개드롭 1인1개 확인 회원"

    sale_events {
        bigint id PK "행사 ID"
        enum type "SPECIAL (특가) / FLASH_DROP (번개드롭)"
        datetime starts_at "시작 시각"
        datetime ends_at "종료 시각"
        enum status "SCHEDULED, ACTIVE, ENDED"
        datetime ended_at "중간에 닫은 시각"
    }

    sale_event_items {
        bigint id PK
        bigint sale_event_id FK
        bigint product_id FK
        int list_price "등록 당시 정가"
        int sale_price "할인가"
        int discount_rate "할인율(%)"
        int limited_quantity "한정 수량"
        int remaining_quantity "남은 수량"
    }

    orders {
        bigint id PK "주문 ID"
        varchar order_number UK "주문 번호"
        bigint member_id FK "회원 ID"
        varchar idempotency_key "중복 주문 방지 키"
        enum status "PENDING, PAID, CANCELLED, CONFIRMED, REFUNDED"
        bigint total_amount "총 결제 금액"
        datetime paid_at "결제 완료 일시"
        datetime confirmed_at "구매 확정 일시"
    }

    order_items {
        bigint id PK "주문 상품 ID"
        bigint order_id FK "주문 ID"
        bigint product_id FK "상품 ID"
        varchar product_name "주문 당시 상품명"
        varchar product_image_url "주문 당시 상품 이미지"
        int quantity "수량"
        int list_price "주문 당시 정가"
        int confirmed_quantity "구매 확정 수량"
        int unit_price "실제 단가 (할인가 또는 정가)"
        enum sale_type "NONE, SPECIAL, FLASH_DROP"
        bigint sale_event_item_id FK "할인 행사 상품 ID"
        bigint flash_drop_member_id FK "번개 드롭 구매 회원 ID"
    }

    carts {
        bigint id PK "장바구니 ID"
        bigint member_id FK, UK "회원 ID"
    }

    cart_items {
        bigint id PK "장바구니 상품 ID"
        bigint cart_id FK "장바구니 ID"
        bigint product_id FK "상품 ID"
        int quantity "수량"
    }
```

---

### 3.6 결제 · 환불 · 정산 도메인
PortOne PG 연동, 등록 카드 결제(빌링키), 결제 상태 변경 원장(이력), 상품 단위 부분/전체 환불, 그리고 일별 정산 집계 모델입니다.

```mermaid
erDiagram
    orders ||--o{ payments : "결제 시도 (1:N)"
    members ||--o{ billing_keys : "등록 카드"
    members ||--o| payment_pins : "결제 비밀번호 (1:1)"
    billing_keys |o--o{ payments : "카드 간편결제"

    payments ||--o{ payment_histories : "상태 변경 원장"
    payments ||--o{ refunds : "환불 건"
    payments |o--o{ webhook_events : "수신 웹훅"

    refunds ||--|{ refund_items : "환불 품목"
    order_items ||--o{ refund_items : "환불 대상 주문 품목"

    payments {
        bigint id PK "결제 ID"
        bigint order_id FK "주문 ID"
        bigint billing_key_id FK "등록 카드 ID (선택)"
        varchar portone_payment_id UK "포트원 결제 고유 ID"
        bigint amount "결제 금액"
        enum pg_provider "KAKAOPAY, KICC, INICIS, TOSSPAYMENTS, TOSSPAY"
        enum status "READY, PAID, FAILED"
        enum fail_reason "실패 사유"
        datetime expires_at "만료 시각 (10분)"
        datetime paid_at "완료 시각"
    }

    billing_keys {
        bigint id PK "등록 카드 ID"
        bigint member_id FK "회원 ID"
        varchar billing_key_ciphertext "빌링키 암호문"
        char billing_key_hash UK "빌링키 지문"
        varchar billing_key "암호화 전 평문 (지금은 NULL)"
        varchar card_name "카드 상품명"
        char card_last4 "카드 끝 4자리"
        varchar card_number_masked "마스킹 카드 번호"
        varchar card_issuer_code "발급사 코드"
        enum card_type "CREDIT, DEBIT, GIFT"
        boolean is_default "대표 카드"
        enum status "ACTIVE, DELETED"
        datetime deleted_at "삭제 시각"
        datetime pg_deleted_at "PortOne 삭제 확인 시각"
    }

    payment_pins {
        bigint id PK "결제 비밀번호 ID"
        bigint member_id FK, UK "회원 ID"
        varchar pin_hash "비밀번호 해시"
        int failed_count "연속 실패 횟수"
        datetime locked_at "잠금 시각"
    }

    webhook_events {
        bigint id PK "웹훅 이벤트 ID"
        bigint payment_id FK "결제 ID (선택)"
        varchar webhook_id UK "포트원 웹훅 ID"
        varchar event_type "이벤트 종류"
        enum process_status "RECEIVED, PROCESSED, FAILED, IGNORED"
        text raw_payload "원문"
        varchar fail_reason "실패 사유"
        datetime processed_at "처리 시각"
    }

    payment_histories {
        bigint id PK
        bigint payment_id FK
        enum event_type "READY, PAID, FAILED, PARTIAL_CANCELLED, CANCELLED"
        bigint amount "변동 금액"
        bigint cancelled_amount "누적 취소 금액"
        bigint refund_id "연관 환불 ID"
        varchar pg_cancellation_id "PG 취소 번호"
        enum changed_by "USER, WEBHOOK, SCHEDULER"
        varchar reason "사유"
    }

    refunds {
        bigint id PK "환불 ID"
        bigint payment_id FK
        bigint refund_amount "환불 금액"
        enum reason_type "사유 구분"
        varchar reason_detail "상세 사유"
        enum status "REQUESTED, COMPLETED, FAILED"
        varchar portone_cancellation_id "포트원 취소 ID"
        datetime refunded_at "환불 완료 시각"
        varchar fail_reason "실패 사유"
    }

    refund_items {
        bigint id PK
        bigint refund_id FK
        bigint order_item_id FK
        int quantity "환불 수량"
        int unit_price "단가"
        bigint amount "환불 금액"
    }

    daily_sales {
        bigint id PK "일별 매출 ID"
        date sales_date UK "매출 날짜"
        bigint paid_amount "결제 금액 합계"
        bigint refunded_amount "환불 금액 합계"
        bigint net_amount "순매출 (자동 계산 컬럼)"
        int paid_count "결제 건수"
        int refunded_count "환불 건수"
    }
```

---

### 3.7 AI 상담 도메인
사용자와 AI 간의 메뉴 추천 상담 세션(회원당 진행 중 1건 보장)과 메시지 내역, 그리고 추천된 레시피/상품 카드 결과를 관리합니다.

```mermaid
erDiagram
    members ||--o{ chat_sessions : "상담 세션"
    chat_sessions ||--o{ chat_messages : "메시지 순서 (1:N)"
    chat_messages ||--o{ chat_recommendations : "추천 카드"
    recipes |o--o{ chat_recommendations : "추천 레시피"
    products |o--o{ chat_recommendations : "추천 상품"

    chat_sessions {
        bigint id PK "상담 세션 ID"
        bigint member_id FK "회원 ID"
        enum status "ACTIVE, COMPLETED, EXPIRED"
        datetime last_message_at "마지막 대화 일시"
        bigint active_member_id UK "진행 중 회원 ID (회원당 1개 보장 계산 컬럼)"
    }

    chat_messages {
        bigint id PK "메시지 ID"
        bigint chat_session_id FK
        int sequence "대화 순서 (1, 2, ...)"
        enum role "USER / ASSISTANT"
        text content "메시지 원문"
        boolean filtered "안전 장치 필터링 여부"
    }

    chat_recommendations {
        bigint id PK "추천 ID"
        bigint chat_message_id FK
        bigint recipe_id FK "추천 레시피 ID"
        bigint product_id FK "추천 상품 ID"
        int display_order "카드 표시 순서"
        text description "추천 사유 AI 설명"
    }
```

---

### 3.8 요리 기록 · 재료 이력 · 후기 & 알림 도메인
레시피 조리 완료 기록과 그에 따른 냉장고 재료 소진/폐기 이력, 레시피 별점/후기, 그리고 인앱 알림을 관리합니다.

```mermaid
erDiagram
    members ||--o{ cooking_logs : "요리 완료 기록"
    recipes ||--o{ cooking_logs : "조리한 레시피"
    chat_sessions |o--o{ cooking_logs : "상담 연계 (선택)"

    members ||--o{ fridge_item_logs : "재료 변동 이력"
    ingredients ||--o{ fridge_item_logs : "표준 재료"
    cooking_logs |o--o{ fridge_item_logs : "요리로 소진된 경우"

    members ||--o{ recipe_reviews : "후기 작성"
    recipes ||--o{ recipe_reviews : "대상 레시피"

    members ||--o{ notifications : "인앱 알림 수신"

    cooking_logs {
        bigint id PK "요리 기록 ID"
        bigint member_id FK
        bigint recipe_id FK
        bigint chat_session_id FK "상담 추천인 경우"
        enum result "SUCCESS / FAILED"
        varchar memo "요리 메모"
    }

    fridge_item_logs {
        bigint id PK "재료 이력 ID"
        bigint member_id FK
        bigint ingredient_id FK
        bigint cooking_log_id FK "요리로 사용된 경우"
        bigint fridge_id "원래 냉장고 재료 ID (참고용)"
        varchar raw_name "재료명 원문"
        decimal quantity "빠진 수량"
        enum action "CONSUMED (소진) / DISCARDED (폐기)"
    }

    recipe_reviews {
        bigint id PK "후기 ID"
        bigint member_id FK
        bigint recipe_id FK
        int rating "별점 (1~5)"
        varchar content "한 줄 후기 (최대 100자)"
    }

    notifications {
        bigint id PK "알림 ID"
        bigint member_id FK
        enum notification_type "EXPIRATION, FLASH_DROP, COMMERCE_STOCKED"
        varchar content "알림 문구"
        varchar link_url "이동 경로"
        varchar dedupe_key UK "중복 방지 키"
        boolean is_read "읽음 여부"
        datetime deleted_at "삭제일시"
    }
```

---

### 3.9 판매 예측 · 할인 후보 분석 & 감사 이력
PyTorch 머신러닝 주간 판매 예측 결과와 요일별 할인 후보 시뮬레이션 및 관리자 작업 감사 로그입니다.

```mermaid
erDiagram
    prediction_runs ||--o{ sales_forecasts : "상품별 주간 예측"
    prediction_runs ||..o| forecast_batch_runs : "같은 대상 주 (FK 없음)"
    members ||--o{ deal_analyses : "분석 실행 관리자"
    deal_analyses ||--o{ deal_candidates : "후보/제외 상품 목록"
    members ||--o{ admin_audit_logs : "작업자"

    prediction_runs {
        bigint id PK "예측 실행 ID"
        date target_week_start UK "대상 주 월요일"
        varchar model_version "모델 버전 (예: fc-v0.3)"
        datetime data_cutoff_at "데이터 기준 시각"
        datetime confirmed_at "실제 판매량 확정일시"
        bit synthetic_data "가상(시연) 데이터 여부"
    }

    sales_forecasts {
        bigint id PK
        bigint prediction_run_id FK
        bigint product_id "상품 ID"
        varchar product_name "예측 시점 상품명"
        int predicted_rank "예상 순위"
        int predicted_qty "예상 판매 수량"
        int baseline_qty "기준선 예측 (전주 판매량)"
        int actual_rank "실제 순위"
        int actual_qty "실제 판매 수량"
    }

    forecast_batch_runs {
        bigint id PK "배치 시도 기록 ID"
        date target_week_start UK "대상 주 월요일"
        enum status "CREATED, ALREADY_EXISTS, NO_PRODUCTS, NO_TRAINING_SAMPLES, FAILED"
        datetime attempted_at "마지막 시도 시각"
        varchar failure_type "실패 종류"
    }

    deal_analyses {
        bigint id PK "할인 후보 분석 ID"
        bigint member_id FK "분석한 관리자"
        date target_week_start "대상 주 월요일"
        enum day_of_week "적용 요일"
        int discount_rate "가상 할인율(%)"
        int min_stock "최소 재고 기준"
        varchar model_version "분석 모델 버전"
        datetime data_cutoff_at "데이터 기준 시각"
        datetime last_saved_at "검토 결과 마지막 저장"
        bit synthetic_data "가상(시연) 데이터 여부"
    }

    deal_candidates {
        bigint id PK
        bigint deal_analysis_id FK
        bigint product_id "상품 ID"
        varchar product_name "분석 시점 상품명"
        int regular_price "정상가"
        int discount_price "할인가"
        int current_stock "현재 재고"
        boolean is_excluded "제외 여부"
        enum exclude_reason "TOP_SELLER_ON_DAY, LOW_STOCK"
        int avg_sales_on_day "그 요일 평균 판매량"
        int search_clicks "최근 4주 같은 요일 검색 클릭"
        int purchases "최근 4주 같은 요일 구매"
        varchar candidate_reason "후보 이유"
        int expected_qty_regular "정상가 예상 판매량"
        int expected_qty_discount "할인 예상 판매량"
        int expected_increase "예상 증가량"
        enum review_status "UNREVIEWED, SELECTED, HELD"
        enum event_request_status "NONE, REQUESTED, APPROVED, REJECTED"
        bigint promotion_id "연계된 행사 ID"
        datetime event_requested_at "행사 등록 시각"
    }

    admin_audit_logs {
        bigint id PK "감사 로그 ID"
        bigint member_id FK "작업 회원"
        enum action "PRODUCT_CREATE, PRICE_UPDATE, DEAL_ANALYSIS, ROLE_CHANGE 등"
        varchar target_type "대상 종류"
        bigint target_id "대상 ID"
        enum result "SUCCESS, FAILED, DENIED"
        enum actor_role "작업 당시 역할 ADMIN, USER"
        json before_value "변경 전 값"
        json after_value "변경 후 값"
    }
```

---

### 3.10 통합검색 기록 도메인
통합검색 결과의 상품·레시피 클릭과 결과가 0건이었던 검색어를 남깁니다. 로그 테이블이라 상품·레시피·회원에 물리 FK를 걸지 않고 ID만 저장하므로 관계선이 없습니다.

```mermaid
erDiagram
    search_click_histories {
        bigint id PK "상품 클릭 기록 ID"
        bigint product_id "클릭한 상품 ID (FK 없음)"
        bigint member_id "클릭한 회원 ID (비로그인은 NULL, FK 없음)"
        varchar keyword "검색어"
        datetime created_at "클릭 시각"
    }

    search_recipe_click_histories {
        bigint id PK "레시피 클릭 기록 ID"
        bigint recipe_id "클릭한 레시피 ID (FK 없음)"
        bigint member_id "클릭한 회원 ID (비로그인은 NULL, FK 없음)"
        varchar keyword "검색어"
        datetime created_at "클릭 시각"
    }

    search_zero_result_histories {
        bigint id PK "0건 검색 기록 ID"
        enum target "RECIPE / PRODUCT"
        varchar keyword "검색어"
        datetime created_at "검색 시각"
    }
```
