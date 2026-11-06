# 상담 카드 장볼 재료 [담기]

상태: 검증 대기 · 작성일: 2026-11-06 · 담당: zcookiez

브랜치 `feat/chat-commerce`. 커밋은 로컬에만 있고 push하지 않았다.

## 목표와 완료 기준
- 사용자 문제: AI 상담 추천 카드의 장볼 재료는 이름만 보였다. 살 수 있는 상품과 가격을 알 수 없었고, 담으려면 상품 화면으로 가서 다시 찾아야 했다.
- 완료 조건:
  - 장볼 재료마다 살 수 있는 상품 하나를 붙인다(가격, 할인가, 품절).
  - 펼친 목록에서 [담기]를 누르면 상담 창 안에서 장바구니에 1개 담긴다.
  - 새 추천, 다시 불러오기, 레시피 자세히 카드가 모두 같다.

## 근거와 범위
- 요구사항: 기획서 "부족 재료·밀키트 추천"(`project-plan.md:22`), 화면 "AI 상담 팝업 부족 재료·밀키트 제안"(`screens.md:38`)
- 변경 영역:
  - product: 재료 ID 조회 추가 (상품 담당 준모님 허락을 받고 사용자가 직접 만들기로 함)
  - chat: 카드 응답, 상품 고르기
  - fe chat: 카드 컴포넌트
- 장바구니는 기존 `POST /api/cart-items`와 `useAddToCart`를 그대로 써서 바꾸지 않았다.
- 정한 정책:
  - 재료 하나에 상품이 여러 개면 살 수 있는 것 → 할인 중 → 판매 단가 낮은 순 → 상품 ID
  - 상품 정보는 SSE 카드에 처음부터 넣는다(따로 조회하는 API 없음).
- 밀키트 추천은 `mealkit_products`에 조리 시간·레시피 연결 칸이 없어 다음 PR로 미뤘다.

## AI 활용과 개발자 판단
- AI에 맡긴 작업: 상품·장바구니·할인 코드와 dev 데이터 조사, 구현·테스트, oag 갱신
- 조사에서 확인한 것:
  - 재료 ID로 상품을 찾는 메서드가 없었다. 상품 하나는 재료 하나에만 이어지고(`product_id` 유일), 재료 하나에는 상품이 여러 개일 수 있다.
  - 할인 가격은 `SaleEventReader.findSellingPrices`가 상품 화면·장바구니·주문의 공통 입구다.
  - dev 상품: 재료 상품 12개(재료 11개), 밀키트 7개
- oag 갱신 방법:
  - `pnpm oag:generate`는 떠 있는 백엔드에서 스펙을 받는다.
  - 서버를 dev DB에 띄우면 정기 작업이 dev DB에 돌기 때문에, 대신 임시 `@SpringBootTest`(Testcontainers MySQL)로 `/v3/api-docs`를 받았다.
  - 스크립트와 같은 정리(servers 삭제, 2칸 들여쓰기)를 한 뒤 `pnpm oag:generate:offline`으로 생성했다. 임시 테스트는 지웠다.
  - 스펙 변경은 `MissingIngredient.product`와 `ShoppingProduct`뿐이다.

## 구현 결과
- 상품 도메인 (`493a383c`):
  - `IngredientProductRepository.findWithProductByIngredientIdIn`: 재료 ID IN + 상품 상태로 고르고, 상품을 함께 읽는다(fetch join).
  - `ProductService.getPublicIngredientProducts`: 판매 중지는 빼고 품절은 넣는다. 빈 입력이면 조회하지 않는다.
- 상담 카드 (`d14a0c5b`):
  - `MissingIngredient`에 `product`(`ShoppingProduct`, 없으면 null)를 더했다.
    - 칸: productId, name, imageUrl, price, salePrice, saleType, soldOut, unitAmount, unit
  - 고른 카드들의 장볼 재료 ID를 모아 상품 조회 1번, 판매가 계산 1번만 한다. 장볼 재료나 상품이 없으면 그 조회를 건너뛴다.
  - `ShoppingProductPicker`(순수 클래스)가 재료마다 하나를 고른다.
  - 새 추천, `rebuildCards`(지금 가격·품절 기준), 레시피 자세히(`describe`) 모두 같다.
- 프론트:
  - `f9b273b1` oag 갱신
  - `593a8208` 카드:
    - `ChatShoppingList`(신규): 재료·상품 이름, 가격(할인이면 할인가 + 줄 그은 정가), [담기]를 보여 준다.
      - 상품이 없으면 "판매 상품 없음"으로 흐리게, 품절이면 [담기]를 막고 "품절"로 보여 준다.
    - [담기]는 `useAddToCart().addToCart(productId, 1)`을 쓴다. 성공 알림은 상품 화면과 같은 "장바구니에 담았습니다."이다.
    - 레시피 링크로 이어지지 않게 `preventDefault`·`stopPropagation`을 걸어 상담 창은 그대로 둔다.
    - `recommendations.ts`: SSE·메시지 조회 카드의 product를 읽는다. 모양이 다르면 null로 둔다.
- 문서: `api-spec.md`에 SSE `recommendations` 이벤트의 장볼 재료 상품 칸을 추가했다.

## 검증
| 명령·시나리오 | 실행 환경 | 결과 | 근거 |
|---|---|---|---|
| `./gradlew clean test` | 로컬 | 통과 | 1,983개, 실패 0, 건너뜀 1(기존 수동 테스트) |
| 재료 ID 조회 쿼리 | Testcontainers MySQL | 통과 | 상태 거르기, 상품 fetch, 한 재료 여러 상품 |
| 상품 고르기·카드 붙이기 | 단위 테스트 | 통과 | 상품 있음/없음/품절/할인/여러 개 중 고르기, 상품·판매가 조회 각 1번, 다시 불러오기·레시피 자세히 |
| `pnpm lint`, `pnpm typecheck`, `pnpm build`, `pnpm test` | 로컬 | 통과 | 테스트 219개 |
| `pnpm format:check` | 로컬 | 실패(이번 변경 아님) | 손대지 않은 35개 파일이 작업 폴더에서만 CRLF다. 저장소에는 LF로 있고, 이번에 바꾼 파일은 Prettier 통과 |
| 화면 확인 (375/768/1440px), 실제 담기 | 브라우저 도구 없음 | 미실행 | 없음 |
| 실제 SSE 응답 | 서버 미실행 | 미실행 | 단위 테스트의 카드 모양만 확인 |

## 연결과 남은 작업
- 커밋: `493a383c`, `d14a0c5b`, `f9b273b1`, `593a8208`, 이 문서 커밋
- 상품 담당에게 부탁할 것:
  - dev 데이터: "감자" 상품(12)이 재료 콩비지(1)에 잘못 이어져 있다.
  - 부하 테스트 상품(29·30)을 판매 중지한다.
  - 배추·돼지고기 상품을 추가한다.
- 다음 PR (밀키트 추천): `mealkit_products`에 `cook_time_min`과 레시피 연결 칸이 생긴 뒤에 한다.
- 확인 필요: [담기] 버튼 높이(antd 기본 크기)가 모바일 클릭 영역 44px 기준을 채우는지 화면에서 확인해야 한다.
