# WORK-110: 환불 엔티티와 환불 신청 검증

상태: 완료 · 작성일: 2026-10-22 · 담당: 결제·환불

## 목표와 완료 기준

- 결제 완료 주문의 상품을 일부 수량 또는 전부 환불 신청할 수 있게 저장 구조와 검증을 만든다.
- 상품별 누적 환불 수량이 주문 수량을 넘지 않는다.
- 결제 테이블에는 환불 상태를 넣지 않고, 부분·전체 환불은 환불 기록으로 계산한다.

## 근거와 범위

- PR: #202(환불·환불 상품 엔티티), #212(주문 상품별 누적 환불 수량 조회), #214(환불 신청 검증과 요청 상태 저장). 모두 10/22.
- 환불은 결제에 붙인다(`refunds.payment_id`). 결제·주문이 1:N이라도 한 주문의 결제 완료 결제는 한 건이다.
- 관리자 승인 단계 없이 신청 즉시 PG 취소를 요청하기로 했다. 이 작업에서는 요청 상태로 저장하는 데까지 하고, PG 취소는 WORK-111에서 붙였다.

## 구현 결과

- `Refund`: `payment_id`, `refund_amount`, `reason_type`, `reason_detail`, `status`(`REQUESTED`·`COMPLETED`·`FAILED`), `portone_cancellation_id`, `fail_reason`, `refunded_at`(정산 기준 시각). 상태는 요청에서만 `complete`·`fail`로 바꾸고 아니면 `REFUND_009`.
- `RefundItem`: `refund_id`, `order_item_id`, `quantity`(1 이상), `unit_price`(주문 당시 단가 복사), `amount`(단가 × 수량, long으로 계산해 넘침 방지). UK(refund_id, order_item_id).
- `RefundItemRepository.findRefundedQuantitiesByOrderItemIds`: 주문 상품별 누적 환불 수량. 요청·완료 상태만 센다(`RefundStatus.COUNTED`). 실패한 환불 수량은 다시 신청할 수 있다.
- `RefundCommandService.request`
  - 주문 행을 잠그고 본인·결제 완료 주문인지 확인한다. 구매 확정은 `REFUND_010`, 그 밖은 `REFUND_003`.
  - 그 주문의 상품인지(`REFUND_004`), 일반 상품인지(`REFUND_011`), 누적 수량이 넘지 않는지(`REFUND_005`), 같은 상품이 중복되지 않는지(`C001`) 확인한다.
  - 금액을 계산해 환불·환불 상품을 요청 상태로 저장한다.
- 주문 행 잠금으로 같은 주문의 환불 신청이 하나씩 처리돼 누적 수량이 주문 수량을 넘지 않는다.

## 검증

| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `./gradlew test --tests '*Refund*'` | 로컬(Windows), MySQL 컨테이너, 2026-10-29 dev 기준 | 통과 | `RefundTest` 6건, `RefundItemTest` 4건, `RefundItemRepositoryTest` 4건, `RefundCommandServiceTest` 16건 포함 실패 0 |
| 일부 수량·여러 상품 신청 | 같은 환경 | 통과 | 단가 × 수량 합으로 요청 저장 |
| 남은 수량 초과, 한 번에 주문 수량 초과 | 같은 환경 | 통과 | `REFUND_005` |
| 구매 확정·결제 전·타인 주문, 다른 주문의 상품, 특가·번개 드롭, 같은 상품 중복 | 같은 환경 | 통과 | 각각 `REFUND_010`·`REFUND_003`·`ORDER_001`·`REFUND_004`·`REFUND_011`·`C001` |
| 누적 수량 집계 | 같은 환경 | 통과 | 요청·완료만 합산, 실패 제외, 다른 주문 상품 섞이지 않음 |

## 연결과 남은 작업

- PG 부분 취소와 완료 처리, 신청 API는 WORK-111.
