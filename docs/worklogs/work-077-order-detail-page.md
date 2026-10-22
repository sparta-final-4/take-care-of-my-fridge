# WORK-077: 주문 상세 화면

상태: 검증 완료 · 작성일: 2026-10-22 · 담당: 준혁

## 목표와 완료 기준

- 주문 내역의 "상세 보기"로 주문 하나의 상품과 금액을 본다.
- 결제 완료 주문에는 구매 확정·환불 신청 자리가 있다.
- 주문 내역과 주문 상세 목록에 칸 이름 머리글이 있고, 머리글과 행의 칸이 맞는다.

## 근거와 범위

- 선행 작업: WORK-076 주문 상세 조회, WORK-074 주문 내역 화면. 이 화면의 PR은 WORK-076 PR 위에 쌓는다.
- 화면 상세는 `docs/requirements/screens.md`에 "주문 상세" 항목으로 추가했다.
- 구매 확정·환불 신청은 아직 API가 없어 비활성으로 두고, 올리면 "준비 중입니다."를 보여 준다. 주문 내역의 "상세 보기"를 연결하기 전과 같은 방식이다.
- 와이어프레임의 상품별 "환불"·"부분 환불" 칸과 "환불 상세 보기"는 두지 않았다. 환불 내역은 환불 상세 화면이 보여 준다.

## 구현 결과

- 경로는 `/mypage/orders/:orderId`이고 마이페이지 틀 안에 둔다. 주문 내역 아래 주소라 관계가 드러나고, 와이어프레임처럼 왼쪽 메뉴가 보인다.
- `MyPageLayout`의 메뉴 링크에서 `end`를 뺐다. 상세 주소에서도 "주문 내역" 메뉴가 켜진다. 다른 메뉴는 하위 주소가 없어 동작이 같다.
- `OrderDetailSection` (신규)
  - 위쪽: "주문 상세", 상태, 주문번호와 주문 시각(`YYYY-MM-DD HH:mm`), 구매 확정 주문이면 구매 확정일(`YYYY-MM-DD (ddd)`), "목록으로".
  - 주문 상품: 주문 확인 화면의 `OrderItemRow`를 그대로 쓴다.
  - 결제 금액: 상품 금액, 할인 금액, 결제 금액.
  - 구매 확정·환불: 결제 완료 주문에만 있다.
  - 없는 주문·남의 주문(`ORDER_001`)과 잘못된 주문 번호는 404 안내와 "목록으로", 그 밖의 실패는 다시 시도. 로딩은 `Skeleton`.
- 주문 내역의 "상세 보기"를 켜서 주문 상세로 보낸다. 상태 이름은 `domains/order/format.ts`로 옮겨 목록과 상세가 같이 쓴다(상세는 취소 상태 이름도 필요하다).
- 머리글
  - 주문 내역(주문번호·주문일·결제 금액·상태)은 lg부터, 주문 상품(상품·수량·단가·금액)은 md부터 보인다. 그보다 좁으면 행이 두 단이라 칸이 맞지 않아 숨긴다.
  - 머리글은 행과 같은 격자 클래스를 쓴다. 칸 너비가 `ch` 단위라 격자의 글자 크기·굵기가 다르면 칸 너비도 달라진다. 머리글 격자의 글자는 행과 같게 두고, 굵기는 칸 글자에만 줬다.
  - 주문 내역의 상태 칸은 너비가 내용에 따라 정해져 머리글("상태")과 행(상태 태그)의 칸이 달랐다. `$order-status-width`로 고정했다.
- 상품 칸 사이는 lg부터 넓혔다(`OrderItemRow`, 주문 확인 화면도 같다). 수량·단가·금액이 붙어 보였다.
- 주문 상세의 상품 목록은 구분선을 카드 끝에서 조금 띄우고, 내용은 다른 카드의 안쪽 여백과 같은 위치에서 시작해 카드끼리 열이 맞는다.
- 반응형: lg 미만은 구매 확정·환불이 결제 금액 아래로, md 미만은 상품이 두 단이다. lg 미만 버튼 높이는 44px이다. 좁은 화면에서 주문번호와 주문 시각은 둘 사이에서만 나뉜다.

<details><summary>변경 파일</summary>

- `app/paths.ts`, `app/router.tsx`: `mypageOrderDetail`
- `pages/mypage/MyPageOrderDetailPage.tsx` (신규)
- `domains/order/api.ts`: `getOrderDetail`
- `domains/order/queries.ts`: `orderQueries.detail`
- `domains/order/format.ts` (신규): 주문 상태 이름
- `domains/order/components/OrderDetailSection.tsx`, `OrderDetailSection.module.scss` (신규)
- `domains/order/components/OrderItemRow.tsx`, `OrderItemRow.module.scss`: `OrderItemHeader`, lg 칸 간격
- `domains/order/components/OrderListSection.tsx`, `OrderListSection.module.scss`: 머리글, "상세 보기" 연결, 상태 칸 고정
- `layout/MyPageLayout.tsx`: 메뉴 링크 `end` 제거
- `styles/_variables.scss`: `$order-status-width`
- `docs/requirements/screens.md`: 주문 상세 항목, 주문 내역 머리글·"상세 보기"

</details>

## 검증

| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `pnpm lint`, `format:check`, `typecheck`, `build`, `test` | 로컬(맥) | 통과 | 오류 없음 |
| 주문 내역 → 주문 상세 | 로컬 프론트, 로컬 백엔드, 상태별 주문과 상품 다섯 개 주문이 있는 로컬 데이터 | 통과 | "상세 보기"로 이동, 상품·할인 표시·금액이 주문 데이터와 같음, 결제 완료 주문만 구매 확정·환불 비활성 버튼, 구매 확정 주문은 구매 확정일 |
| 결제 대기·취소 주문 주소로 열기 | 같은 환경 | 통과 | 상태와 상품이 보이고 버튼 없음 |
| 다른 회원 주문·없는 주문 주소로 열기 | 같은 환경 | 통과 | "주문을 찾을 수 없습니다."와 "목록으로" |
| 머리글과 행의 칸 위치 | 같은 환경, 1440 / 768px | 통과 | 머리글과 행의 칸 좌우 위치가 같음, 상품 목록 내용이 다른 카드와 같은 위치에서 시작 |
| 화면 375 / 768 / 1440px | 같은 환경, 앱 브라우저 | 통과 | 가로 스크롤 없음, 상품 이름 잘림 없음, 모바일 버튼 높이 44px |

## 다음 작업

구매 확정 API가 생기면 "구매 확정"을 연결한다. 환불 신청은 환불 도메인 화면이 생기면 연결한다.
