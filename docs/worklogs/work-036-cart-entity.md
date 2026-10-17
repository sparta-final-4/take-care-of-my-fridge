# WORK-036: 장바구니 저장 모델

상태: 검증 대기 · 작성일: 2026-10-17 · 담당: 주문

## 목표와 완료 기준

- 회원의 장바구니와 담긴 상품을 저장할 carts, cart_items JPA 모델을 만든다.
- 회원당 장바구니는 하나이고, 한 장바구니에 같은 상품은 한 줄만 담긴다.

## 근거와 범위

- 주문·할인 행사 도메인 DDL의 carts, cart_items 정의.
- 회원과 상품은 다른 도메인이므로 연관관계 없이 member_id, product_id 번호로만 참조한다.
- 장바구니 담기·조회·수정 서비스와 API는 다음 작업에서 추가한다.

## 구현 결과

- Cart(member_id 유일), CartItem(cart_id, product_id 유일) 엔티티를 추가했다.
- CartItem은 같은 도메인의 Cart와만 LAZY 다대일로 연결하고 수량을 바꾸는 changeQuantity를 둔다.
- 신규 HTTP API와 오류 코드는 없다.

## 검증

- CartItemTest: 생성 값과 수량 변경을 확인하는 단위 테스트를 추가했다. 로컬에서 통과했다.
- TakeCareOfMyFridgeBeApplicationTests(contextLoads): 로컬 MySQL로 통과했다.

## 연결과 남은 작업

- 장바구니 담기 서비스를 다음 작은 작업에서 추가한다.
