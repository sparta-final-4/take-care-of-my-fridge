# WORK-055: 장바구니 삭제

상태: 검증 완료 · 작성일: 2026-10-19 · 담당: 준혁

## 목표와 완료 기준

- 로그인 회원이 `DELETE /api/cart-items?cartItemIds=11,12`로 본인 장바구니 줄을 지운다. 줄마다 삭제, 선택 삭제, 전체 삭제를 번호 목록 하나로 처리한다.

## 근거와 범위

- 선행 작업: WORK-039 장바구니 담기, WORK-041 장바구니 조회, WORK-043 장바구니 수량 변경.
- 회원 번호는 로그인 토큰(`AuthPrincipal`)에서만 꺼낸다. 요청은 쿼리 `cartItemIds`(쉼표로 구분, 1개 이상)다.
- 선택 삭제와 전체 삭제는 따로 두지 않는다. 전체 삭제는 화면이 모든 줄의 번호를 보낸다.
- 남의 줄이나 없는 번호는 오류 없이 무시하고, 같은 요청을 두 번 보내도 같은 결과다.
- 삭제 이력은 남기지 않는다.

## 구현 결과

- CartController `DELETE /api/cart-items`: 200과 `data` 없음을 반환한다. 요청은 `RemoveCartItemsRequest`를 `@Valid @ModelAttribute`로 받는다. `@RequestParam`은 `GlobalExceptionHandler`가 누락 예외를 따로 처리하지 않아 쓰지 않았고, 이 방식이면 기존 검증 처리(400, `INVALID_INPUT_VALUE`)를 그대로 쓴다.
- CartService `removeItems`, CartItemRepository `deleteAllByIdsAndMemberId`: 회원 조건이 들어간 `@Modifying` 쿼리 한 번으로 내 장바구니의 줄만 지운다. 실행 뒤 영속성 컨텍스트를 비운다.
- RemoveCartItemsRequest(`cartItemIds` 1개 이상)를 추가했다. 신규 오류 코드는 없다.
- 테스트(Testcontainers MySQL):
  - `CartItemRepositoryTest`: 내 장바구니의 줄만 지우고 다른 회원의 줄은 그대로 두며, 목록에 없는 줄은 지우지 않는다.
  - `CartServiceTransactionTest`: 목록의 내 줄이 DB에서 지워지고 나머지는 남는다. 남의 줄과 없는 번호가 섞여도 내 줄만 지우고 오류가 없다. 같은 요청을 두 번 보내도 같은 결과다.
  - `CartControllerTest`(`@WebMvcTest`): 번호가 서비스에 그대로 전달된다. 번호가 없거나 비었거나 숫자가 아니면 400이다.

## 검증

| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `./gradlew clean compileJava compileTestJava test` | 로컬(맥) | 통과 | 컴파일 성공, 전체 테스트 실패 없음. 새 테스트 포함 |
| Postman: 장바구니 삭제 | 로컬 서버, docker compose MySQL | 통과 | 삭제 요청이 정상 동작 |
| 서비스·쿼리를 일부러 망가뜨려 테스트가 실패하는지 확인 | 로컬, 확인 뒤 원복 | 통과 | `removeItems`에서 `@Transactional`을 빼면 서비스 테스트가, 삭제 쿼리의 회원 조건을 항상 참으로 바꾸면 "다른 회원" 테스트(리포지토리·서비스)가 실패 |

## 연결과 남은 작업

- 번호 목록은 쿼리스트링으로 보낸다. 본문(JSON)으로 보내는 안도 검토했으나, 소유 확인이 서버 쿼리에서 이뤄져 보내는 위치에 따른 보안 차이가 없고 팀 코드에 `DELETE` 본문 선례가 없어 유지했다.
- 장바구니 줄 수에 상한이 없어 번호가 매우 많으면 주소 길이에 걸릴 수 있다. 필요해지면 전달 방식을 바꾼다.
- 프론트 `cart.ts`의 삭제 연동은 별도 작업으로 한다.
