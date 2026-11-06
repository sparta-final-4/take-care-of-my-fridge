# 리뷰 반영: 인기 레시피 캐시와 구매 확정 냉장고 입고

상태: 검증 대기 · 작성일: 2026-11-06 · 담당: zcookiez

브랜치 `fix/review-fridge-popular`. 커밋은 로컬에만 있고 push하지 않았다.

## 리뷰 4. 인기 레시피 캐시 (PopularRecipeService#getPopularRecipes)

- 지적: 인기 목록을 캐시하는데 레시피가 수정되면? 24시간 늦어도 되는 정책인가?
- 결정: 레시피 삭제는 없지만 수정은 있고 이름·이미지가 바뀔 수 있으니 바로 반영한다.
- 조사:
  - 전에는 응답 전체(레시피 id·이름·이미지·팁 요약·만든 회원 수·평점·후기 수)를 Redis `recipe:popular`(키 size, TTL 24시간)에 담았다. 첫 요청에 채우고 스케줄러는 없다.
  - 레시피 수정(`RecipeChangedEvent`, 커밋 뒤 색인만 받음) 말고도 팁 요약 새벽 배치(`RecipeService.updateTipSummary`)와 후기 작성·수정·삭제가 이벤트 없이 값을 바꾼다. 이벤트로 캐시를 지우는 방법(A)은 세 군데를 챙겨야 하고 둘은 이벤트부터 만들어야 했다.
- 반영 (B, 순위만 캐시):
  - `PopularRecipeRankingService.getRanking(size)`: 최근 30일 요리한 회원 수 순위(레시피 id + 회원 수)만 Redis `recipe:popular-ranking`에 24시간 캐시한다. 순위는 30일 집계라 최대 24시간 늦게 바뀌어도 되는 정책으로 둔다. 빈 순위는 캐시하지 않는다.
  - `PopularRecipeService.getPopularRecipes`: 순위를 받아 레시피 이름·이미지·팁 요약(`findSummaries`)과 평점·후기 수(`findRatingStats`)를 요청마다 읽는다. 레시피 수정·팁 요약·후기가 바로 반영된다.
  - 캐시 이름을 바꿔 배포 뒤 예전 `recipe:popular` 값(응답 전체)을 읽지 않는다. 예전 키는 TTL로 사라진다.
- 테스트 중 발견: 캐시 테스트의 `Cache.clear()`는 Redis에서 바로 지워지지 않을 수 있어(Spring 문서상 비동기·지연 가능), 앞 테스트의 값이 남았다. `invalidate()`로 바꿨다.

### 응답 시간 (로컬 컨테이너 기준)
- 환경: 로컬 PC(Windows, Docker) Testcontainers MySQL 8.4 + Redis 7, 같은 JVM에서 `getPopularRecipes(6)` 호출. 레시피 1,600개, 회원 300명, 최근 30일 요리 기록 20,000건, 후기 5,000건. 워밍업 50번 뒤 300번 잼. 운영 환경 수치가 아니며 실행마다 몇 ms씩 흔들린다.

| 경우 | DB 쿼리 | 평균 | p50 | p95 |
|---|---|---|---|---|
| 캐시 없음 (집계 + 레시피 + 평점) | 3 | 21.05ms | 16.53ms | 43.30ms |
| 전 캐시 (응답 전체, Redis만) | 0 | 1.21ms | 0.81ms | 2.40ms |
| B 캐시 (순위만, 레시피·평점 매번) | 2 | 11.33ms | 7.22ms | 36.63ms |
| B 캐시 + `recipe_reviews(recipe_id)` 인덱스 | 2 | 6.55ms | 4.40ms | 20.40ms |

- B는 바로 반영되는 대신 요청마다 쿼리 2번이 늘어 전 캐시보다 느리고, 캐시 없음보다는 빠르다(평균 약 절반).
- 평점 집계(`findRatingStatsByRecipeIds`)가 `recipe_reviews` 전체를 훑는다(EXPLAIN type=ALL). 유니크 인덱스가 `(member_id, recipe_id)`라 recipe_id로 시작하는 인덱스가 없다. 인덱스를 두면 range 조회(rows 5,137 → 114)로 바뀐다. 후기 테이블은 이번 범위가 아니라 제안만 한다.
- 측정 테스트는 커밋하지 않았다.

## 리뷰 2. 구매 확정 냉장고 입고 (FridgeFacade#stockPurchasedItems)

- 지적: 입고할 상품을 구매 시점 정보가 아니라 입고 시점에 다시 조회해 정보가 어긋날 수 있다.
- 결정: 정책 문서화(b). "구매 확정 시점 상품 정보로 입고, 이름만 주문 당시 값".
- 조사:
  - 흐름: `OrderStatusService.confirm`(`order/service/OrderStatusService.java:62`) → 커밋 뒤 Kafka `purchase-confirmed` → `PurchaseConfirmedConsumer` → `FridgeFacade.stockPurchasedItems`. 메시지에는 orderItemId·productId·productName(주문 당시)·확정 수량만 있다.
  - 입고 때 다시 읽는 값: 상품 종류, 보관 방법, 보관 일수(소비기한 = 확정일 + 보관 일수), 재료 연결(ingredientId), 기본 수량(baseQuantity). `getProductDetailsByIds`는 상태를 거르지 않는다.
  - 주문 상품(`order_items`)에는 product_id·product_name·product_image_url·가격·수량은 있지만 입고용 값(보관·재료·기본 수량)은 없다.
  - 구매~확정(자동 확정은 결제 후 7일) 사이에 상품이 바뀌면: 보관 일수 → 소비기한, 보관 방법, 재료 연결 → 다른 재료, 기본 수량 → 수량이 새 값으로 들어간다. 이름은 영향 없다. 상품 삭제는 판매 중지(소프트 삭제)라 그대로 입고된다. 재료 연결 행이 없어지면 `PRODUCT_DETAIL_MISSING` → 재시도 2번 → DLT.
- 판단: 상품 쪽 변경은 대부분 잘못된 연결·보관 정보를 바로잡는 것(예: dev의 감자 상품 재료 연결 수정)이라 최근 값이 맞는 경우가 많다. 주문 상품에 입고용 값을 남기는 방법(a)은 주문 도메인(준혁님) 변경이 필요해 하지 않았다.
- 반영: `stockPurchasedItems` Javadoc에 정책을 적고, 판매 중지·이름이 바뀐 상품도 주문 당시 이름과 확정 시점 상품 정보로 입고되는 테스트로 고정했다.
- 리뷰 1(주문 쪽 재발행 없음)과의 관계: 발행이 실패하면 그 주문은 입고되지 않는다. 입고 쪽은 이미 입고된 주문 상품을 거르므로(`getStockedOrderItemIds`) 주문 쪽이 다시 발행해도 두 번 들어가지 않는다.

## 냉장고 줄 상품 ID (fridge.product_id)

- 구매 확정으로 들어온 줄에 상품 ID를 남긴다. 입고 메시지의 productId(주문 상품의 product_id)로 채우고, 직접 등록한 줄은 NULL이다.
- `Fridge.productId`(BIGINT NULL, 물리 FK 없이 ID만, `order_item_id`와 같은 방식). 응답(`FridgeItemResponse`)은 바꾸지 않았다.
- SQL: `docs/database/fridge-product-id.sql` (ALTER, 기존 커머스 입고 줄을 `order_items.product_id`로 채우기, 실행 전·후 확인 SELECT, product_id가 빈 줄만 채워 다시 실행해도 같다). dev는 ddl-auto=validate라 앱을 새 코드로 띄우기 전에 ALTER를 먼저 실행한다.
- 인덱스는 두지 않았다. 지금 냉장고를 읽는 곳(목록, 상담 후보)은 회원의 냉장고 전체를 읽고 메모리에서 거른다. "회원 + 상품"으로 찾는 쿼리가 생기면 그때 `idx_fridge_member_id_product_id (member_id, product_id)`를 둔다.
- 테스트: 입고 때 재료 상품·밀키트 줄에 상품 ID 저장(단위, Kafka·MySQL 통합), 직접 등록한 줄은 NULL.

## 검증
- `./gradlew clean test` 2,034개 통과, 실패 0, 건너뜀 1(기존 수동 테스트).
- 실제 Redis·dev DB에서의 동작과 SQL 실행은 하지 않았다.

## 나중에 할 것
- 상담 밀키트의 "냉장고에 같은 밀키트" 판단(`MealkitPicker`, 지금은 raw_name 비교)을 product_id 비교로 바꾼다. 냉장고 목록 응답(`FridgeItemResponse`)에 productId를 더하고, product_id가 없는 예전 줄은 raw_name으로 보완한다.
- 후기 테이블에 `recipe_reviews(recipe_id)` 인덱스 제안 (평점 집계가 전체를 훑음, 후기 담당 확인).
- 주문 쪽 구매 확정 재발행(리뷰 1)은 주문 담당. 입고 쪽은 중복 입고를 막아 두었다.
