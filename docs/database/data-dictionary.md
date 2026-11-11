# 데이터 사전 (Data Dictionary)

> 기준: 최신 전체 데이터베이스 스키마 ([schema.sql](schema.sql))  
> 전체 39개 테이블의 컬럼 정의, 물리 타입, 제약조건 및 비즈니스 규칙을 명세합니다.

---

## 📌 공통 규칙

- **기본키(PK)**: 모든 테이블은 `id BIGINT NOT NULL AUTO_INCREMENT`를 기본키로 사용합니다.
- **감사 컬럼**: 모든 테이블은 `created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` 및 `updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)`를 포함합니다 (아래 테이블별 상세 목록에서는 가독성을 위해 생략 또는 특이사항만 기재).
- **문자열 인코딩**: `utf8mb4` (다국어 및 이모지 지원), Collation: `utf8mb4_0900_ai_ci`.
- **금액 및 수량**: 금액은 원 단위 `INT` 또는 `BIGINT`(`DEFAULT 0`, `CHECK >= 0`), 수량은 `INT` 또는 소수점 수량을 지원하는 `DECIMAL(10, 2)`을 사용합니다.
- **외래키(FK)**: 도메인 격리 원칙에 따라 동일 도메인 또는 긴밀한 연관 관계는 물리 FK를 맺고, 도메인 간 경계가 명확한 참조는 물리 FK 없이 식별자(ID)만 저장하여 결합도를 낮춥니다.

---

## 1. 회원 · 인증 도메인

### `members` — 회원 계정
회원의 기본 로그인 계정 정보, 역할 및 상태를 관리합니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `email` | `VARCHAR(255)` | N | | `UK` | 로그인 이메일 (계정 식별자) |
| `password_hash` | `VARCHAR(255)` | N | | | 단방향 암호화 해시 (BCrypt) |
| `nickname` | `VARCHAR(30)` | N | | `UK` | 서비스 내 활동명 (후기 및 화면 노출) |
| `role` | `ENUM('USER','ADMIN')` | N | `'USER'` | | 회원 권한 (일반회원: USER, 관리자: ADMIN) |
| `status` | `ENUM('ACTIVE','SUSPENDED')` | N | `'ACTIVE'` | | 계정 상태 (정상: ACTIVE, 활동 정지: SUSPENDED) |

### `member_tastes` — 회원 맛 선호
회원이 선택한 개인 맞춤 맛 선호 목록입니다. (0개 이상 다중 선택 가능)

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `member_id` | `BIGINT` | N | | `FK (members.id)` | 맛 선호를 등록한 회원 |
| `taste` | `ENUM('SALTY','SWEET','SPICY','UMAMI','MILD')` | N | | `UK(member_id, taste)` | 짠맛, 단맛, 매운맛, 감칠맛, 슴슴한맛 |

### `refresh_sessions` — 로그인 세션
JWT Refresh 토큰의 발급 및 유효성을 관리하는 세션 테이블입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `member_id` | `BIGINT` | N | | `FK (members.id)` | 로그인한 회원 |
| `token_hash` | `VARCHAR(255)` | N | | `UK` | Refresh 토큰의 단방향 해시 |
| `keep_logged_in` | `BOOLEAN` | N | `FALSE` | | 로그인 상태 유지 체크 여부 |
| `device_info` | `VARCHAR(255)` | Y | `NULL` | | 접속 기기/브라우저 식별 정보 |
| `last_used_at` | `DATETIME(6)` | N | `NOW(6)` | | 마지막 활동 시각 (30분 미사용 시 세션 만료) |
| `expires_at` | `DATETIME(6)` | N | | | 세션 절대 만료 일시 |
| `revoked_at` | `DATETIME(6)` | Y | `NULL` | | 로그아웃 또는 강제 폐기된 일시 |

---

## 2. 표준 재료 & 상품 도메인

### `ingredients` — 표준 재료 사전
제각각인 상품명과 레시피 재료를 통합 비교하기 위한 표준 재료 사전입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `ingredient_code` | `VARCHAR(50)` | N | | `UK` | 표준 코드 (예: `ING_RCP_0003`, `ING_CUSTOM`, `ING_MEALKIT`) |
| `name` | `VARCHAR(100)` | N | | `UK` | 표준 대표 명칭 (예: '대파', '마늘') |
| `base_unit` | `VARCHAR(20)` | N | | | 기본 단위 (개, 대, 모, 장, g, ml 등) |
| `unit_weight_g` | `DECIMAL(8, 2)` | Y | `NULL` | | 기본 단위 1개당 무게(g) (g 수량을 기본 단위로 환산 시 활용) |
| `default_storage` | `ENUM('REFRIGERATED','FROZEN','ROOM_TEMP')` | N | | | 권장 보관 방식 (냉장 / 냉동 / 실온) |
| `default_shelf_days` | `INT` | N | `0` | | 권장 보관 일수 (등록일 + 일수 = 소비기한 자동 계산) |
| `is_basic_seasoning` | `BOOLEAN` | N | `FALSE` | | 기본 양념 여부 (소금, 설탕 등은 요리 자동 차감에서 제외) |

### `products` — 판매 상품 공통 정보
쇼핑몰에서 판매 중인 재료 상품 및 밀키트의 공통 카탈로그 정보입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `product_type` | `ENUM('INGREDIENT','MEALKIT')` | N | | | 상품 구분 (단일 재료 상품 / 밀키트) |
| `name` | `VARCHAR(200)` | N | | | 판매 상품명 (예: "국산 무농약 깐마늘 200g") |
| `description` | `TEXT` | Y | `NULL` | | 상품 상세 설명 |
| `image_url` | `VARCHAR(500)` | Y | `NULL` | | 대표 이미지 URL |
| `price` | `INT` | N | `0` | `CHECK(>= 0)` | 기본 판매 정가(원) |
| `stock` | `INT` | N | `0` | `CHECK(>= 0)` | 현재 판매 가능 재고 수량 |
| `storage` | `ENUM('REFRIGERATED','FROZEN','ROOM_TEMP')` | N | | | 보관 방식 (냉장 / 냉동 / 실온) |
| `shelf_days` | `INT` | N | `0` | | 권장 보관 일수 (구매 확정일 + 일수 = 냉장고 입고 소비기한) |
| `status` | `ENUM('ON_SALE','SOLD_OUT','STOPPED')` | N | `'ON_SALE'` | `INDEX` | 판매 상태 (판매중 / 품절 / 판매중지) |

### `ingredient_products` — 재료 상품 상세
일반 식재료 상품이 표준 재료 사전에 어떻게 매핑되는지 정의합니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `product_id` | `BIGINT` | N | | `FK (products.id), UK` | 상품 ID (1:1 매핑) |
| `ingredient_id` | `BIGINT` | N | | `FK (ingredients.id), INDEX` | 연결된 표준 재료 ID |
| `unit_amount` | `DECIMAL(8, 2)` | N | | `CHECK(> 0)` | 상품 포장 용량 수치 (예: 200.00) |
| `unit` | `VARCHAR(20)` | N | | | 용량 단위 (g, ml, 개, 팩 등) |
| `base_quantity` | `DECIMAL(10, 2)` | N | | | 기본 단위 환산 갯수 (예: 마늘 200g ➔ 10개 상당) |

### `mealkit_products` — 밀키트 상품 상세
밀키트 상품의 인분 수 및 연결된 원본 레시피를 정의합니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `product_id` | `BIGINT` | N | | `FK (products.id), UK` | 상품 ID (1:1 매핑) |
| `servings` | `INT` | N | `1` | `CHECK(> 0)` | 밀키트 구성 인분 수 |
| `recipe_id` | `BIGINT` | Y | `NULL` | `INDEX` | 연결된 원본 레시피 ID (논리 참조) |

---

## 3. 레시피 도메인

### `recipes` — 레시피 기본 정보
공공 공공데이터(농식품, 식약처) 및 자체 등록 레시피 정보입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `source` | `VARCHAR(20)` | N | | `UK(source, source_id)` | 레시피 출처 (`MAFRA`, `MFDS` 등) |
| `source_id` | `VARCHAR(20)` | N | | `UK(source, source_id)` | 원본 레시피 고유 코드 |
| `name` | `VARCHAR(200)` | N | | `INDEX` | 레시피 요리명 (예: "마늘종 볶음") |
| `summary` | `TEXT` | Y | `NULL` | | 레시피 간략 소개 |
| `cuisine_type` | `VARCHAR(20)` | Y | `NULL` | | 요리 종류 (한식 / 양식 / 중식 등) |
| `category` | `VARCHAR(50)` | Y | `NULL` | `INDEX` | 요리 분류 (밥 / 국 / 반찬 / 일품 등) |
| `cook_method` | `VARCHAR(20)` | Y | `NULL` | | 조리 방법 (끓이기, 굽기, 볶기 등) |
| `cook_time_min` | `INT` | Y | `NULL` | | 예상 조리 시간 (분 단위) |
| `servings` | `INT` | Y | `NULL` | | 기준 분량 (인분) |
| `difficulty` | `TINYINT` | N | | `CHECK(1~10)` | 조리 난이도 (1: 가장 쉬움 ~ 10: 전문가) |
| `calories` | `DECIMAL(8, 2)` | Y | `NULL` | | 열량 (kcal) |
| `tip` | `TEXT` | Y | `NULL` | | 나트륨 저감 등 조리 팁 |
| `tip_summary` | `TEXT` | Y | `NULL` | | 사용자 후기 기반 AI 요약 팁 |
| `image_url` | `VARCHAR(500)` | Y | `NULL` | | 대표 완성 요리 이미지 URL |

### `recipe_ingredients` — 레시피 재료 목록 (N:M)
레시피에 들어가는 재료와 표준 재료 사전을 매핑합니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `recipe_id` | `BIGINT` | N | | `FK (recipes.id) ON DELETE CASCADE` | 레시피 ID |
| `ingredient_id` | `BIGINT` | N | | `FK (ingredients.id), INDEX` | 표준 재료 ID |
| `seq` | `INT` | N | | `UK(recipe_id, seq)` | 재료 표기 순서 |
| `display_name` | `VARCHAR(100)` | Y | `NULL` | | 레시피 원문에 표기된 재료명 (예: "다진 마늘") |
| `amount` | `VARCHAR(100)` | Y | `NULL` | | 원문 용량 표기 (예: "1/2큰술", "약간") |
| `quantity` | `DECIMAL(10, 2)` | Y | `NULL` | | 기본 단위 기준 수량 |
| `role` | `VARCHAR(20)` | Y | `NULL` | | 재료 역할 (주재료 / 부재료 / 양념) |

### `recipe_steps` — 레시피 조리 단계
레시피의 순차적인 조리 과정과 단계별 안내입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `recipe_id` | `BIGINT` | N | | `FK (recipes.id) ON DELETE CASCADE` | 레시피 ID |
| `step_no` | `INT` | N | | `UK(recipe_id, step_no)` | 조리 순서 번호 (1부터 시작) |
| `description` | `TEXT` | N | | | 조리 설명 원문 |
| `image_url` | `VARCHAR(500)` | Y | `NULL` | | 해당 단계 조리 사진 URL |
| `tip` | `TEXT` | Y | `NULL` | | 단계별 주의점 및 팁 |

### `recipe_tastes` — 레시피 맛
AI 맞춤 추천을 위해 레시피가 가진 맛 특성을 정의합니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `recipe_id` | `BIGINT` | N | | `FK (recipes.id) ON DELETE CASCADE` | 레시피 ID |
| `taste` | `ENUM('SALTY','SWEET','SPICY','UMAMI','MILD')` | N | | `UK(recipe_id, taste)` | 레시피가 지닌 맛 속성 |

---

## 4. 냉장고 도메인

### `fridge` — 냉장고 보관 재료
회원이 현재 보관 중인 식재료 실물 목록입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `member_id` | `BIGINT` | N | | `FK (members.id)` | 냉장고 주인 회원 ID |
| `ingredient_id` | `BIGINT` | N | | `FK (ingredients.id)` | 매핑된 표준 재료 ID |
| `order_item_id` | `BIGINT` | Y | `NULL` | `FK (order_items.id)` | 커머스 자동 입고된 경우 주문 상품 ID |
| `product_id` | `BIGINT` | Y | `NULL` | | 입고된 상품 ID (밀키트 판별 및 재구매 링크용) |
| `raw_name` | `VARCHAR(200)` | N | | | 입력 재료명 (직접 입력 또는 상품명) |
| `quantity` | `DECIMAL(10, 2)` | N | `0` | | 현재 잔여 수량 (단위: `ingredients.base_unit`) |
| `storage` | `ENUM('REFRIGERATED','FROZEN','ROOM_TEMP')` | N | | | 실제 보관 방식 (냉장 / 냉동 / 실온) |
| `expiration_date` | `DATE` | N | | `INDEX(expiration_date, member_id)` | 소비기한 |
| `source` | `ENUM('MANUAL','COMMERCE')` | N | `'MANUAL'` | | 등록 경로 (직접 등록 / 커머스 구매 확정 자동 입고) |

### `fridge_commerce_histories` — 커머스 자동 입고 이력
구매 확정된 주문 상품이 냉장고에 중복으로 입고되는 것을 방지하는 이력 원장입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `member_id` | `BIGINT` | N | | `FK (members.id)` | 회원 ID |
| `order_item_id` | `BIGINT` | N | | `FK (order_items.id), UK` | 입고 완료된 주문 상품 ID (중복 입고 차단) |

---

## 5. 장바구니 · 주문 · 할인 행사 도메인

### `carts` — 장바구니
회원당 1개씩 보유하는 장바구니 엔티티입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `member_id` | `BIGINT` | N | | `FK (members.id), UK` | 장바구니 소유 회원 ID |

### `cart_items` — 장바구니 상품
장바구니에 담긴 상품 품목입니다. 가격과 재고는 실시간 조회를 원칙으로 합니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `cart_id` | `BIGINT` | N | | `FK (carts.id)` | 소속 장바구니 ID |
| `product_id` | `BIGINT` | N | | `FK (products.id), UK(cart_id, product_id)` | 담은 상품 ID |
| `quantity` | `INT` | N | `1` | | 담은 수량 (1 이상) |

### `sale_events` — 할인 행사
특가 프로모션 및 타임 한정 번개 드롭 이벤트 정보입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `type` | `ENUM('SPECIAL','FLASH_DROP')` | N | | | 행사 종류 (SPECIAL: 기간 특가 / FLASH_DROP: 번개 드롭) |
| `starts_at` | `DATETIME(6)` | N | | `INDEX` | 행사 오픈 일시 |
| `ends_at` | `DATETIME(6)` | N | | `INDEX` | 행사 마감 일시 |
| `status` | `ENUM('SCHEDULED','ACTIVE','ENDED')` | N | `'SCHEDULED'` | `INDEX` | 행사 진행 상태 (예정 / 진행 중 / 종료) |

### `sale_event_items` — 할인 행사 상품
행사에 포함된 대상 상품 및 특별 할인가 정보입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `sale_event_id` | `BIGINT` | N | | `FK (sale_events.id)` | 소속 행사 ID |
| `product_id` | `BIGINT` | N | | `FK (products.id), INDEX` | 대상 상품 ID |
| `list_price` | `INT` | N | `0` | | 행사 등록 당시 기준 정상가(원) |
| `sale_price` | `INT` | N | `0` | | 실제 행사 판매가(원) |
| `discount_rate` | `INT` | N | `0` | | 정가 대비 할인율(%) |
| `limited_quantity` | `INT` | Y | `NULL` | | 번개 드롭 한정 총 수량 (특가는 NULL) |
| `remaining_quantity` | `INT` | Y | `NULL` | | 번개 드롭 현재 잔여 수량 |

### `orders` — 주문
사용자가 작성한 주문 정보 및 결제·구매 확정 상태입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `order_number` | `VARCHAR(20)` | N | | `UK` | 주문 번호 (예: `20261013-000123`) |
| `member_id` | `BIGINT` | N | | `FK (members.id), INDEX` | 주문한 회원 ID |
| `idempotency_key` | `VARCHAR(64)` | Y | `NULL` | `UK(member_id, idempotency_key)` | 클라이언트 발급 중복 주문 방지 키 |
| `status` | `ENUM('PENDING','PAID','CANCELLED','CONFIRMED')` | N | `'PENDING'` | `INDEX` | 주문 상태 (대기 / 결제완료 / 취소 / 구매확정) |
| `total_amount` | `BIGINT` | N | `0` | | 최종 결제 확정 금액(원) |
| `paid_at` | `DATETIME(6)` | Y | `NULL` | `INDEX` | 결제 완료 일시 (7일 후 자동 구매 확정 기준) |
| `confirmed_at` | `DATETIME(6)` | Y | `NULL` | | 구매 확정 일시 |

### `order_items` — 주문 상품 스냅샷
주문 당시의 상품명, 이미지, 정가 및 실제 결제 단가를 불변 스냅샷으로 보존합니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `order_id` | `BIGINT` | N | | `FK (orders.id)` | 소속 주문 ID |
| `product_id` | `BIGINT` | N | | `FK (products.id)` | 상품 ID |
| `product_name` | `VARCHAR(200)` | N | | | 주문 당시 상품명 스냅샷 |
| `product_image_url` | `VARCHAR(500)` | Y | `NULL` | | 주문 당시 상품 이미지 스냅샷 |
| `quantity` | `INT` | N | `1` | | 주문 수량 |
| `list_price` | `INT` | N | `0` | | 주문 당시 정상 정가 |
| `unit_price` | `INT` | N | `0` | | 실제 구매 단가 (할인가 적용) |
| `sale_type` | `ENUM('NONE','SPECIAL','FLASH_DROP')` | N | `'NONE'` | | 적용된 행사 구분 (번개 드롭은 환불 제한) |
| `sale_event_item_id` | `BIGINT` | Y | `NULL` | `FK (sale_event_items.id)` | 연계된 행사 상품 ID |
| `flash_drop_member_id` | `BIGINT` | Y | `NULL` | `FK (members.id), UK` | 번개 드롭 1인 1개 구매 제한 검증용 회원 ID |

---

## 6. 결제 · 환불 · 정산 도메인

### `billing_keys` — 등록 카드 (빌링키)
간편 결제를 위해 발급받은 PortOne 빌링키 및 카드 정보입니다.
빌링키는 평문으로 두지 않고 암호문(`billing_key_ciphertext`)과 지문(`billing_key_hash`)으로만 저장합니다. 암호화 키와 지문 키는 DB 밖(배포 서버는 Parameter Store)에 둡니다.
암호화 전 DB에 남은 평문 `billing_key` 컬럼은 서버 기동 때 `LegacyBillingKeyEncryptor`가 NULL 허용으로 바꾸고 옛 UK를 지운 뒤 값을 옮기고 비웁니다. 컬럼은 이후 별도 배포에서 지우며, 새로 만드는 DB에는 없습니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `member_id` | `BIGINT` | N | | `FK (members.id), INDEX` | 카드 소유 회원 ID |
| `billing_key_ciphertext` | `VARCHAR(512)` | N | | | PortOne 빌링키의 AES-256-GCM 암호문 (`v1:{키 ID}:{Base64(IV+암호문+태그)}`). 회원 ID를 추가 인증 데이터로 묶어 다른 회원 행에서는 풀리지 않음. 결제·PG 삭제 직전에만 복호화 (외부 비공개) |
| `billing_key_hash` | `CHAR(64)` | N | | `UK` | PortOne 빌링키의 HMAC-SHA256 지문 (16진수 64자). 같은 빌링키 중복 저장 방지·등록 확정 중복 조회. 되돌릴 수 없음 |
| `card_name` | `VARCHAR(100)` | Y | `NULL` | | 카드 상품명 (예: 신한카드) |
| `card_last4` | `CHAR(4)` | Y | `NULL` | | 카드 번호 끝 4자리 (화면 표시용) |
| `card_number_masked` | `VARCHAR(30)` | Y | `NULL` | | 마스킹된 카드 번호 (PortOne `card.number`). 같은 카드 중복 등록 확인용, 외부 비공개 |
| `card_issuer_code` | `VARCHAR(30)` | Y | `NULL` | | 카드 발급사 코드 (PortOne `card.issuer`). 마스킹 번호와 함께 같은 카드 확인 |
| `card_type` | `ENUM('CREDIT','DEBIT','GIFT')` | Y | `NULL` | | 카드 종류 (신용 / 체크 / 기프트) |
| `is_default` | `BOOLEAN` | N | `FALSE` | | 대표 카드 여부 |
| `status` | `ENUM('ACTIVE','DELETED')` | N | `'ACTIVE'` | `INDEX` | 카드 사용 상태 (사용중 / 삭제됨) |
| `deleted_at` | `DATETIME(6)` | Y | `NULL` | | 회원 삭제 일시 |
| `pg_deleted_at` | `DATETIME(6)` | Y | `NULL` | `INDEX` | PG사 빌링키 폐기 확인 일시 |

### `payment_pins` — 결제 비밀번호
등록 카드로 결제 시 입력하는 6자리 비밀번호 해시입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `member_id` | `BIGINT` | N | | `FK (members.id), UK` | 회원 ID (1:1 매핑) |
| `pin_hash` | `VARCHAR(100)` | N | | | 비밀번호 단방향 해시 (BCrypt) |
| `failed_count` | `INT` | N | `0` | | 연속 오입력 횟수 (성공 시 0 리셋) |
| `locked_at` | `DATETIME(6)` | Y | `NULL` | | 5회 실패로 계정 잠금된 일시 |

### `payments` — 결제
주문 건에 대한 결제 시도 및 승인 정보입니다. (재결제 시도로 1 주문당 N 결제 가능)

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `order_id` | `BIGINT` | N | | `FK (orders.id), INDEX` | 대상 주문 ID |
| `billing_key_id` | `BIGINT` | Y | `NULL` | `FK (billing_keys.id)` | 사용된 등록 카드 ID (일반 결제 시 NULL) |
| `portone_payment_id` | `VARCHAR(100)` | N | | `UK` | 서버 발급 포트원 결제 식별자 (`payment-` + UUID) |
| `amount` | `BIGINT` | N | `0` | | 결제 요청/승인 금액(원) |
| `pg_provider` | `ENUM('KAKAOPAY','KICC','INICIS','TOSSPAYMENTS','TOSSPAY')` | N | | | PG사 구분 |
| `status` | `ENUM('READY','PAID','FAILED')` | N | `'READY'` | `INDEX` | 결제 상태 (대기 / 완료 / 실패) |
| `fail_reason` | `ENUM('PG_FAILED','USER_CANCELLED','AMOUNT_MISMATCH','TIMEOUT','ORDER_NOT_PAYABLE','SUPERSEDED')` | Y | `NULL` | | 결제 실패 사유 |
| `expires_at` | `DATETIME(6)` | N | | `INDEX` | 결제 대기 만료 일시 (생성 시각 + 10분) |
| `paid_at` | `DATETIME(6)` | Y | `NULL` | `INDEX` | PG 결제 승인 완료 일시 |

### `payment_histories` — 결제 이력 원장
결제 및 취소/환불로 인해 발생하는 모든 금융 사건을 기록하는 감사 원장입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `payment_id` | `BIGINT` | N | | `FK (payments.id), INDEX` | 소속 결제 ID |
| `event_type` | `ENUM('READY','PAID','FAILED','PARTIAL_CANCELLED','CANCELLED')` | N | | | 발생 사건 종류 |
| `amount` | `BIGINT` | Y | `NULL` | | 이번 사건의 결제 또는 취소 금액 |
| `cancelled_amount` | `BIGINT` | N | `0` | | 해당 시점 기준 누적 취소 금액 |
| `refund_id` | `BIGINT` | Y | `NULL` | `INDEX` | 환불로 인한 취소인 경우 환불 ID |
| `pg_cancellation_id` | `VARCHAR(200)` | Y | `NULL` | | PG사 취소 고유 번호 |
| `changed_by` | `ENUM('USER','WEBHOOK','SCHEDULER')` | N | | | 상태 변경 주체 |
| `reason` | `VARCHAR(255)` | Y | `NULL` | | 변경/취소 사유 |

### `refunds` — 환불
주문 취소 및 반품에 따른 환불 요청 단위입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `payment_id` | `BIGINT` | N | | `FK (payments.id)` | 대상 결제 ID |
| `refund_amount` | `BIGINT` | N | `0` | | 이번 요청 환불 금액 합계 |
| `reason_type` | `ENUM('CHANGE_OF_MIND','ORDER_MISTAKE','NO_LONGER_NEEDED','ETC')` | N | | | 환불 사유 구분 |
| `reason_detail` | `VARCHAR(500)` | Y | `NULL` | | 상세 환불 사유 |
| `status` | `ENUM('REQUESTED','COMPLETED','FAILED')` | N | `'REQUESTED'` | | 환불 진행 상태 |
| `portone_cancellation_id` | `VARCHAR(200)` | Y | `NULL` | | PortOne 취소 식별자 |
| `refunded_at` | `DATETIME(6)` | Y | `NULL` | `INDEX` | 환불 완료 일시 |

### `refund_items` — 환불 상품 품목
주문 상품 단위 부분 환불을 지원하기 위한 세부 환불 항목입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `refund_id` | `BIGINT` | N | | `FK (refunds.id)` | 소속 환불 ID |
| `order_item_id` | `BIGINT` | N | | `FK (order_items.id), INDEX` | 환불 대상 주문 상품 ID |
| `quantity` | `INT` | N | `0` | | 환불 수량 |
| `unit_price` | `INT` | N | `0` | | 주문 당시 단가 스냅샷 |
| `amount` | `BIGINT` | N | `0` | | 환불 금액 (`unit_price * quantity`) |

### `webhook_events` — 포트원 웹훅 수신 이력
외부 PG 웹훅의 중복 수신 방지 및 감사 추적을 위한 원문 수신 로그입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `payment_id` | `BIGINT` | Y | `NULL` | `FK (payments.id), INDEX` | 매핑된 결제 ID (매칭 실패 시 NULL) |
| `webhook_id` | `VARCHAR(200)` | N | | `UK` | PortOne 웹훅 고유 식별자 (중복 수신 차단) |
| `event_type` | `VARCHAR(50)` | N | | | 웹훅 이벤트 타입 (예: `Transaction.Paid`) |
| `process_status` | `ENUM('RECEIVED','PROCESSED','FAILED','IGNORED')` | N | `'RECEIVED'` | `INDEX` | 처리 상태 |
| `raw_payload` | `TEXT` | N | | | 웹훅 원문 페이로드 (서명 검증용 원본 보존) |
| `processed_at` | `DATETIME(6)` | Y | `NULL` | | 처리 완료 시각 |

### `daily_sales` — 일별 매출 정산
자정에 집계되는 일별 매출 통계 원장입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `sales_date` | `DATE` | N | | `UK` | 매출 기준 일자 (한국 시각) |
| `paid_amount` | `BIGINT` | N | `0` | | 결제 완료 금액 총합 |
| `refunded_amount` | `BIGINT` | N | `0` | | 환불 완료 금액 총합 |
| `net_amount` | `BIGINT` | N | | `GENERATED STORED` | 순매출 (`paid_amount - refunded_amount`, 자동 계산) |
| `paid_count` | `INT` | N | `0` | | 결제 완료 건수 |
| `refunded_count` | `INT` | N | `0` | | 환불 발생 건수 |

---

## 7. AI 상담 도메인

### `chat_sessions` — AI 상담 세션
회원의 대화형 메뉴 추천 상담 세션입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `member_id` | `BIGINT` | N | | `FK (members.id), INDEX` | 상담을 진행 중인 회원 ID |
| `status` | `ENUM('ACTIVE','COMPLETED','EXPIRED')` | N | `'ACTIVE'` | `INDEX` | 세션 상태 (진행 중 / 완료 / 만료) |
| `last_message_at` | `DATETIME(6)` | N | `NOW(6)` | `INDEX` | 마지막 메시지 교환 일시 |
| `active_member_id` | `BIGINT` | Y | | `GENERATED STORED, UK` | `status = 'ACTIVE'`일 때만 `member_id`를 갖는 계산 컬럼 (회원당 활성 세션 1개 보장) |

### `chat_messages` — AI 대화 메시지
상담 세션 내에서 사용자와 AI 어시스턴트가 주고받은 텍스트 내역입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `chat_session_id` | `BIGINT` | N | | `FK (chat_sessions.id)` | 소속 세션 ID |
| `sequence` | `INT` | N | `0` | `UK(chat_session_id, sequence)` | 세션 내 메시지 순번 (1부터 증가) |
| `role` | `ENUM('USER','ASSISTANT')` | N | | | 발화 주체 (USER: 사용자 / ASSISTANT: AI) |
| `content` | `TEXT` | N | | | 메시지 내용 원문 |
| `filtered` | `BOOLEAN` | N | `FALSE` | | 유해성/안전 가드레일에 의해 필터링된 메시지 여부 |

### `chat_recommendations` — AI 상담 추천 결과
AI 답변 메시지 아래에 첨부되는 추천 레시피 및 상품 카드입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `chat_message_id` | `BIGINT` | N | | `FK (chat_messages.id)` | 추천이 포함된 메시지 ID |
| `recipe_id` | `BIGINT` | Y | `NULL` | `FK (recipes.id)` | 추천 레시피 ID (레시피 추천 시) |
| `product_id` | `BIGINT` | Y | `NULL` | `FK (products.id)` | 추천 상품 ID (부족 재료/밀키트 추천 시) |
| `display_order` | `INT` | N | `0` | `UK(chat_message_id, display_order)` | 카드 노출 순서 |
| `description` | `TEXT` | Y | `NULL` | | 추천 사유에 대한 AI 부가 설명 |

---

## 8. 요리 · 후기 & 알림 도메인

### `cooking_logs` — 요리 완료 기록
회원이 레시피를 보고 요리를 완료했을 때 생성되는 기록입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `member_id` | `BIGINT` | N | | `FK (members.id), INDEX` | 요리한 회원 ID |
| `recipe_id` | `BIGINT` | N | | `FK (recipes.id), INDEX` | 조리한 레시피 ID |
| `chat_session_id` | `BIGINT` | Y | `NULL` | `FK (chat_sessions.id)` | AI 상담 추천을 통해 요리한 경우 세션 ID |
| `result` | `ENUM('SUCCESS','FAILED')` | N | | | 요리 성공/실패 여부 |
| `memo` | `VARCHAR(50)` | Y | `NULL` | | 개인 조리 메모 (작성자 본인만 열람) |

### `fridge_item_logs` — 재료 소진/폐기 이력
요리 완성 또는 수동 관리를 통해 냉장고에서 재료가 차감/삭제된 이력입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `member_id` | `BIGINT` | N | | `FK (members.id), INDEX` | 회원 ID |
| `ingredient_id` | `BIGINT` | N | | `FK (ingredients.id)` | 표준 재료 ID |
| `cooking_log_id` | `BIGINT` | Y | `NULL` | `FK (cooking_logs.id)` | 요리로 인해 소진된 경우 요리 기록 ID |
| `fridge_id` | `BIGINT` | N | | | 원본 냉장고 재료 행 ID (참고용 식별자) |
| `raw_name` | `VARCHAR(200)` | N | | | 재료명 스냅샷 |
| `quantity` | `DECIMAL(10, 2)` | N | `0` | | 차감된 수량 |
| `action` | `ENUM('CONSUMED','DISCARDED')` | N | | | 처리 구분 (소진: CONSUMED / 폐기: DISCARDED) |

### `recipe_reviews` — 레시피 후기
회원이 레시피에 남긴 별점과 한 줄 후기입니다. (회원당 레시피 1건 작성 및 수정 가능)

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `member_id` | `BIGINT` | N | | `FK (members.id)` | 작성 회원 ID |
| `recipe_id` | `BIGINT` | N | | `FK (recipes.id), INDEX` | 대상 레시피 ID |
| `rating` | `INT` | N | `0` | `CHECK(1~5)` | 별점 평점 (1점 ~ 5점) |
| `content` | `VARCHAR(100)` | N | | | 한 줄 후기 본문 (최대 100자) |

### `notifications` — 인앱 알림
소비기한 임박, 번개 드롭 시작, 구매 상품 입고 알림입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `member_id` | `BIGINT` | N | | `FK (members.id), INDEX` | 알림을 수신하는 회원 ID |
| `notification_type` | `ENUM('EXPIRATION','FLASH_DROP','COMMERCE_STOCKED')` | N | | `INDEX` | 알림 종류 (소비기한 임박 / 번개드롭 / 입고) |
| `content` | `VARCHAR(200)` | N | | | 알림 노출 문구 |
| `link_url` | `VARCHAR(500)` | N | | | 클릭 시 이동할 화면 URL |
| `dedupe_key` | `VARCHAR(150)` | N | | `UK(member_id, dedupe_key)` | 중복 발송 방지 고유 키 |
| `is_read` | `BOOLEAN` | N | `FALSE` | `INDEX` | 읽음 확인 여부 |

---

## 9. 판매 예측 · 할인 후보 분석 & 감사 도메인

### `prediction_runs` — 주간 판매 예측 실행
PyTorch 딥러닝 모델의 주간 판매량 예측 배치 실행 단위입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `target_week_start` | `DATE` | N | | `UK` | 예측 대상 주의 월요일 날짜 |
| `model_version` | `VARCHAR(50)` | N | | | 사용된 머신러닝 모델 버전 (예: `fc-v0.3`) |
| `data_cutoff_at` | `DATETIME(6)` | N | | | 모델 입력에 사용된 데이터 기준 시각 |
| `confirmed_at` | `DATETIME(6)` | Y | `NULL` | | 실제 판매량이 집계되어 확정된 일시 (다음 주 월요일 06:00) |

### `sales_forecasts` — 상품별 판매 예측 및 실제 판매량
각 상품별 예상 판매 순위/수량과 사후 집계된 실제 판매량 비교 결과입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `prediction_run_id` | `BIGINT` | N | | `FK (prediction_runs.id)` | 소속 예측 실행 ID |
| `product_id` | `BIGINT` | N | | `UK(prediction_run_id, product_id)` | 대상 상품 ID |
| `product_name` | `VARCHAR(200)` | N | | | 예측 시점의 상품명 스냅샷 |
| `predicted_rank` | `INT` | Y | `NULL` | | 예상 판매 순위 |
| `predicted_qty` | `INT` | Y | `NULL` | | 예상 판매 수량 |
| `baseline_qty` | `INT` | N | `0` | | 기준선 예측 수량 (전주 동일 요일 판매량) |
| `actual_rank` | `INT` | Y | `NULL` | | 실제 달성 판매 순위 (실적 확정 시 입력) |
| `actual_qty` | `INT` | Y | `NULL` | | 실제 판매량 (환불 제외 순판매량) |

### `deal_analyses` — 요일별 할인 후보 분석
관리자가 실행한 가격 탄력성 기반 할인 후보 시뮬레이션 세션입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `member_id` | `BIGINT` | N | | `FK (members.id)` | 분석을 실행한 관리자 회원 ID |
| `target_week_start` | `DATE` | N | | | 분석 대상 주의 월요일 |
| `day_of_week` | `ENUM('MON','TUE','WED','THU','FRI','SAT','SUN')` | N | | | 할인 적용 대상 요일 |
| `discount_rate` | `INT` | N | `0` | | 시뮬레이션 가상 할인율(%, 10~40) |
| `min_stock` | `INT` | N | `50` | | 최소 재고 보유 기준 수량 |
| `model_version` | `VARCHAR(50)` | N | | | 분석 알고리즘 버전 |
| `data_cutoff_at` | `DATETIME(6)` | N | | | 분석 입력 데이터 기준 시각 |
| `last_saved_at` | `DATETIME(6)` | Y | `NULL` | | 관리자 검토 결과 최종 저장 일시 |

### `deal_candidates` — 할인 후보 상품 및 검토 결과
분석 모델이 도출한 할인 추천 상품 및 제외된 상품 목록입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `deal_analysis_id` | `BIGINT` | N | | `FK (deal_analyses.id)` | 소속 분석 세션 ID |
| `product_id` | `BIGINT` | N | | `UK(deal_analysis_id, product_id)` | 대상 상품 ID |
| `product_name` | `VARCHAR(200)` | N | | | 분석 당시 상품명 스냅샷 |
| `regular_price` | `INT` | N | `0` | | 분석 당시 정상 판매가(원) |
| `discount_price` | `INT` | N | `0` | | 가상 할인가(원) |
| `current_stock` | `INT` | N | `0` | | 분석 당시 재고 수량 |
| `avg_sales_on_day` | `INT` | N | `0` | | 평소 해당 요일 평균 판매량 |
| `search_clicks` | `INT` | N | `0` | | 최근 4주간 동일 요일 검색 클릭 수 |
| `purchases` | `INT` | N | `0` | | 최근 4주간 동일 요일 실제 구매 수 |
| `is_excluded` | `BOOLEAN` | N | `FALSE` | | 후보 제외 여부 (TRUE: 제외 상품) |
| `exclude_reason` | `ENUM('TOP_SELLER_ON_DAY','LOW_STOCK')` | Y | `NULL` | | 제외 사유 (평소 상위 판매 / 재고 부족) |
| `candidate_reason` | `VARCHAR(255)` | Y | `NULL` | | 추천 사유 (예: "검색량 대비 구매율 저조") |
| `expected_increase` | `INT` | Y | `NULL` | | 할인 적용 시 예상 판매 증가량 |
| `review_status` | `ENUM('UNREVIEWED','SELECTED','HELD')` | Y | `NULL` | | 관리자 검토 상태 (미검토 / 선택 / 보류) |
| `event_request_status` | `ENUM('NONE','REQUESTED','APPROVED','REJECTED')` | N | `'NONE'` | | 할인 프로모션 등록 요청 상태 |
| `promotion_id` | `BIGINT` | Y | `NULL` | | 등록 완료된 행사 ID (`sale_events.id`) |

### `admin_audit_logs` — 관리자 작업 감사 이력
관리자의 상품 등록/수정/삭제, 가격 변경, 할인 행사 연동, 권한 변경 등 주요 작업의 변경 전후 감사 기록입니다.

| 컬럼 | 물리 타입 | NULL | 기본값 | 제약 | 비즈니스 의미 및 설명 |
|---|---|---|---|---|---|
| `member_id` | `BIGINT` | N | | `FK (members.id)` | 작업을 수행한 회원 ID |
| `action` | `ENUM('PRODUCT_CREATE','PRODUCT_DELETE','PRICE_UPDATE','DEAL_REVIEW','EVENT_REQUEST','ROLE_CHANGE')` | N | | | 수행한 작업 유형 |
| `target_type` | `VARCHAR(50)` | N | | | 작업 대상 개체 타입 (`Product`, `Member` 등) |
| `target_id` | `BIGINT` | Y | `NULL` | | 작업 대상 개체의 고유 식별자 |
| `result` | `ENUM('SUCCESS','FAILED','DENIED')` | N | | | 작업 결과 (성공 / 실패 / 권한 거부) |
| `before_value` | `JSON` | Y | `NULL` | | 변경 이전 데이터 스냅샷 (JSON) |
| `after_value` | `JSON` | Y | `NULL` | | 변경 이후 데이터 스냅샷 (JSON) |
