# WORK-039: 장바구니 담기

상태: 검증 완료 · 작성일: 2026-10-18 · 담당: 주문

## 목표와 완료 기준

- 로그인 회원이 `POST /api/cart-items`로 상품을 장바구니에 담는다.

## 근거와 범위

- 선행 PR: 장바구니 엔티티, 주문 엔티티, 할인 행사 엔티티.
- 회원 번호는 요청 본문이 아니라 로그인 토큰(`AuthPrincipal`)에서만 꺼낸다.
- 상품 존재·판매 여부, 재고, 번개 드롭 수량 한도는 상품 도메인이 들어온 뒤에 붙인다.

## 구현 결과

- CartRepository, CartItemRepository를 추가했다.
- CartController `POST /api/cart-items`: 새 줄이 만들어지면 201, 이미 담긴 상품에 수량만 더하면 200을 반환한다.
- AddCartItemRequest: `productId` 필수, `quantity`는 생략하면 1이고 1 미만이면 400(`C001`). Request가 `toEntity(Cart)`로 담긴 줄을 만든다.
- AddCartItemResponse: 담은 뒤의 `productId`, `quantity`. AddCartItemResult는 새 줄 여부(`created`)를 컨트롤러에 전달한다.
- CartService.addItem은 회원의 장바구니가 없으면 만들고, 같은 상품이 담겨 있으면 새 줄 없이 수량을 더한다.
- 신규 오류 코드는 없다.

## 검증

| 명령·시나리오 | 결과 | 근거 |
|---|---|---|
| `./gradlew clean compileJava compileTestJava test --tests '*cart*'` | 통과 | 장바구니 테스트 4개(CartServiceTest 2, CartItemTest 2) |
| Postman: 가입 → 로그인 → 담기 | 통과 | 새 줄 담기 201, 같은 상품 재요청 200 |

- 로컬에서 Postman으로 호출하려면 `JWT_SECRET`, `AUTH_COOKIE_SECURE=false`가 필요하다. 쓰기 요청은 매 요청 직전의 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더에 넣어야 한다(서버가 응답마다 쿠키를 새 값으로 바꾼다).

## 연결과 남은 작업

- 상품 판매 여부·재고·번개 드롭 한도(2개 이상 거부) 검증은 상품 도메인 연동 시 추가한다. 서비스에 TODO가 있다.
- 재고 검증을 붙일 때 수량 비교는 `새 수량 > 재고 - 기존 수량`으로 한다. `기존 + 새 수량`을 int로 먼저 더하면 2147483647 근처 값에서 오버플로로 검증을 통과할 수 있다.
- 조회, 수량 변경, 삭제 API는 별도 작업으로 한다.
