# WORK-041: 장바구니 조회

상태: 검증 완료 · 작성일: 2026-10-19 · 담당: 주문

## 목표와 완료 기준

- 로그인 회원이 `GET /api/cart-items`로 본인 장바구니에 담긴 줄을 조회한다.

## 근거와 범위

- 선행 작업: WORK-039 장바구니 담기.
- 회원 번호는 로그인 토큰(`AuthPrincipal`)에서만 꺼낸다. 요청에 본문과 쿼리가 없다.
- 상품 도메인이 아직 없어 이름·정가·할인·품절 여부는 이번 범위에서 뺀다.

## 구현 결과

- CartController `GET /api/cart-items`: 200과 `data.items[]`를 반환한다.
- 줄마다 `cartItemId`, `productId`, `quantity`를 돌려준다. 담은 순서(id 오름차순)다.
- 장바구니가 없거나 비어 있으면 빈 목록이다.
- CartItemRepository `findByCartMemberIdOrderByIdAsc`: 회원의 장바구니에 속한 줄만 읽는다.
- CartItemResponse, CartItemsResponse를 추가했다. 신규 오류 코드는 없다.

## 검증

| 명령·시나리오 | 결과 | 근거 |
|---|---|---|
| `./gradlew clean compileJava compileTestJava test --tests '*cart*'` | 통과 | 장바구니 테스트 6개(CartServiceTest 4, CartItemTest 2): 담은 순서대로 반환, 빈 장바구니는 빈 목록 |
| Postman: 다른 회원으로 로그인해 조회 | 통과 | 빈 목록이 나와 다른 회원의 줄이 섞이지 않는다 |

## 연결과 남은 작업

- 응답에 상품 이름·정가·할인 단가·할인율·번개 드롭 남은 수량·품절 여부가 없다. 상품 도메인과 할인 조회가 들어오면 서비스의 TODO 자리에서 채우고 프론트 타입도 맞춘다.
- 수량 변경, 삭제 API는 별도 작업으로 한다. 줄 번호로 접근하므로 내 장바구니의 줄인지 확인한다.
