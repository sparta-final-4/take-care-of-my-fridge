# WORK-106: 결제 대기 만료와 같은 주문 재결제

상태: 검증 대기 · 작성일: 2026-10-21 · 담당: 결제·환불

## 목표와 완료 기준

- 결제창을 띄운 뒤 끝내지 않은 결제 시도를 일정 시간 뒤 정리한다. 만료 뒤에 PG에서 결제되면 받지 않고 취소한다.
- 결제가 실패해도 같은 주문으로 다시 결제할 수 있게 한다(결제·주문 1:N).
- 같은 주문에 결제가 두 번 승인되는 일(이중 결제)을 막는다.

## 근거와 범위

- PR: #167(결제 대기 만료, 10/21), #180(결제·주문 1:N 전환, 10/21).
- 처음에는 주문 하나에 결제 하나(1:1)였다. 결제가 실패하면 주문도 다시 만들어야 해서, 결제 실패는 결제 시도 하나만 실패로 두고 주문은 결제 대기로 남기기로 했다.
- 결제는 주문 취소·재고를 건드리지 않는다. 재고 복구는 주문 도메인이 맡는다.
- 재결제 규칙: 서버는 "다른 탭에서 결제 중"과 "브라우저 종료로 버려진 창"을 구분할 수 없다. 버려진 창 때문에 재결제가 막히지 않도록 가장 최근에 연 결제창만 인정하고, 이전 창에서 나중에 결제되면 받지 않고 취소한다.

## 구현 결과

- 만료
  - `payments.expires_at`(생성 10분 뒤)과 인덱스 `(status, expires_at)`.
  - `Payment.isPaidAfterExpiry`: PG 결제 시각이 만료 시각을 넘었는지. 같으면 기한 내.
  - `PaymentFacade.decidePaid`: PG 결제 완료 건의 승인·금액 불일치·만료 판단을 한 곳으로 모았다. 만료 후 결제는 `TIMEOUT` 실패 후 PG 취소, `PAYMENT_006`(409). 금액이나 결제 시각을 확인할 수 없으면 판단하지 않는다.
  - 확정 요청은 PG 취소 실패를 삼키고 거절 이유로 응답한다. 웹훅은 예외를 던져 재전송으로 다시 시도한다.
  - `PaymentTimeoutScheduler.expireOverduePayments`: 1분마다 만료된 결제 대기를 id 커서로 100건씩 읽는다. PG에서 결제가 끝났으면 웹훅과 같은 규칙으로 처리하고, 아니면 `TIMEOUT`. 한 건이 실패해도 나머지는 계속한다.
- 1:N 재결제
  - `payments.order_id` UNIQUE를 없앴다.
  - 결제 준비 API `POST /api/orders/{orderId}/payments`: 응답은 orderId, portonePaymentId, orderName, amount, expiresAt.
  - 남은 결제 대기 건은 PG를 확인해 승인하거나 `SUPERSEDED`로 정리한 뒤 새로 만든다. 결제 대기가 남아 있으면 재사용하지 않고 `PAYMENT_008`로 거절한다(만료됐거나 곧 만료될 결제가 프론트로 내려가지 않게).
  - 승인·실패·생성 모두 주문 → 결제 순서로 잠근다. 주문 잠금이 트랜잭션의 첫 조회가 되게 해 `refresh`가 잠근 뒤 최신 값을 읽게 했다.
  - 승인 때 주문이 결제 대기가 아니면 `ORDER_NOT_PAYABLE`로 실패 처리하고 PG를 취소한다(`PAYMENT_007`).
- 새 FailReason: `TIMEOUT`, `ORDER_NOT_PAYABLE`, `SUPERSEDED`. 새 ErrorCode: `PAYMENT_006`~`PAYMENT_008`.

## 검증

| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| 만료 관련 `PaymentFacadeTest`, `PaymentTimeoutSchedulerTest` | 로컬(Windows), 2026-10-29 dev 기준 | 통과 | 만료 시각 이후 결제는 `TIMEOUT` 후 취소, 같은 시각은 승인. 스케줄러는 한 건 실패해도 계속, id 커서로 이어 읽음 |
| 결제 준비 규칙 `PaymentFacadeTest` | 같은 환경 | 통과 | PG 미결제면 `SUPERSEDED` 후 새로 생성, PG 결제됨이면 승인하고 생성 거절, PG 조회 실패면 생성 안 함 |
| `PaymentCommandServiceConcurrencyTest` | 같은 환경 | 미실행 | Docker가 꺼져 MySQL 컨테이너를 띄우지 못함. 다시 돌려 확인 필요 |
| 같은 주문 결제 두 건 동시 승인 | GitHub Actions BE CI(#180 머지 조건) | 통과 | 한 건만 결제 완료, 나머지 `ORDER_NOT_PAYABLE` |
| 결제 준비 동시 요청 | GitHub Actions BE CI(#180 머지 조건) | 통과 | 결제 대기 한 건만 생성, 나머지 `PAYMENT_008` |

## 연결과 남은 작업

- 결제를 시도했지만 끝내지 않은 결제 대기 주문의 취소·재고 복구는 주문 담당이 #309에서 같은 스케줄러(`cancelAbandonedOrders`)로 더했다.
- 보상 취소 실패의 재시도는 WORK-108.
