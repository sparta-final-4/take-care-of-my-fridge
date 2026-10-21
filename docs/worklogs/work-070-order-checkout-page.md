# WORK-070: 주문 확인 화면

상태: 검증 완료 · 작성일: 2026-10-21 · 담당: 준혁

## 목표와 완료 기준

- 장바구니에서 "주문하기"를 누르면 체크한 항목으로 주문을 만들고 주문 확인 화면으로 이동한다.
- 주문 확인 화면은 주문할 때 확정한 상품·금액을 보여 주고, 새로고침해도 같은 화면이다.
- 결제 완료·취소·없는 주문은 화면 대신 안내를 보여 준다.

## 근거와 범위

- 선행 작업: WORK-067 주문하기 API, WORK-068 장바구니 화면, WORK-069 주문 확인 조회 API.
- 화면 상세는 `docs/requirements/screens.md`의 "9 주문 확인"에 적었다.
- 결제 시작 API와 주문 취소 API가 아직 없어 "결제하기"·"취소하기"는 비활성으로 둔다.

## 구현 결과

- 주문 API는 생성 클라이언트 `orderApi`를 도메인 `api.ts`에서 `unwrap`으로 감싸 쓴다. 타입은 생성 타입 `OrderCheckoutResponse`·`OrderItemResponse`를 쓴다. CSRF는 `axiosInstance` 인터셉터가 붙인다.
- 멱등 키는 주문 시도 하나에 하나다.
  - 처음 누를 때 만들어 ref에 둔다.
  - 성공이나 4xx 응답을 받으면 버린다. 서버가 처리를 끝낸 시도라서다.
  - 응답이 없거나 5xx면 남겨 둔다. 주문이 만들어졌는지 알 수 없어, 다시 누르면 같은 키를 보내 서버가 처음 주문을 돌려주게 한다.
  - 체크한 항목이나 수량이 바뀌면 새로 만든다. 같은 키로 다른 내용을 보내면 서버가 `ORDER_006`으로 거절한다.
- 따닥은 버튼 loading으로 막고, 다시 그려지기 전에 두 번 눌린 경우는 같은 키가 받는다.
- 주문 확인 화면의 오류는 코드로 나눈다. `ORDER_007`은 주문 내역 안내, `ORDER_008`은 장바구니 안내, `ORDER_001`과 숫자가 아닌 주문 번호는 404 안내다.
- 장바구니 품절 상품의 "주문에서 빠집니다" 문구를 지웠다. 이미지 위 "품절" 표시와 흐린 표시로 충분하다고 판단했다.

<details><summary>변경 파일</summary>

- `domains/order/api.ts`: `createOrder`, `getOrderCheckout`
- `domains/order/queries.ts`: `orderQueries.checkout(orderId)`
- `domains/order/components/OrderCheckoutSection`: 주문 확인 화면 본체와 오류 안내
- `domains/order/components/OrderItemRow`: 주문 상품 행. md 미만은 두 단, md부터 한 행
- `pages/order/OrderCheckoutPage`, `ROUTES.orderCheckout`(`RequireAuth` 아래)
- `domains/cart/components/CartSummary`: "주문하기" 연결, 멱등 키
- `domains/cart/components/CartItemRow`: 품절 문구 삭제
- `common/api/errors.ts`: `isOutcomeUnknown`(응답 없음·5xx 판단)
- `styles/_variables.scss`: `$order-quantity-width`
- `docs/requirements/screens.md`: "9 주문 확인" 추가, 장바구니 "주문하기"·품절 항목 갱신

</details>

## 검증

| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `pnpm lint`, `format:check`, `typecheck`, `build`, `test` | 로컬(맥) | 통과 | 오류 없음 |
| 화면 375 / 768 / 1440px | 로컬 프론트, 로컬 백엔드, docker MySQL | 통과 | 가로 스크롤 없음. lg 미만은 결제할 금액이 목록 아래로 |
| 주문하기 → 주문 확인 화면 | 같은 환경 | 통과 | 주문 확인 화면으로 이동, 새로고침해도 같은 화면 |
| 재고 0으로 바꾼 뒤 주문하기 | 같은 환경, 화면을 띄운 채 DB에서 재고 변경 | 통과 | 실패 알림, 주문 생성 없음, 새로고침 후 품절 표시 |
| 없는 주문 번호 | 같은 환경 | 통과 | 404 안내 |
| 결제 완료·취소 주문 안내 | - | 미실행 | 결제·취소 API가 없어 주문 상태를 바꿀 화면 경로가 없다 |
| 응답을 못 받은 뒤 다시 주문하기 | - | 미실행 | 같은 키가 다시 가는지 네트워크 차단으로 확인하지 않았다 |

## 연결과 남은 작업

- 다음은 주문 취소다. "취소하기"와 함께, 주문 확인 화면에서 앱 안으로 나갈 때 "나가면 주문이 취소됩니다" 확인 창을 붙인다. 탭 닫기·새로고침에는 묻지 않고, 남은 주문은 주문 만료 스케줄러가 5분 뒤 취소한다.
- 주문 확인 조회는 전역 재시도 설정(`retry: 1`) 때문에 409·404도 한 번 더 요청해, 안내가 조금 늦게 뜬다.
- 할인 표시는 주문할 때 할인 구분이 정해지면 그대로 보인다.
