-- 냉장고를 부탁해 · 구매 확정 수량 일회성 보정
-- 확정 수량(order_items.confirmed_quantity)은 #304부터 구매 확정 때 저장한다. 그 전에 확정된 주문은 이 값이 NULL이라
-- 확정 수량을 합산하는 할인 분석(요일 판매량·할인 효과 학습)에서 판매가 빠진다. 판매 예측은 주문 수량에서 완료 환불을 빼서 세므로 영향이 없다.
-- 구매 확정 서비스와 같은 규칙(주문 수량 - 완료된 환불 수량)으로 채운다. 확정 뒤에는 환불할 수 없으므로 지금 다시 세도 확정 당시 값과 같다.
-- 실행: 운영 DB에 한 번. 이미 채워진 행은 건드리지 않아 다시 실행해도 결과가 같다.

-- 1. 보정 대상 확인
SELECT COUNT(*) AS null_confirmed_items
FROM order_items oi
JOIN orders o ON o.id = oi.order_id
WHERE o.status = 'CONFIRMED' AND oi.confirmed_quantity IS NULL;

-- 2. 보정
UPDATE order_items oi
JOIN orders o ON o.id = oi.order_id
LEFT JOIN (
    SELECT ri.order_item_id, SUM(ri.quantity) AS refunded
    FROM refund_items ri
    JOIN refunds r ON r.id = ri.refund_id
    WHERE r.status = 'COMPLETED'
    GROUP BY ri.order_item_id
) completed ON completed.order_item_id = oi.id
SET oi.confirmed_quantity = oi.quantity - COALESCE(completed.refunded, 0)
WHERE o.status = 'CONFIRMED' AND oi.confirmed_quantity IS NULL;
