# WORK-037: 주문 저장 모델

상태: 검증 대기 · 작성일: 2026-10-17 · 담당: 주문

## 목표와 완료 기준

- 주문과 주문 상품을 저장할 orders, order_items JPA 모델을 만든다.
- 주문 상태(결제 대기, 결제 완료, 취소, 구매 확정)와 상태 전이 규칙을 엔티티에 둔다.

## 근거와 범위

- 주문·할인 행사 도메인 DDL의 orders, order_items 정의.
- 회원, 상품, 할인 행사 상품은 다른 도메인이므로 연관관계 없이 번호로만 참조한다.
- 주문 생성·취소·결제 연동 서비스와 API는 다음 작업에서 추가한다.

## 구현 결과

- Order, OrderItem 엔티티와 OrderStatus, SaleType enum을 추가했다.
- Order는 결제 완료(결제 대기에서만), 취소(결제 대기에서만), 구매 확정(결제 완료에서만) 전이를 가진다. 어긋나면 BusinessException을 던진다.
- OrderItem은 번개 드롭 1인 1개 확인용 flash_drop_member_id를 비우는 메서드를 가진다.
- 신규 HTTP API와 오류 코드는 없다. 기존 INVALID_ORDER_STATUS, ORDER_NOT_CANCELABLE을 쓴다.

## 검증

- OrderTest: 상태 전이를 확인하는 단위 테스트를 추가했다. 로컬에서 통과했다.
- TakeCareOfMyFridgeBeApplicationTests(contextLoads): 로컬 MySQL로 통과했다.

## 연결과 남은 작업

- 결제 지연 후 되살리기(취소에서 결제 완료)는 결제 도메인 연동 때 정한다.
- 주문 생성 서비스를 다음 작은 작업에서 추가한다.
