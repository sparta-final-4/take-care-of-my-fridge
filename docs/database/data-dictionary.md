# 데이터 사전 초안

상태: 개인 담당 8개 테이블 컬럼 초안 · DDL 미실행 · 근거: S2, S4, 2026-10-14 사용자 결정

개인 담당(회원·인증·관리자) 테이블의 컬럼을 정의합니다. 관계는 [ERD](erd.md), 실행용 DDL 초안은 [member-auth-admin.sql](member-auth-admin.sql)에 있습니다. 공통 규칙은 [DB 규칙](../conventions/database.md)을 따릅니다.

## 공통 사항

- 모든 테이블: `id BIGINT AUTO_INCREMENT PK`, `created_at`·`updated_at DATETIME(6)`, 테이블·컬럼 한국어 COMMENT.
- 아래 표에는 공통 컬럼(id, created_at, updated_at)을 생략합니다. 의미가 다른 경우만 따로 적습니다.
- 금액·수량은 원·개 단위 INT입니다. 금액은 `NOT NULL DEFAULT 0`입니다.
- 다른 도메인 ID(product_id, promotion_id)는 물리 FK 없이 저장합니다.
- 시간대, 감사 시각 관리 주체(Q-06), DB 제품(Q-07)은 미정입니다.

## members — 회원

| 컬럼 | 타입 | NULL | 기본값 | 제약 | 의미 |
|---|---|---|---|---|---|
| email | VARCHAR(255) | 불가 | | UK | 로그인 이메일 |
| password_hash | VARCHAR(255) | 불가 | | | 비밀번호 해시(복원 불가) |
| nickname | VARCHAR(30) | 불가 | | UK | 닉네임. 필수·중복 불가, 후기에 표시 |
| phone | VARCHAR(11) | 허용 | NULL | UK | 가입 시 필수인 010 전화번호. 하이픈을 제외한 숫자 11자리로 저장. 기존 회원·초기 관리자 NULL 허용 |
| role | ENUM('USER','ADMIN') | 불가 | 'USER' | | 공개 가입은 USER. 관리자 승격 시 ADMIN |
| status | ENUM('ACTIVE','SUSPENDED') | 불가 | 'ACTIVE' | | 정상·정지. 회원 탈퇴 기능이 없어 WITHDRAWN 없음 |
| created_at | DATETIME(6) | 불가 | 현재 시각 | | 가입일시 |

- 이메일 대소문자·공백 정규화 규칙은 Q-09에서 정합니다.
- 정지는 누가 처리할지 미정입니다. 정해지면 A-10 회원 권한 관리 화면에 추가합니다.

## member_tastes — 회원 맛 선호

| 컬럼 | 타입 | NULL | 기본값 | 제약 | 의미 |
|---|---|---|---|---|---|
| member_id | BIGINT | 불가 | | FK fk_member_tastes_member | 회원 |
| taste | ENUM('SALTY','SWEET','SPICY','UMAMI','MILD') | 불가 | | UK(member_id, taste) | 짠맛·단맛·매운맛·감칠맛·슴슴한맛 |

- 회원은 0개 이상 고를 수 있습니다. 하나도 고르지 않으면 행이 없습니다.
- 후기 화면은 이 테이블을 읽어 표시하므로, 수정하면 이미 쓴 후기에도 바뀐 맛이 보입니다.

## refresh_sessions — 로그인 세션

| 컬럼 | 타입 | NULL | 기본값 | 제약 | 의미 |
|---|---|---|---|---|---|
| member_id | BIGINT | 불가 | | FK fk_refresh_sessions_member | 회원 |
| token_hash | VARCHAR(255) | 불가 | | UK | 갱신 토큰 해시. 원문 토큰은 저장하지 않음 |
| keep_logged_in | BOOLEAN | 불가 | FALSE | | 현재 로그인 요청에서는 항상 FALSE. 선택 입력은 제외 |
| device_info | VARCHAR(255) | 허용 | NULL | | 기기별 로그아웃용(제안 기능) |
| last_used_at | DATETIME(6) | 불가 | 현재 시각 | | 마지막 사용일시. 30분 미사용이면 만료 |
| expires_at | DATETIME(6) | 불가 | | | 로그인 후 최대 7일 만료일시 |
| revoked_at | DATETIME(6) | 허용 | NULL | | 로그아웃·비밀번호 변경·정지 시 폐기일시 |
| created_at | DATETIME(6) | 불가 | 현재 시각 | | 로그인일시 |

- 토큰 회전·동시 갱신 처리는 Q-08에서 정합니다.

## prediction_runs — 주간 판매 예측 실행

| 컬럼 | 타입 | NULL | 기본값 | 제약 | 의미 |
|---|---|---|---|---|---|
| target_week_start | DATE | 불가 | | UK | 예측 대상 주의 월요일 |
| model_version | VARCHAR(50) | 불가 | | | 예측 모델 버전(예: fc-v0.3) |
| data_cutoff_at | DATETIME(6) | 불가 | | | 입력 데이터 기준 시각 |
| confirmed_at | DATETIME(6) | 허용 | NULL | | 실제 판매량 확정일시(다음 주 월요일 06:00) |
| synthetic_data | BOOLEAN | 불가 | false | | 가상 데이터 사용 여부 |
| created_at | DATETIME(6) | 불가 | 현재 시각 | | 예측 생성일시(일요일 22:00) |

- 주 1건만 저장하며, 주가 시작되면 예측을 바꾸지 않습니다.

## sales_forecasts — 상품별 예측과 실제 판매량

| 컬럼 | 타입 | NULL | 기본값 | 제약 | 의미 |
|---|---|---|---|---|---|
| prediction_run_id | BIGINT | 불가 | | FK fk_sales_forecasts_prediction_run | 예측 실행 |
| product_id | BIGINT | 불가 | | UK(prediction_run_id, product_id) | 상품 ID(상품 도메인, FK 없음) |
| product_name | VARCHAR(100) | 불가 | | | 예측 시점 상품명 |
| predicted_rank | INT | 허용 | NULL | | 예상 순위. 예상 TOP 10 밖이었다가 실제 진입한 상품은 NULL |
| predicted_qty | INT | 허용 | NULL | | 예상 판매 수량 |
| baseline_qty | INT | 불가 | 0 | | 기준선 예측(전주 판매량) |
| actual_rank | INT | 허용 | NULL | | 실제 순위. 확정 후 입력 |
| actual_qty | INT | 허용 | NULL | | 실제 판매 수량(결제 완료 − 취소·환불). 확정 후 입력 |

- 기준선 TOP 10 비교를 위해 예측 대상 상품의 기준선 수량을 저장합니다. 실적 확정 시 예상 TOP 10 상품의 실제 판매 수량이 0이어도 0으로 저장합니다.

## deal_analyses — 요일별 할인 후보 분석

"후보 분석" 버튼을 한 번 누를 때마다 1건이 생깁니다.

| 컬럼 | 타입 | NULL | 기본값 | 제약 | 의미 |
|---|---|---|---|---|---|
| member_id | BIGINT | 불가 | | FK fk_deal_analyses_member | 분석한 관리자 |
| target_week_start | DATE | 불가 | | | 대상 주의 월요일 |
| day_of_week | ENUM('MON'~'SUN') | 불가 | | | 적용 요일 |
| discount_rate | INT | 불가 | 0 | | 가상 할인율(%, 10~40) |
| min_stock | INT | 불가 | 50 | | 최소 재고 기준(개) |
| model_version | VARCHAR(50) | 불가 | | | 분석 모델 버전 |
| data_cutoff_at | DATETIME(6) | 불가 | | | 입력 데이터 기준 시각 |
| synthetic_data | BOOLEAN | 불가 | FALSE | | 가상 데이터 사용 여부 |
| last_saved_at | DATETIME(6) | 허용 | NULL | | 검토 결과 마지막 저장일시 |
| created_at | DATETIME(6) | 불가 | 현재 시각 | | 분석일시 |

## deal_candidates — 분석 결과 상품(후보·제외)

후보 상품과 제외 상품을 한 테이블에 저장하고 `is_excluded`로 구분합니다. 두 경우 모두 "이 분석에서 이 상품을 어떻게 판단했는지"를 담기 때문입니다.

| 컬럼 | 타입 | NULL | 기본값 | 제약 | 의미 |
|---|---|---|---|---|---|
| deal_analysis_id | BIGINT | 불가 | | FK fk_deal_candidates_deal_analysis | 분석 |
| product_id | BIGINT | 불가 | | UK(deal_analysis_id, product_id) | 상품 ID(FK 없음) |
| product_name | VARCHAR(100) | 불가 | | | 분석 시점 상품명 |
| regular_price | INT | 불가 | 0 | | 분석 당시 정상가(원) |
| discount_price | INT | 불가 | 0 | | 가상 할인가(원) |
| current_stock | INT | 불가 | 0 | | 분석 당시 재고(개) |
| avg_sales_on_day | INT | 불가 | 0 | | 해당 요일 평균 판매량 |
| search_clicks | INT | 불가 | 0 | | 최근 4주 같은 요일 검색 클릭 수 |
| purchases | INT | 불가 | 0 | | 최근 4주 같은 요일 구매 확정 후 환불을 뺀 상품 수량 |
| is_excluded | BOOLEAN | 불가 | FALSE | | TRUE면 제외된 상품 |
| exclude_reason | ENUM('TOP_SELLER_ON_DAY','LOW_STOCK') | 허용 | NULL | | 평소 그 요일 판매 상위 / 재고 50개 미만. 제외 상품만 |
| candidate_reason | VARCHAR(255) | 허용 | NULL | | 후보 사유(예: 검색 클릭 대비 구매 적음). 후보만 |
| expected_qty_regular | INT | 허용 | NULL | | 정상가일 때 예상 판매 수량. 후보만 |
| expected_qty_discount | INT | 허용 | NULL | | 할인가일 때 예상 판매 수량. 후보만 |
| expected_increase | INT | 허용 | NULL | | 예상 판매 증가량. 후보만 |
| review_status | ENUM('UNREVIEWED','SELECTED','HELD') | 허용 | NULL | | 미검토·선택·보류. 후보만 |
| event_request_status | ENUM('NONE','REQUESTED','APPROVED','REJECTED') | 불가 | 'NONE' | | 행사 등록 상태. 현재 직접 등록 성공 시 APPROVED |
| event_requested_at | DATETIME(6) | 허용 | NULL | | 요청일시 |
| promotion_id | BIGINT | 허용 | NULL | | 생성된 sale_events ID(FK 없음) |

- 이 화면은 실제 상품 가격을 바꾸지 않습니다. 가격 변경은 할인 행사 등록 후 행사 쪽에서 일어납니다.

## admin_audit_logs — 관리자 작업 이력

| 컬럼 | 타입 | NULL | 기본값 | 제약 | 의미 |
|---|---|---|---|---|---|
| member_id | BIGINT | 불가 | | FK fk_admin_audit_logs_member | 작업한 회원. 권한 없이 시도한 일반 회원 포함 |
| actor_role | ENUM('USER','ADMIN') | 허용 | NULL | | 작업 당시 역할. 기존 기록은 NULL |
| action | ENUM('PRODUCT_CREATE','PRODUCT_DELETE','PRICE_UPDATE','DEAL_REVIEW','EVENT_REQUEST','ROLE_CHANGE') | 불가 | | | 작업 종류 |
| target_type | VARCHAR(50) | 불가 | | | 대상 종류(Product, DealCandidate, Member 등) |
| target_id | BIGINT | 허용 | NULL | | 대상 ID. 등록 실패처럼 대상이 없으면 NULL. FK 아님 |
| result | ENUM('SUCCESS','FAILED','DENIED') | 불가 | | | 성공·실패·권한 거부 |
| before_value | JSON | 허용 | NULL | | 변경 전 값. 비밀번호 등 민감 값 제외 |
| after_value | JSON | 허용 | NULL | | 변경 후 값. 민감 값 제외 |
| created_at | DATETIME(6) | 불가 | 현재 시각 | | 작업일시 |

- 일반 작업 기록 서비스는 작업자·동작·대상·결과만 받습니다. 관리자 승격 기록은 role의 변경 전후 값만 JSON 컬럼에 저장합니다. 비밀번호 등 민감 값은 저장하지 않습니다.
- 기록은 수정·삭제하지 않습니다. 보관 기간은 미정입니다.
- 미인증 요청(로그인 전)의 실패는 회원을 특정할 수 없어 이 테이블에 남기지 않습니다. 필요하면 별도 로그로 다룹니다.

## carts — 장바구니

- 회원당 장바구니 하나입니다. 처음 상품을 담을 때 만들고, 상품이 모두 지워져도 남깁니다.
- 제약 이름: `uk_carts_member_id`.

| 컬럼 | 타입 | NULL | 기본값 | 제약 | 의미 |
|---|---|---|---|---|---|
| member_id | BIGINT | 불가 | | UK | 장바구니 주인. 물리 FK 없이 회원 번호만 저장 |

## cart_items — 장바구니 상품

- 같은 장바구니에 같은 상품은 하나입니다. 다시 담으면 수량을 더합니다.
- 제약 이름: FK `fk_cart_items_cart`, UK `uk_cart_items_cart_id_product_id`.

| 컬럼 | 타입 | NULL | 기본값 | 제약 | 의미 |
|---|---|---|---|---|---|
| cart_id | BIGINT | 불가 | | FK, UK(cart_id, product_id) | 소속 장바구니 |
| product_id | BIGINT | 불가 | | UK(cart_id, product_id) | 담은 상품. 물리 FK 없이 상품 번호만 저장 |
| quantity | INT | 불가 | | | 담은 수량(1 이상) |

- 가격·할인은 저장하지 않습니다. 조회할 때 상품의 현재 정가와 진행 중인 할인을 읽습니다.
- 실행용 DDL 초안은 [cart.sql](cart.sql)에 있습니다.

## orders — 주문

- 결제 대기에서 시작해 결제 완료, 취소, 구매 확정으로 바뀝니다. 주문 한 건에 주문 상품 여러 건이 있습니다.
- 제약 이름: UK `uk_orders_order_number`, `uk_orders_member_id_idempotency_key`. 인덱스 `idx_orders_member_id_status`(member_id, status), `idx_orders_status_created_at`(status, created_at), `idx_orders_status_paid_at`(status, paid_at).

| 컬럼 | 타입 | NULL | 기본값 | 제약 | 의미 |
|---|---|---|---|---|---|
| order_number | VARCHAR(20) | 불가 | | UK | 주문번호. 밀리초 시각 17자리와 순번 3자리의 숫자 20자리(한국 시각) |
| member_id | BIGINT | 불가 | | UK(member_id, idempotency_key), 인덱스 | 주문한 회원. 물리 FK 없이 회원 번호만 저장 |
| idempotency_key | VARCHAR(64) | 허용 | NULL | UK(member_id, idempotency_key) | 주문하기 클릭마다 프론트가 만든 값. 같은 회원이 같은 값으로 다시 보내면 새 주문을 만들지 않음. 기존 주문은 NULL |
| status | ENUM('PENDING','PAID','CANCELLED','CONFIRMED') | 불가 | 'PENDING' | 인덱스 | 결제 대기·결제 완료·취소·구매 확정 |
| total_amount | BIGINT | 불가 | | | 결제할 금액(할인 후). 주문을 만들 때 확정 |
| paid_at | DATETIME(6) | 허용 | NULL | | 결제 완료 시각. 결제 전에는 NULL |
| confirmed_at | DATETIME(6) | 허용 | NULL | | 구매 확정 시각. 확정 전에는 NULL |

## order_items — 주문 상품

- 주문할 때의 상품 이름·이미지·가격을 복사해 저장합니다. 이후 상품이 바뀌어도 주문 화면은 이 값을 씁니다.
- 제약 이름: UK `uk_order_items_sale_event_item_id_flash_drop_member_id`.

| 컬럼 | 타입 | NULL | 기본값 | 제약 | 의미 |
|---|---|---|---|---|---|
| order_id | BIGINT | 불가 | | FK | 소속 주문 |
| product_id | BIGINT | 불가 | | | 주문한 상품. 물리 FK 없이 상품 번호만 저장 |
| product_name | VARCHAR(200) | 불가 | | | 주문 당시 상품 이름 |
| product_image_url | VARCHAR(500) | 허용 | NULL | | 주문 당시 상품 이미지. 없으면 NULL |
| quantity | INT | 불가 | | | 주문 수량 |
| list_price | INT | 불가 | | | 주문 당시 정가(원) |
| unit_price | INT | 불가 | | | 실제 단가(원). 할인 중이면 할인가, 아니면 정가 |
| sale_type | ENUM('NONE','SPECIAL','FLASH_DROP') | 불가 | | | 주문 당시 할인 구분 |
| sale_event_item_id | BIGINT | 허용 | NULL | | 할인으로 샀을 때의 행사 항목. 일반 상품은 NULL. 물리 FK 없음 |
| flash_drop_member_id | BIGINT | 허용 | NULL | UK(sale_event_item_id, flash_drop_member_id) | 번개 드롭 1인 1개 확인용 회원. 번개 드롭 항목에만 값이 있고 주문을 취소하면 NULL |
| confirmed_quantity | INT | 허용 | NULL | | 구매 확정 수량. 구매 확정 때 주문 수량에서 완료된 환불 수량을 뺀 값. 확정 전에는 NULL이고 확정 뒤에는 바뀌지 않음 |

- 같은 행사 항목에 같은 회원은 한 번만 들어갈 수 있습니다(번개 드롭 1인 1개). NULL은 여러 건 가능합니다.

## refunds — 환불

- 환불 신청 한 번에 한 건입니다. 한 결제에 부분 환불이 여러 건 생길 수 있고, 같은 주문에 요청 상태 환불은 한 건만 둡니다.
- 요청에서 완료 또는 실패로 한 번만 바뀝니다. 실패한 환불은 지우지 않고 남기며, 그 수량은 다시 신청할 수 있습니다.
- 제약 이름: 인덱스 `idx_refunds_refunded_at`(refunded_at). FK `fk_refunds_payment`.

| 컬럼 | 타입 | NULL | 기본값 | 제약 | 의미 |
|---|---|---|---|---|---|
| payment_id | BIGINT | 불가 | | FK | 환불할 결제. 주문의 결제 완료 결제 |
| refund_amount | BIGINT | 불가 | | | 환불 금액(원). 환불 상품 금액의 합 |
| reason_type | ENUM('CHANGE_OF_MIND','ORDER_MISTAKE','NO_LONGER_NEEDED','ETC') | 불가 | | | 단순 변심·주문 실수·더 이상 필요 없음·기타. 설명 문구를 PG 취소 사유로 보냄 |
| reason_detail | VARCHAR(500) | 허용 | NULL | | 상세 사유 |
| status | ENUM('REQUESTED','COMPLETED','FAILED') | 불가 | | | 요청(PG 취소 대기)·완료·실패 |
| portone_cancellation_id | VARCHAR(200) | 허용 | NULL | | PG 취소 건 ID. 완료 전이나 찾지 못하면 NULL |
| fail_reason | VARCHAR(255) | 허용 | NULL | | 실패 사유(PG 오류 원문). 길면 255자에서 잘라 저장. 사용자에게 보여 주지 않음 |
| refunded_at | DATETIME(6) | 허용 | NULL | 인덱스 | 환불 완료 시각. 정산 기준 시각. 완료 전에는 NULL |

- `created_at`이 신청 시각입니다.
- 실행용 DDL 초안은 [refund.sql](refund.sql)에 있습니다.

## refund_items — 환불 상품

- 환불 한 건에 포함된 주문 상품과 수량입니다. 단가는 주문 상품에서 복사합니다.
- 제약 이름: UK `uk_refund_items_refund_id_order_item_id`. 인덱스 `idx_refund_items_order_item_id`(order_item_id). FK `fk_refund_items_refund`, `fk_refund_items_order_item`.

| 컬럼 | 타입 | NULL | 기본값 | 제약 | 의미 |
|---|---|---|---|---|---|
| refund_id | BIGINT | 불가 | | FK, UK(refund_id, order_item_id) | 소속 환불 |
| order_item_id | BIGINT | 불가 | | FK, UK(refund_id, order_item_id), 인덱스 | 환불할 주문 상품. 일반 상품만 |
| quantity | INT | 불가 | | | 환불 수량(1 이상) |
| unit_price | INT | 불가 | | | 주문 당시 단가(원) |
| amount | BIGINT | 불가 | | | 환불 금액(원). 단가 × 수량 |

- 상품별 누적 환불 수량은 요청·완료 상태 환불의 수량 합입니다. 이 값이 주문 수량을 넘지 않게 신청 때 확인합니다.
- 한 환불에 같은 주문 상품은 한 번만 들어갑니다.

## sale_events — 할인 행사

- 상품 하나에 대한 한 번의 할인 기간입니다. 등록하면 상품마다 한 건씩 만들어지고, 예정에서 진행 중, 종료로 바뀝니다.
- 제약 이름: 인덱스 `idx_sale_events_status_starts_at`(status, starts_at), `idx_sale_events_status_ends_at`(status, ends_at).

| 컬럼 | 타입 | NULL | 기본값 | 제약 | 의미 |
|---|---|---|---|---|---|
| type | ENUM('SPECIAL','FLASH_DROP') | 불가 | | | 특가·번개 드롭 |
| starts_at | DATETIME(6) | 불가 | | 인덱스 | 시작 시각(한국 시각) |
| ends_at | DATETIME(6) | 불가 | | 인덱스 | 종료 시각. 중간에 종료해도 바뀌지 않음 |
| status | ENUM('SCHEDULED','ACTIVE','ENDED') | 불가 | | 인덱스 | 예정·진행 중·종료 |

## sale_event_items — 할인 행사 항목

- 할인 행사에 든 상품과 가격입니다. 등록은 상품마다 행사를 하나씩 만들어서 지금은 행사 하나에 항목 하나입니다.
- 제약 이름: 인덱스 `idx_sale_event_items_product_id`(product_id).

| 컬럼 | 타입 | NULL | 기본값 | 제약 | 의미 |
|---|---|---|---|---|---|
| sale_event_id | BIGINT | 불가 | | FK | 소속 할인 행사 |
| product_id | BIGINT | 불가 | | 인덱스 | 할인 상품. 물리 FK 없이 상품 번호만 저장 |
| list_price | INT | 불가 | | | 등록(수정) 때 읽은 정가(원). 종료된 행사의 정가 표시에 씀 |
| sale_price | INT | 불가 | | | 할인가(원). 정가 × (100 − 할인율) / 100의 원 아래 버림 |
| discount_rate | INT | 불가 | | | 할인율(1~99) |
| limited_quantity | INT | 허용 | NULL | | 번개 드롭 한정 수량. 특가는 NULL |
| remaining_quantity | INT | 허용 | NULL | | 번개 드롭 남은 수량. 특가는 NULL. 주문하면 줄고 취소하면 한정 수량까지 늘어남 |

- 번개 드롭 1인 1개는 `order_items`의 UK(sale_event_item_id, flash_drop_member_id)가 막습니다.
- 실행용 DDL 초안은 [sale-event.sql](sale-event.sql)에 있습니다.

## payments — 결제

- 결제하기를 누를 때마다 한 건씩 만듭니다. 한 주문에 결제가 여러 건 있을 수 있고(재결제), 결제 완료는 주문 잠금으로 한 건만 남습니다.
- 상태는 결제 대기·결제 완료·실패만 갖습니다. 결제 완료 뒤의 취소(환불·보상 취소)는 결제 이력과 환불 도메인에 남깁니다.
- 제약 이름: UK `uk_payments_portone_payment_id`. 인덱스 `idx_payments_status_expires_at`(status, expires_at). FK `fk_payments_order`.

| 컬럼 | 타입 | NULL | 기본값 | 제약 | 의미 |
|---|---|---|---|---|---|
| order_id | BIGINT | 불가 | | FK | 결제할 주문 |
| portone_payment_id | VARCHAR(100) | 불가 | | UK | PortOne 결제 ID. 서버가 `payment-` + 하이픈 없는 UUID로 만든 40자. PortOne 결제의 멱등 키 역할 |
| amount | BIGINT | 불가 | | | 결제 금액(원). 클라이언트 값이 아니라 주문 금액(`orders.total_amount`) |
| status | ENUM('READY','PAID','FAILED') | 불가 | | 인덱스 | 결제 대기·결제 완료·실패 |
| paid_at | DATETIME(6) | 허용 | NULL | | 결제 완료 시각(서버가 승인한 시각). 결제 완료 전에는 NULL |
| expires_at | DATETIME(6) | 불가 | | 인덱스 | 결제 대기 만료 시각. 생성 10분 뒤. 이 시각 이후 PG에서 결제되면 받지 않고 취소 |
| fail_reason | ENUM('PG_FAILED','USER_CANCELLED','AMOUNT_MISMATCH','TIMEOUT','ORDER_NOT_PAYABLE','SUPERSEDED') | 허용 | NULL | | 실패 사유. PG 실패·결제창 닫기·금액 불일치·만료·결제할 수 없는 주문·새 결제로 정리. 실패가 아니면 NULL |

- 실행용 DDL 초안은 [payment.sql](payment.sql)에 있습니다.

## payment_histories — 결제 이력

- 결제 한 건에 일어난 사건을 시간순으로 추가만 합니다. 수정·삭제하지 않습니다.
- 결제 상태 변경과 같은 트랜잭션에서 남기므로 상태 변경이 롤백되면 이력도 남지 않습니다.
- 제약 이름: 인덱스 `idx_payment_histories_payment_id`(payment_id, created_at), `idx_payment_histories_refund_id`(refund_id). FK `fk_payment_histories_payment`.

| 컬럼 | 타입 | NULL | 기본값 | 제약 | 의미 |
|---|---|---|---|---|---|
| payment_id | BIGINT | 불가 | | FK, 인덱스 | 대상 결제 |
| event_type | ENUM('READY','PAID','FAILED','PARTIAL_CANCELLED','CANCELLED') | 불가 | | | 결제 시도 생성·PG 결제됨·실패·PG 부분 취소·PG 전액 취소. 받지 않은 결제가 PG에서 결제되면 `PAID`를 남겨 보상 취소 대상으로 표시 |
| amount | BIGINT | 허용 | NULL | | 이번 사건 금액(원). 실패는 돈이 움직이지 않아 NULL |
| cancelled_amount | BIGINT | 불가 | | | 사건 직후 누적 취소 금액(원) |
| refund_id | BIGINT | 허용 | NULL | 인덱스 | 환불로 생긴 취소면 환불 ID. 보상 취소·콘솔 취소는 NULL. 물리 FK 없음 |
| pg_cancellation_id | VARCHAR(200) | 허용 | NULL | | PG 취소 건 ID. 환불 쪽이 PG 취소 목록과 맞춰 볼 때 씀 |
| changed_by | ENUM('USER','WEBHOOK','SCHEDULER') | 불가 | | | 사건을 일으킨 주체 |
| reason | VARCHAR(255) | 허용 | NULL | | 사유. 길면 255자에서 잘라 저장 |

- `created_at`이 사건 시각입니다.
- 결제 상태와 사건 종류는 다릅니다. 결제는 `FAILED`인데 이력에 `PAID`가 있고 `CANCELLED`가 없으면 보상 취소가 끝나지 않은 결제로 보고 스케줄러가 다시 취소합니다.

## webhook_events — PortOne 웹훅 수신 기록

- 서명 검증을 통과한 웹훅만 저장합니다. 결제 처리와 다른 트랜잭션에서 저장해 처리가 실패해도 기록이 남습니다.
- 제약 이름: UK `uk_webhook_events_webhook_id`. FK `fk_webhook_events_payment`.

| 컬럼 | 타입 | NULL | 기본값 | 제약 | 의미 |
|---|---|---|---|---|---|
| payment_id | BIGINT | 허용 | NULL | FK | 매칭된 결제. 결제를 못 찾았거나 처리 대상이 아닌 웹훅은 NULL |
| webhook_id | VARCHAR(200) | 불가 | | UK | PortOne `webhook-id` 헤더. 같은 웹훅을 한 번만 처리 |
| event_type | VARCHAR(50) | 불가 | | | 웹훅 type 원문(예: `Transaction.Paid`). SDK가 모르는 type도 그대로 저장 |
| process_status | ENUM('RECEIVED','PROCESSED','FAILED','IGNORED') | 불가 | | | 수신 후 처리 중·상태 반영·처리 실패·바꿀 것 없음. `RECEIVED`·`FAILED`는 재전송 때 다시 처리 |
| raw_payload | TEXT | 불가 | | | 웹훅 본문 원문 |
| fail_reason | VARCHAR(500) | 허용 | NULL | | 처리 실패 사유. 길면 500자에서 잘라 저장 |
| processed_at | DATETIME(6) | 허용 | NULL | | 처리 완료 시각(`PROCESSED`·`IGNORED`) |

## search_click_histories — 검색 결과 상품 클릭 기록

- 검색 결과에서 상품을 누를 때마다 한 건씩 쌓습니다. 수정·삭제하지 않습니다.
- 클릭 시각은 공통 컬럼 `created_at`(서버 저장 시각, 한국 시각)입니다. 클라이언트 시각은 쓰지 않습니다.
- 중복 클릭은 제거하지 않고 누른 만큼 기록합니다. 상품이 실제로 있는지는 확인하지 않습니다.
- 제약 이름: 인덱스 `idx_search_click_histories_created_at_product_id`(created_at, product_id). 기간·기준 시각 범위의 상품별 클릭 수 집계용입니다.

| 컬럼 | 타입 | NULL | 기본값 | 제약 | 의미 |
|---|---|---|---|---|---|
| product_id | BIGINT | 불가 | | 인덱스 | 클릭한 상품. 물리 FK 없이 상품 번호만 저장 |
| member_id | BIGINT | 허용 | NULL | | 클릭한 회원. 비로그인 클릭은 NULL. 물리 FK 없음 |
| keyword | VARCHAR(100) | 불가 | | | 클릭 당시 검색어(앞뒤 공백 제거) |

- 실행용 DDL 초안은 [search.sql](search.sql)에 있습니다.

## notifications — 알림

- 회원 한 명에게 보이는 알림 하나입니다. 다른 도메인이 보낸 알림 요청(`notification-requested`)을 받아 저장합니다.
- 수정은 읽음 여부만 바뀝니다. 지우지 않습니다.
- 같은 회원에게 같은 중복 방지 키는 하나만 저장합니다. 같은 요청이 두 번 와도 알림은 하나입니다.
- 제약 이름: UK `uk_notifications_member_id_dedupe_key`(member_id, dedupe_key). 회원별 목록·안 읽은 수 조회도 이 인덱스의 앞부분(member_id)을 씁니다.

| 컬럼 | 타입 | NULL | 기본값 | 제약 | 의미 |
|---|---|---|---|---|---|
| member_id | BIGINT | 불가 | | UK(member_id, dedupe_key) | 받는 회원. 물리 FK 없이 회원 번호만 저장 |
| notification_type | ENUM('EXPIRATION','FLASH_DROP','COMMERCE_STOCKED') | 불가 | | | 유통기한 임박·번개 드롭·입고 |
| content | VARCHAR(200) | 불가 | | | 알림 문구 |
| link_url | VARCHAR(500) | 불가 | | | 누르면 이동할 화면 경로(예: `/products/{상품 ID}`) |
| dedupe_key | VARCHAR(150) | 불가 | | UK(member_id, dedupe_key) | 보낸 도메인이 정한 중복 방지 키 |
| is_read | BOOLEAN | 불가 | FALSE | | 읽음 여부 |

- 목록 순서와 "더 보기" 기준은 id입니다. 클수록 나중에 저장된 알림입니다.
- 전부 읽음은 일괄 UPDATE라 updated_at이 바뀌지 않습니다.
- 실행용 DDL 초안은 [notification.sql](notification.sql)에 있습니다.

## 다른 도메인에 요청할 컬럼

| 도메인 | 필요한 데이터 | 근거 |
|---|---|---|
| 상품 | 유통기한(DATE, 필수), 보관 방법(ENUM 상온/냉장/냉동, 필수), 정상가 수정 | Q-03, Q-04 |
| 주문·결제 | 상품별 확정 판매 수량(취소·환불 제외)과 집계 기준 시각 | 판매 예측 |
| 재고 | 상품별 현재 재고와 기준 시각 | 할인 후보, 상품 상세 |
| 검색·추천 | 상품별·요일별 검색 클릭·구매 수와 집계 기준 시각 | 할인 후보 |
| 후기 | 작성자 닉네임·맛 선호를 회원 정보에서 읽어 표시 | 맛 선호 |

## 새 테이블 작성 템플릿

### <테이블명> — <한국어 설명>

- 담당 도메인:
- 상태·근거:
- 보관·논리 삭제 정책:

| 컬럼 | 물리 타입 | NULL | 기본값 | PK/FK/UNIQUE | 의미·단위·허용값 |
|---|---|---|---|---|---|
| 작성 필요 | 미정 | 미정 | 미정 | 미정 | 작성 필요 |

- 인덱스와 대상 조회:
- FK 대상·삭제·갱신 동작:
- 업무 검증과 DB 제약:
- 시간대·정밀도:
- 마이그레이션·기존 데이터 영향:
