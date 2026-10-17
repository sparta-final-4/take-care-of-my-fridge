# WORK-038: 할인 행사 저장 모델

상태: 검증 대기 · 작성일: 2026-10-17 · 담당: 주문

## 목표와 완료 기준

- 특가와 번개 드롭의 기간·상태·할인 상품을 저장할 sale_events, sale_event_items JPA 모델을 만든다.

## 근거와 범위

- 주문·할인 행사 도메인 DDL의 sale_events, sale_event_items 정의.
- 상품은 다른 도메인이므로 연관관계 없이 번호로만 참조한다.
- 행사 등록·수정·종료와 상태 전이, 남은 수량 차감은 서비스 작업에서 추가한다.

## 구현 결과

- domain/sale 패키지에 SaleEvent, SaleEventItem 엔티티와 SaleEventType, SaleEventStatus enum을 추가했다.
- 행사는 SCHEDULED로 시작한다. 번개 드롭 상품은 한정 수량을 받아 남은 수량을 같은 값으로 시작한다.
- 신규 HTTP API와 오류 코드는 없다.

## 검증

- TakeCareOfMyFridgeBeApplicationTests(contextLoads): 통과했다.

## 연결과 남은 작업

- 행사 상태 전이와 남은 수량 조건부 차감은 서비스 작업에서 정한다.
