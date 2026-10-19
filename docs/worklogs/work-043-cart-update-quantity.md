# WORK-043: 장바구니 수량 변경

상태: 검증 완료 · 작성일: 2026-10-19 · 담당: 준혁

## 목표와 완료 기준

- 로그인 회원이 `PATCH /api/cart-items/{cartItemId}`로 본인 장바구니 줄의 수량을 바꾼다.

## 근거와 범위

- 선행 작업: WORK-039 장바구니 담기, WORK-041 장바구니 조회.
- 회원 번호는 로그인 토큰(`AuthPrincipal`)에서만 꺼낸다. 요청 본문은 `quantity`(절대값, 1 이상, 필수)다.
- 소유 확인은 쿼리에 회원 번호를 넣어 내 장바구니의 줄만 읽는 방식이다. 남의 줄과 없는 줄은 똑같이 404라 줄 번호가 존재하는지 드러나지 않는다.
- 상품은 엔티티만 있고 재고를 조회하는 서비스·리포지토리가 아직 없어 재고 초과와 번개 드롭 수량 한도 검증은 이번 범위에서 뺀다.

## 구현 결과

- CartController `PATCH /api/cart-items/{cartItemId}`: 200과 `data`(`cartItemId`, `quantity`)를 반환한다.
- CartService `changeQuantity`: 내 장바구니의 줄을 읽어 수량을 바꾼다. 영속 상태 엔티티의 `changeQuantity`로 바꾸고 `save()`는 부르지 않는다. 줄이 없으면 `CART_ITEM_NOT_FOUND`(404)다.
- CartItemRepository `findByIdAndCartMemberId`: 줄 번호와 회원 번호로 읽는다.
- ChangeCartItemQuantityRequest(`quantity` 필수, 1 이상), ChangeCartItemQuantityResponse를 추가했다. 신규 오류 코드는 없다.
- 테스트(Testcontainers MySQL):
  - `CartItemRepositoryTest`: `findByIdAndCartMemberId`가 내 줄은 찾고 다른 회원의 줄 번호로는 찾지 못한다.
  - `CartServiceTransactionTest`: 수량 변경이 DB에 반영된다. 다른 회원의 줄과 없는 줄은 `CART_ITEM_NOT_FOUND`이고 수량이 그대로다. 서비스의 트랜잭션 경계를 보려고 테스트 자체 트랜잭션을 끈다(`NOT_SUPPORTED`). 켜 두면 서비스에서 `@Transactional`이 빠져도 테스트 트랜잭션이 대신 커밋해 가려진다.

## 검증

| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `./gradlew clean compileJava compileTestJava test` | 로컬(맥) | 통과 | 컴파일 성공, 전체 테스트 실패 없음. 새 테스트 포함 |
| Postman: 장바구니 수량 변경 | 로컬 서버, docker compose MySQL | 통과 | 수량 변경 요청이 정상 동작 |
| 서비스 코드를 일부러 망가뜨려 테스트가 실패하는지 확인 | 로컬, 확인 뒤 원복 | 통과 | `changeQuantity`에서 `@Transactional`을 빼면 "수량 변경은 DB에 반영된다"가, 소유 확인을 줄 번호만으로 조회하도록 바꾸면 "다른 회원의 줄은 바꿀 수 없고 수량은 그대로다"가 실패 |

## 연결과 남은 작업

- 컨트롤러 계층 테스트(요청 검증 400, 응답 형식)는 이 작업에 없다.
- 재고 초과와 번개 드롭 한도 검증은 상품 도메인이 들어오면 서비스의 TODO 자리에서 채운다. 수량이 절대값이라 비교는 `새 수량 > 재고`다.
- 프론트 `cart.ts`의 수량 변경 연동은 별도 작업으로 한다.
- 삭제 API는 별도 작업으로 한다. 줄 번호로 접근하므로 같은 방식으로 내 장바구니의 줄인지 확인한다.
