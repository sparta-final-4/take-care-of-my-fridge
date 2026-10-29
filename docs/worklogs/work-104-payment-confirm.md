# WORK-104: 결제 엔티티와 결제 확정

상태: 완료 · 작성일: 2026-10-20 · 담당: 결제·환불

## 목표와 완료 기준

- 결제 시도를 저장하고, 결제창이 끝난 뒤 서버가 PG 결제를 직접 확인해야만 결제 완료로 바꾼다.
- 브라우저가 보내는 결제 성공 결과나 금액은 믿지 않는다. 금액이 다르면 받지 않고 PG 결제를 취소한다.
- 확정을 두 번 눌러도 상태는 한 번만 바뀐다.

## 근거와 범위

- PR: #86(결제 엔티티·DTO, 10/19), #136(결제 서비스·PortOne 클라이언트, 10/19), #152(결제 컨트롤러, 10/20).
- 결제 금액은 결제를 만들 때 주문 금액으로 정한다. 확정 요청에는 금액을 받지 않는다.
- PG 호출(조회·취소)은 트랜잭션 밖에서 하고, 상태 변경만 짧은 트랜잭션으로 묶는다. PG 응답을 기다리는 동안 DB 연결과 잠금을 잡고 있지 않게 하기 위해서다.
- 결제창 닫기는 "실패"로 이름을 맞췄다(`cancel` → `fail`). 결제 완료 뒤의 취소(환불)와 헷갈리지 않게 하기 위해서다.

## 구현 결과

- `Payment` 엔티티: `order_id`, `portone_payment_id`(UNIQUE), `amount`, `status`(`READY`·`PAID`·`FAILED`), `paid_at`, `fail_reason`. 상태는 `complete`·`fail`로만 바꾸고 결제 대기가 아니면 `PAYMENT_003`.
- `PaymentFacade.confirmPayment`: 본인 결제인지, 요청의 주문 ID가 결제의 주문과 같은지 확인 → 이미 결제 완료면 같은 응답 → PG 조회 → 결제 완료가 아니면 `PAYMENT_004`(PG 실패면 `PG_FAILED`로 실패 처리) → 금액 비교 → 승인.
- 금액이 다르면 `AMOUNT_MISMATCH`로 실패 처리하고, 실패 처리가 실제로 반영된 경우에만 PG를 전액 취소한다. 다른 요청이 이미 처리했으면 취소하지 않아 "DB 결제 완료 + PG 취소" 상태를 막는다. PG 응답에 금액이 없으면 판단하지 않고 `PAYMENT_004`.
- `PaymentCommandService.approve`·`fail`: 결제 행을 비관적 잠금으로 읽고 상태를 다시 확인한 뒤 바꾼다. 이미 결제 완료면 같은 결과를 돌려준다.
- `PaymentGateway` 포트와 `PortOneClient`: 결제 조회(없으면 빈 값), 결제 취소(전액·부분).
- API: `POST /api/payments/confirm`, `POST /api/payments/{portonePaymentId}/fail`.
- 새 ErrorCode: `PAYMENT_001`~`PAYMENT_005`.

## 검증

| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `./gradlew test --tests '*Payment*' --tests '*PortOne*'` | 로컬(Windows), 2026-10-29 dev 기준 | 통과 | `PaymentFacadeTest` 58건, `PaymentCommandServiceTest` 14건, `PaymentControllerTest` 10건, `PaymentServiceTest` 6건, `PaymentTest` 2건, `PortOneClientTest` 12건 |
| 금액 불일치 확정 | 같은 환경 | 통과 | `AMOUNT_MISMATCH` 실패 후 PG 취소. 다른 요청이 먼저 처리했으면 취소 안 함, PG 취소가 실패해도 실패 기록은 남음 |
| 타인 결제·다른 주문의 결제 ID로 확정 | 같은 환경 | 통과 | `ORDER_003`·`C001`, PG 조회 안 함 |
| 이미 결제 완료된 결제 재확정 | 같은 환경 | 통과 | PG 조회 없이 같은 응답 |

## 연결과 남은 작업

- 결제 준비 API와 재결제 규칙은 WORK-106, 웹훅은 WORK-105에서 더했다.
- 확정 때 PG 응답의 통화·채널 종류는 확인하지 않는다(테스트 채널이라 후순위).
