# WORK-102: 구매 확정 검증과 확정 수량

상태: 검증 대기 · 작성일: 2026-10-27 · 담당: 준혁

## 목표와 완료 기준

- 환불이 진행 중인 주문은 구매 확정하지 못한다. 확정된 주문이 뒤늦게 환불되고 재고가 되돌아가는 일을 막는다.
- 구매 확정할 때 주문 상품마다 확정 수량(주문 수량 − 완료된 환불 수량)을 저장한다.
- 주문 상세는 구매 확정된 주문의 확정 수량을 보여 준다.

## 근거와 범위

- 문제: 구매 확정은 주문 상태(결제 완료)만 보고 확정했다. 환불이 요청 상태일 때 확정하면, 환불 완료(`RefundCommandService.complete`)가 주문을 다시 확인하지 않아 확정된 주문이 환불되고 재고가 되돌아갔다.
- 구매 확정은 주문 행을 잠근 뒤 요청 상태(`REQUESTED`) 환불이 있으면 거절한다. 실패한 환불은 막지 않는다.
- 환불 신청도 같은 주문 행을 잠그므로 구매 확정과 하나씩 순서대로 처리된다. 환불 상태는 요청 상태에서만 바뀌므로, 잠근 뒤 요청 상태 환불이 없으면 확정이 끝날 때까지 새로 완료될 환불도 없다.
- 확정 뒤에는 환불 신청이 거절되므로 확정 수량은 확정할 때 한 번 계산하면 이후 바뀌지 않는다. 할인 상품은 환불할 수 없어 주문 수량과 같다.
- 주문 도메인은 환불 데이터를 환불 도메인의 조회 메서드로 읽는다. 어떤 환불 상태를 셀지는 환불 도메인에 둔다.

## 구현 결과

- `OrderConfirmService.confirm`: 주문 행을 잠그고, 이미 확정된 주문이면 그대로 응답한다. 요청 상태 환불이 있으면 `ORDER_011`로 거절하고, 아니면 확정한 뒤 주문 상품마다 확정 수량을 저장한다.
- `RefundQueryService`에 조회 메서드 두 개를 더했다.
  - `hasRequestedRefund(orderId)`: 요청 상태 환불이 있는지
  - `getCompletedRefundQuantities(orderItemIds)`: 주문 상품별 완료된 환불 수량의 합
- `OrderItem`에 `confirmedQuantity`(`confirmed_quantity`)와 `confirmQuantity(completedRefundQuantity)`를 더했다. 확정 전에는 null이라 `Integer`로 둔다.
- 새 ErrorCode: `ORDER_REFUND_IN_PROGRESS`(`ORDER_011`, 409, 환불이 진행 중인 주문은 구매 확정할 수 없습니다).
- 주문 상세 응답의 주문 상품을 `OrderDetailItemResponse`로 바꾸고 `confirmedQuantity`(확정 전 null)를 더했다. 주문 확인 조회 응답은 그대로 `OrderItemResponse`를 쓴다.

<details><summary>DB 변경과 기존 데이터 채우기</summary>

`order_items`에 열을 더한다. 서버 시작 시 JPA 스키마 갱신으로 추가된다.

```sql
ALTER TABLE order_items
    ADD COLUMN confirmed_quantity INT NULL COMMENT '구매 확정 수량. 주문 수량에서 완료된 환불 수량을 뺀 값. 확정 전에는 NULL';
```

이미 구매 확정된 주문은 열이 추가된 뒤 한 번 채운다.

```sql
UPDATE order_items oi
JOIN orders o ON o.id = oi.order_id
LEFT JOIN (
    SELECT ri.order_item_id, SUM(ri.quantity) AS qty
    FROM refund_items ri
    JOIN refunds r ON r.id = ri.refund_id
    WHERE r.status = 'COMPLETED'
    GROUP BY ri.order_item_id
) c ON c.order_item_id = oi.id
SET oi.confirmed_quantity = oi.quantity - COALESCE(c.qty, 0)
WHERE o.status = 'CONFIRMED' AND oi.confirmed_quantity IS NULL;
```

</details>

## 검증

| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `sh ./gradlew test --tests '*OrderConfirmServiceTransactionTest' --tests '*OrderQueryServiceTransactionTest'` | 로컬(맥), MySQL 컨테이너 | 통과 | 두 클래스 실패·오류·건너뜀 없음 |
| 완료 환불과 실패 환불이 있는 주문의 구매 확정 | 같은 환경, 확정 뒤 주문 상품을 다시 읽음 | 통과 | 일반 상품 5개에 완료 2개·실패 1개면 확정 수량 3, 할인 상품은 주문 수량 그대로 |
| 환불 없는 주문의 구매 확정 | 같은 환경 | 통과 | 확정 수량이 주문 수량과 같음 |
| 요청 상태 환불이 있는 주문의 구매 확정 | 같은 환경 | 통과 | `ORDER_011`로 거절, 주문은 결제 완료 그대로이고 확정 시각과 확정 수량은 null |
| 구매 확정과 환불 신청을 주문 10건에 동시에 보냄 | 같은 환경 | 통과 | 주문마다 하나만 성공. 확정된 주문에는 요청·완료 환불이 없고 확정 수량이 주문 수량, 결제 완료로 남은 주문은 요청 상태 환불 1건에 확정 수량 null |
| 주문 상세의 확정 수량 | 같은 환경 | 통과 | 결제 완료 주문은 null, 구매 확정된 주문은 저장된 확정 수량 |
| 기존 데이터 채우기 쿼리 | - | 미실행 | 열이 추가된 dev DB에서 실행한다 |

## 연결과 남은 작업

- 일별 상품 판매량 조회를 확정 수량의 합으로 바꿔, 환불을 따로 빼지 않고 순판매 수량을 돌려준다.
