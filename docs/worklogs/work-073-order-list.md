# WORK-073: 내 주문 목록 조회

상태: 검증 완료 · 작성일: 2026-10-22 · 담당: 준혁

## 목표와 완료 기준

- 로그인한 회원이 자기 주문을 최신 주문부터 페이지로 나눠 본다.
- 남의 주문은 섞이지 않는다.
- 결제 대기·결제 완료·구매 확정 주문은 보이고, 취소된 주문은 보이지 않는다.

## 근거와 범위

- 선행 작업: WORK-069 주문 확인 조회, WORK-071 주문 취소.
- 결제 대기 주문도 목록에 넣었다. PG에서는 결제가 끝났는데 서버 확인(`/api/payments/confirm`이 "아직 진행 중"을 받은 경우, 웹훅 지연)이 늦어지면 주문은 결제 대기로 남는다. 그동안 카드사 문자는 왔는데 주문 내역에 주문이 없으면 사용자는 주문이 사라졌다고 느낀다.
- 취소된 주문은 결제가 끝나지 않은 주문이라 목록에서 뺐다.
- 탭을 닫아 버려진 결제 대기 주문도 만료되기 전까지 목록에 보인다. 결제 시도가 있는 주문만 고르려면 결제 테이블을 같이 읽어야 해서 주문 상태로만 거른다.

## 구현 결과

### API

`GET /api/orders?page=1&size=10` (`OrderController.getMyOrders`, `OrderQueryService.getMyOrders`)

| 쿼리 | 기본값 | 검증 |
|---|---|---|
| `page` | 1 | 1 이상, 1부터 센다 |
| `size` | 10 | 1~100 |

응답은 공통 `PageResponse`(`content`, `pageNumber`, `pageSize`, `totalElements`, `totalPages`, `isLast`)이고, `content` 한 건은 아래와 같다.

| 필드 | 값 |
|---|---|
| `orderId` | 주문 ID |
| `orderNumber` | 주문번호 |
| `orderedAt` | 주문 시각 |
| `payAmount` | 결제 금액(할인 후). 주문 확인 응답과 같은 이름 |
| `status` | `PENDING`, `PAID`, `CONFIRMED` |

- 정렬은 주문 ID 내림차순이다. ID는 주문 순서대로 늘어나므로 최신 주문이 먼저 오고, 같은 시각에 만든 주문도 순서가 정해진다.
- `orders` 테이블만 읽는다. 목록 조회와 개수 조회 두 번이고, 주문 상품은 읽지 않아 N+1이 없다.

### 구조

- `OrderListRequest` (신규): `page`, `size`와 기본값. 요리 기록 목록 요청과 같은 모양이다.
- `OrderSummaryResponse` (신규): 주문 내역 화면의 한 건. 주문 확인 응답(`OrderCheckoutResponse`)은 재사용하지 않았다.
- `OrderRepository.findByMemberIdAndStatusIn` (신규)
- `OrderQueryService.getMyOrders` (신규): 보일 상태 목록을 상수로 둔다. 목록은 주문 번호를 받지 않아 소유 확인 단계가 없고 주문 내역 화면만 쓰므로, 여러 기능이 같이 쓰는 `OrderReader`를 거치지 않고 리포지토리를 직접 부른다.
- 프론트 생성 클라이언트에 이 API만 더했다. 로컬 백엔드에서 받은 스펙에는 dev 스펙에 아직 없는 다른 도메인 API(요리 통계, 요리 기록 수정, 결제 준비)도 있어, dev 스펙에 목록 경로와 스키마 4개만 옮긴 뒤 `pnpm oag:generate:offline`으로 생성했다.

### 테스트

<details>
<summary>테스트 목록</summary>

- `OrderQueryServiceTransactionTest` (Testcontainers MySQL, DB에 저장한 주문을 서비스로 다시 읽어 확인)
  - 내 결제 대기·결제 완료·구매 확정 주문만 돌려주고, 취소된 주문과 남의 주문은 뺀다.
  - 최신 주문부터 페이지 크기만큼 나누고, 1부터 센 페이지 번호와 전체 개수·전체 페이지·마지막 페이지 여부가 맞다.
  - 주문이 없으면 빈 목록이다.
- `OrderControllerTest`
  - 쿼리 없이 부르면 1페이지 10개로 서비스를 부르고 200이다.
  - 페이지 크기가 100을 넘으면 400이고 서비스를 부르지 않는다.

</details>

## 검증

| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `./gradlew test` | 로컬(맥), dev fc771e9 기준 | 통과 | 전체 테스트 실패 없음. 새 테스트 포함 |
| 로컬 서버에 내 주문 목록 요청 | 로컬 서버, 로컬 시드 데이터, Postman CLI | 통과 | 200, 취소된 주문 없음, 최신 주문부터 |
| 생성 클라이언트 | `pnpm oag:generate:offline` | 통과 | 목록 경로와 스키마만 추가되고 기존 내용 변경 없음 |

## 다음 작업

주문 상세 조회를 만든다. 목록에 결제 대기 주문이 보이므로, 상세가 결제 대기 주문을 거절할지(주문 확인 화면으로 보냄) 그대로 보여 줄지 정한다.
