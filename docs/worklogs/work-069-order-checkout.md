# WORK-069: 주문 확인 조회

상태: 검증 완료 · 작성일: 2026-10-21 · 담당: 준혁

## 목표와 완료 기준

- 주문 확인 화면에서 새로고침하거나 다른 화면에 갔다가 돌아와도, 주문 번호로 결제 대기 주문을 다시 불러올 수 있다.
- 내 주문만 보인다. 남의 주문은 없는 주문과 똑같이 404다.
- 결제 대기 주문만 보인다. 결제 이후 주문은 주문 상세 화면이 따로 보여 준다.

## 근거와 범위

- 선행 작업: WORK-067 주문하기.
- 주문 확인 화면과 주문 상세 화면의 조회 API를 나눈다. 주문 상세는 환불·결제 정보까지 보여 줘야 해서, 하나로 두면 결제 직전 화면까지 그 조회에 기대게 된다.
- 응답 DTO는 화면마다 둔다. 주문하기와 주문 확인 조회는 같은 화면을 그리므로 같은 응답(`OrderCheckoutResponse`)을 쓴다.
- 주문 서비스는 쓰기는 기능별, 읽기는 하나(`OrderQueryService`)로 두고, 여러 기능이 같이 쓰는 조회는 `OrderReader`에 모은다.

## 구현 결과

### API

`GET /api/orders/{orderId}/checkout` (`OrderController.getCheckout`, `OrderQueryService.getCheckout`)

| 주문 상태 | 응답 | 화면이 할 일 |
|---|---|---|
| 결제 대기 | 200, 주문하기 응답과 같은 모양 | 주문 확인 화면을 보여 준다 |
| 결제 완료, 구매 확정 | 409 `ORDER_ALREADY_PAID` (신규, `ORDER_007`, "이미 결제가 완료된 주문입니다.") | 메시지를 띄우고 주문 내역으로 안내한다 |
| 취소 | 409 `ORDER_CANCELLED` (신규, `ORDER_008`, "취소된 주문입니다.") | 메시지를 띄우고 장바구니로 안내한다 |
| 남의 주문, 없는 주문 | 404 `ORDER_NOT_FOUND` | 없는 주문으로 처리한다 |

409가 나는 경우
- `ORDER_007`: 결제를 마친 뒤 뒤로 가기로 돌아오거나, 다른 탭에서 결제한 뒤 새로고침한 경우
- `ORDER_008`: 사용자가 취소했거나, 결제하기 없이 시간이 지나 만료된 주문으로 들어온 경우

결제가 실패한 주문은 결제 대기로 남으므로 200이고, 사용자는 다시 결제할 수 있다.

### 구조

- `OrderReader` (신규): 여러 주문 기능이 같이 쓰는 조회와 소유 확인
  - `getOwnedOrder(memberId, orderId)`: 주문 번호와 회원 번호를 함께 조건으로 찾는다. 없으면 404.
  - `getOrderItems(order)`: 주문 상품을 순서대로 읽는다.
  - 클래스 전체가 읽기 전용 트랜잭션이다. 부르는 서비스에 이미 트랜잭션이 있으면 그 안에서 실행된다.
- `OrderQueryService` (신규): 읽기 기능을 모은 서비스. 클래스 전체가 읽기 전용 트랜잭션이다.
- `Order.validatePending()` (신규): 결제 대기인지는 엔티티가 판단한다. 아니면 취소인지 결제 완료인지 나눠 예외를 던진다.
- `OrderCreateService`: 같은 멱등 키 재요청에서 주문 상품을 읽는 부분을 `OrderReader.getOrderItems`로 바꿨다.
- `OrderDetailResponse`를 `OrderCheckoutResponse`로 이름만 바꿨다. JSON 모양은 그대로다. 주문 상세 응답은 주문 상세 기능을 만들 때 따로 둔다.
- `OrderRepository.findByIdAndMemberId` 추가.
- 프론트 생성 클라이언트를 다시 생성했다. 주문 API가 스펙에 없던 상태였다. `clients.ts`에 `orderApi`를 추가했다.

### 테스트

<details>
<summary>테스트 목록</summary>

- `OrderQueryServiceTransactionTest` (Testcontainers MySQL에 저장한 주문으로 확인)
  - 내 결제 대기 주문은 주문 상품과 금액까지 돌려준다.
  - 남의 주문과 없는 주문은 404다.
  - 결제가 끝난 주문은 409 `ORDER_ALREADY_PAID`, 취소된 주문은 409 `ORDER_CANCELLED`이다.
- `OrderTest`: 결제 대기는 통과, 결제 완료·구매 확정은 `ORDER_ALREADY_PAID`, 취소는 `ORDER_CANCELLED`.
- `OrderControllerTest`
  - 로그인한 회원 번호와 주문 번호로 서비스를 부르고 200이다.
  - 남의 주문이거나 없는 주문이면 404다.
- 주문하기 테스트는 `OrderReader`로 바꾸고 응답 이름을 바꾼 뒤에도 그대로 통과한다.

</details>

## 검증

| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `./gradlew clean compileJava compileTestJava test` | 로컬(맥), dev 4ca7ca6 기준 | 통과 | 컴파일 성공, 전체 테스트 실패 없음. 새 테스트 포함 |
| 로컬 서버에 주문 확인 조회 요청 | 로컬 서버, 로컬 시드 데이터, Postman | 통과 | 주문하기 뒤 결제 대기 주문 200, 다른 회원으로 같은 주문 조회 404, 결제 완료로 바꾼 주문 409 `ORDER_007`, 취소로 바꾼 주문 409 `ORDER_008` |

## 다음 작업

주문 확인 화면 프론트를 만든다. 주문하기 응답의 `orderId`로 주문 확인 화면으로 넘어가고, 화면은 이 API로 그린다.
