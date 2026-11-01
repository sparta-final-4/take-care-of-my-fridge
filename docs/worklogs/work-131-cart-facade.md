# WORK-131: 장바구니 서비스의 상품·판매가 조합을 퍼사드로 분리

상태: 구현 완료 · 작성일: 2026-11-01 · 담당: 준혁

## 목표와 완료 기준

- 장바구니 서비스가 다른 도메인(상품, 할인 행사)을 직접 읽지 않고 장바구니 일만 하게 한다.
- 컨트롤러가 퍼사드를 거치는 다른 도메인과 같은 구조로 맞춘다.
- 오류 코드와 화면 동작은 바뀌지 않는다. 장바구니 조회 응답의 할인 정보만 상품 응답과 같은 `sale` 객체로 통일한다.

## 근거와 범위

- 선행 작업: WORK-082 장바구니 할인 반영, WORK-118 번개 드롭 수량 내리기.
- 기존에는 `CartService`가 상품 저장소와 판매가 조회를 직접 부르며 장바구니 저장까지 함께 했다. 상품 응답에 판매가를 붙이는 `ProductFacade`처럼 조합은 퍼사드가 맡는 구조가 팀의 방식이다.
- 담기와 수량 변경에 같은 모양으로 들어 있던 번개 드롭 한도·재고 검사가 두 곳에 있었다.

## 구현 결과

- `CartFacade`(새로 만듦): 담기, 조회, 수량 변경에서 상품과 판매가를 읽어 서비스에 넘기거나 응답에 붙인다.
- 상품 읽기와 판매 중지 검사: `CartFacade`가 상품 서비스(`ProductService`)로 상품을 읽고 판매 중지를 거른다. 상품 없음(`PRODUCT_NOT_FOUND`)과 판매 중지(`PRODUCT_NOT_ON_SALE`)의 구분은 그대로다.
- 오류 문구: 판매 중지와 재고 초과에도 구매 한도 초과처럼 상품 이름을 붙였다(`판매 중지 · 상품명`, `재고 초과 · 상품명`). 주문·할인 행사와 같은 형식이다.
- `CartQuantityLimit`(새로 만듦): 상품 이름, 재고, 번개 드롭 여부를 서비스에 넘기는 값이다.
- `CartService`: 상품 저장소와 판매가 조회 의존을 없앴다. 번개 드롭 한도와 재고 검사는 `validateQuantity` 하나로 모았고, 수량을 내릴 때 막지 않는 규칙과 `int` 넘침 방지는 그대로 유지한다. 합산은 `long`으로 한다.
- `CartController`: 담기, 조회, 수량 변경은 `CartFacade`를, 삭제는 합칠 것이 없어 `CartService`를 바로 부른다.
- `CartItemResponse`: `saleType`, `discountRate`, `flashDropRemaining`, `flashDropLimit`, `saleEndsAt`을 없애고 `sale`(`SaleResponse`, 할인이 없으면 null) 하나로 바꿨다. `listPrice`, `unitPrice`, `soldOut`은 그대로다. 생성 클라이언트(oag)와 프론트는 WORK-FE-CART-SALE에서 맞췄다.
- `PaymentCompletedEventListener`: 결제 완료 리스너 이름을 팀의 리스너 이름(`…EventListener`)에 맞췄다.
- DB 변경 없음.
- 문서: 장바구니 도메인 문서의 구현 근거와 구현 방향, API 명세의 조회 응답, 테스트 전략(CART-02, CART-05)의 테스트 이름을 갱신했다.

<details>
<summary>테스트 목록</summary>

- `CartServiceTest`: 장바구니·항목 저장, 수량 더하기, 번개 드롭 한도 거절, 재고 초과 거절, 재고가 줄어도 내리기 허용
- `CartFacadeTest`(새로 만듦): 조회 순서, 빈 장바구니, 진행 중 할인 반영과 품절 표시, 번개 드롭 담기 거절, 판매 중지·없는 상품의 오류 구분
- `CartControllerTest`: 삭제는 서비스, 나머지는 퍼사드를 부르도록 조정
- `CartServiceTransactionTest`, `PaymentCompletedEventListenerTest`: 새 구조에 맞춰 조정

</details>
