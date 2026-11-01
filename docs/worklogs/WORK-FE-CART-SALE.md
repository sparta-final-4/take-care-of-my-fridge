# WORK-FE-CART-SALE: 장바구니 할인 응답 반영

상태: 구현 완료 · 작성일: 2026-11-01 · 담당: 준혁

## 목표와 완료 기준
- 장바구니 조회 응답의 할인 정보가 `sale` 객체 하나로 바뀐 것(WORK-131)에 맞춘다.
- 장바구니 화면 표시는 바뀌지 않는다.

## 근거와 범위
- 백엔드 `CartItemResponse`: `saleType`, `discountRate`, `flashDropRemaining`, `flashDropLimit`, `saleEndsAt`을 `sale`(`SaleResponse`) 하나로 바꿈.
- 할인 행사·주문 정리(WORK-132, WORK-133)는 응답 모양이 바뀌지 않아 프론트 변경이 없다.

## 구현 결과
- 생성 클라이언트(oag)와 스펙 파일을 다시 만들었다. 바뀐 것은 `CartItemResponse` 하나다.
- 장바구니 화면은 응답의 `sale`을 그대로 쓴다. 따로 오던 할인 값을 `SaleResponse` 모양으로 묶던 코드를 없앴다.
- 주문하기 요청의 할인 구분은 `sale.type`에서 읽는다.

## 검증
- `pnpm typecheck`, `pnpm lint`, `pnpm build`: 통과.
- 장바구니 화면 375/768/1440: 할인 없는 상품은 실제 데이터로, 특가·번개 드롭은 응답에 할인 값을 넣어 할인 표시·번개 드롭 수량 한도·합계·주문 확인 화면 이동을 확인했다.
