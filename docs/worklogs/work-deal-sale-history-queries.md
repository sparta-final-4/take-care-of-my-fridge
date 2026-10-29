# 실제 할인 행사 학습 이력 조회

상태: 검증 대기 · 작성일: 2026-10-29 · 담당: 관리자·분석 (제공: 행사·주문)

## 목표

24번 할인 효과 모델이 종료된 일반 특가와 해당 행사 상품의 확정 순판매 수량만 학습하도록 조회한다.

## 구현

`SaleEventItemRepository.findFinishedForTraining`은 종료 시각이 지난 일반 특가 상품을 읽는다. `OrderItemRepository`는 해당 행사 상품에 연결된 구매 확정 후 환불 차감 수량과 아직 결제 완료 상태인 주문 수를 각각 읽는다. 번개 드롭과 기준 시각 뒤의 확정 수량은 제외한다. API·DB 구조는 변경하지 않는다.

## 검증

`DealSaleHistoryQueriesTest`로 종료 시각, 행사 종류, 확정 수량과 미확정 주문을 MySQL Testcontainers에서 확인한다. 로컬 Docker가 없으면 PR의 백엔드 CI 결과를 확인한다.
