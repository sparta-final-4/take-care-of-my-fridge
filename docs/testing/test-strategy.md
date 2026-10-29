# 테스트 전략

상태: 계획 · 테스트 코드·실행 결과 없음 · 근거: S1, S4, S5

## 검증 범위

단위 테스트는 정책과 계산을, 통합 테스트는 DB 제약·트랜잭션·권한·연동 경계를, 화면 검증은 사용자 흐름과 상태 표시를 확인합니다. 동일 구현을 복제하는 테스트는 피합니다. 외부 결제·AI는 통제된 테스트 환경과 대역을 구분합니다.

## 개인 담당 필수 시나리오

| ID | 우선순위 | 조건·행동 | 기대 결과 |
|---|---|---|---|
| AUTH-01 | P0 | 가입 요청에 ADMIN 포함 | USER 고정 또는 거절, 권한 상승 없음 |
| AUTH-02 | P0 | 중복 확인 뒤 동시 가입 | 이메일·닉네임 중복 계정 생성 차단 |
| AUTH-03 | P0 | 틀린 비밀번호 또는 없는 이메일 | 동일 실패 문구, 토큰 미발급 |
| AUTH-04 | P0 | 정지 계정 로그인 | 토큰 미발급, 정지 안내 정책 준수 |
| AUTH-05 | P0 | 로그아웃 후 갱신 수단 재사용 | 재발급 거부 |
| AUTH-06 | P0 | 이전 세션 요청이 뒤늦게 실패 | 새 세션 종료·잘못된 알림 없음 |
| AUTH-07 | P0 | 비밀번호 변경 | 현재 세션 유지, 다른 기기 세션 폐기 |
| AUTH-08 | P0 | 외부 origin의 복귀 URL | 외부 이동 차단 |
| ADMIN-01 | P0 | USER가 관리자 쓰기 호출 | 403, 데이터 불변, 관리자 작업 이력에 DENIED 기록 |
| ADMIN-02 | P0 | 정지 회원 승격 | 거부, 권한 불변 |
| PRODUCT-01 | P0 | 가격 0 이하·필수값 누락 | 등록 실패, 계약된 필드 오류 |
| PRODUCT-02 | P0 | 상품 논리 삭제·정상가 변경 | 기존 주문 스냅샷 유지 |
| AUDIT-01 | P0 | 변경 실패·권한 거부 | 실패해도 관리자 작업 이력 보존, 민감 값 제외 |
| FORECAST-01 | P1 | 주 시작 후 예측 변경 | 차단, 기존 예측 고정 |
| FORECAST-02 | P1 | 예측·기준선 평가 | 같은 기간·모집단·실적 기준으로 비교 |
| DEAL-01 | P1 | 후보 분석·검토 저장 | 실제 상품·행사 가격 불변 |
| DEAL-02 | P1 | 미검토 후보가 남음 | 저장 가능, 검토 상태 보존 |

Q-01~Q-17에 의존하는 테스트는 기대값을 합의한 뒤 구현합니다.

## 장바구니 필수 시나리오

| ID | 우선순위 | 조건·행동 | 기대 결과 | 대응 테스트 |
|---|---|---|---|---|
| CART-01 | P0 | 재고를 넘는 수량 담기·올리기(기존 수량과 합산, int 넘침 포함) | 거절, 수량 불변, 처음 담다 실패하면 장바구니도 남지 않음 | `CartServiceTransactionTest` |
| CART-02 | P0 | 번개 드롭 상품을 이미 담았는데 더 담기·수량 2로 바꾸기 | `CART_003`으로 거절, 수량 불변 | `CartServiceTest` |
| CART-03 | P0 | 타인 항목의 수량 변경·삭제 | 변경은 404, 삭제는 내 항목만 지움 | `CartServiceTransactionTest`, `CartItemRepositoryTest` |
| CART-04 | P0 | 담은 뒤 재고가 줄거나 상품이 판매 중지 | 내리기만 허용, 올리기·판매 중지 변경 거절, 조회는 품절 표시 | `CartServiceTransactionTest` |
| CART-05 | P1 | 진행 중 할인이 있는 상품 조회 | 항목마다 할인가·구분·종료 시각, 번개 드롭 남은 수량 0이면 그 항목만 품절 | `CartServiceTest` |
| CART-06 | P1 | 같은 삭제 요청 두 번, 남의 항목·없는 번호 섞기 | 내 항목만 지우고 오류 없음 | `CartServiceTransactionTest` |

## 주문 필수 시나리오

| ID | 우선순위 | 조건·행동 | 기대 결과 | 대응 테스트 |
|---|---|---|---|---|
| ORDER-01 | P0 | 여러 회원이 같은 상품을 동시에 주문 | 재고만큼만 성공, 나머지는 재고 부족으로 거절 | `OrderCreateServiceTransactionTest` |
| ORDER-02 | P0 | 한 항목이라도 재고 부족·가격 불일치 | 모두 롤백, 다른 항목 재고도 그대로 | `OrderCreateServiceTransactionTest`, `OrderCreateServiceSaleTransactionTest` |
| ORDER-03 | P0 | 같은 멱등 키로 반복·동시에 주문하기 | 주문 하나만 생기고 모두 같은 주문을 받음, 다른 상품이면 `ORDER_006` | `OrderCreateServiceTransactionTest` |
| ORDER-04 | P0 | 같은 주문을 동시에 여러 번 취소 | 하나만 지우고 나머지는 404, 재고는 한 번만 복구 | `OrderCancelServiceTransactionTest` |
| ORDER-05 | P0 | 결제 완료 주문 취소, 결제 대기 주문 구매 확정 | 409, 상태·재고 불변 | `OrderCancelServiceTransactionTest`, `OrderConfirmServiceTransactionTest` |
| ORDER-06 | P0 | 타인 주문 조회·취소·확정 | 없는 주문과 같은 404, 상태 불변 | `OrderQueryServiceTransactionTest`, `OrderCancelServiceTransactionTest`, `OrderConfirmServiceTransactionTest` |
| ORDER-07 | P0 | 한정 수량보다 많은 회원이 번개 드롭을 동시에 주문 | 한정 수량만큼만 성공, 남은 수량 0 | `OrderCreateServiceSaleTransactionTest` |
| ORDER-08 | P0 | 번개 드롭을 이미 산 회원이 다시 주문, 취소 뒤 다시 주문 | 다시 주문은 `CART_003`, 취소하면 남은 수량이 돌아와 주문 가능 | `OrderCreateServiceSaleTransactionTest` |
| ORDER-09 | P1 | 주문 목록 조회 | 결제 대기·결제 완료·구매 확정만 최신순, 취소 제외, 1부터 센 페이지 | `OrderQueryServiceTransactionTest` |
| ORDER-10 | P1 | 주문번호 생성 | 20자리 숫자, 동시에 만들어도 안 겹침, 겹치면 `ORDER_005` | `OrderNumberGeneratorTest`, `OrderCreateServiceConstraintTest` |
| ORDER-11 | P0 | 결제를 시도한 결제 대기 주문 취소, 취소하기와 결제하기 동시 | 결제 시도가 있으면 `ORDER_012`로 거절하고 주문·재고 불변. 동시면 하나만 성공하고 결제가 붙은 주문은 지워지지 않음 | `OrderCancelServiceTransactionTest` |
| ORDER-12 | P0 | 주문 만료, 취소하기와 주문 만료 동시 | 생성 5분이 지난 결제 시도 없는 결제 대기 주문만 지움. 동시면 재고는 한 번만 복구 | `OrderCancelServiceTransactionTest`, `OrderExpireSchedulerTest` |
| ORDER-13 | P0 | 결제 정리, 결제 정리와 결제하기·주문 취소 동시 | 진행 중 결제가 없고 마지막 결제 뒤 10분이 지난 결제 대기 주문만 취소 상태로 바꾸고 재고 복구, 결제 행 유지. 동시면 재고는 많아야 한 번 복구 | `OrderCancelServiceTransactionTest`, `PaymentTimeoutSchedulerTest` |

## 할인 행사 필수 시나리오

| ID | 우선순위 | 조건·행동 | 기대 결과 | 대응 테스트 |
|---|---|---|---|---|
| SALE-01 | P0 | 같은 상품을 동시에 5번 등록 | 행사 하나만 저장, 나머지는 `SALE_001` | `SaleEventCreateServiceTransactionTest` |
| SALE-02 | P0 | 예정·진행 중 행사에 든 상품, 판매 중지·품절 상품, 없는 상품, 한정 수량이 재고보다 많음 | 거절하고 아무것도 저장되지 않음(상품이 여럿이면 다른 상품도) | `SaleEventCreateServiceTransactionTest` |
| SALE-03 | P0 | 시작 전 행사 수정, 진행 중·종료·시작 시각이 지난 행사 수정 | 시작 전만 수정, 나머지는 거절하고 값 불변, 하나라도 거절되면 전부 불변 | `SaleEventUpdateServiceTransactionTest` |
| SALE-04 | P0 | 진행 중 행사 종료, 예정·종료 행사 종료 | 상태만 종료로 바뀌고 종료 시각·항목 불변, 나머지는 `SALE_004`, 종료 뒤 할인 조회에서 빠짐 | `SaleEventTerminationServiceTransactionTest` |
| SALE-05 | P0 | 시작 전 행사 삭제, 시작한 행사 삭제 | 행사와 항목이 지워지고 다른 행사는 남음, 시작한 행사는 `SALE_005`로 모두 그대로 | `SaleEventTerminationServiceTransactionTest` |
| SALE-06 | P0 | 상태 갱신 | 시작 시각이 지난 예정은 진행 중, 종료 시각이 지난 행사는 종료, 같은 시각에 두 번 처리해도 결과가 같음 | `SaleEventLifecycleTransactionTest`, `SaleEventSchedulerTest` |
| SALE-07 | P0 | 상태 갱신 전에 시각이 지난 행사 조회 | 시각으로 판단해 진행 중으로 조회, 종료로 닫은 행사는 빠짐 | `SaleEventLifecycleTransactionTest` |
| SALE-08 | P1 | 목록 조회 | 상품마다 한 행, 종료는 등록 당시 정가·나머지는 현재 정가, 필터·정렬·판매량 | `SaleEventListQueryServiceTest` |
| SALE-09 | P1 | 상세 조회, 후보 상품 조회 | 상세는 시각과 행사 항목, 없는 행사는 404. 후보는 판매 중·재고 있음·예정/진행 행사 없는 상품만 | `SaleEventDetailQueryServiceTest`, `SaleCandidateProductQueryServiceTest` |
| SALE-10 | P1 | 권한·입력값 | 일반 회원 403, 비로그인 401, 잘못된 입력 400이고 서비스 호출 없음 | `AdminSaleEventControllerTest` |

번개 드롭 동시 주문과 1인 1개는 ORDER-07·08에서 검증합니다.

## 결제 필수 시나리오

| ID | 우선순위 | 조건·행동 | 기대 결과 | 대응 테스트 |
|---|---|---|---|---|
| PAY-01 | P0 | PG 결제 완료, 금액 일치로 확정 | 결제·주문 결제 완료, 이력 `PAID` | `PaymentFacadeTest`, `PaymentCommandServiceTest` |
| PAY-02 | P0 | PG 금액이 결제 금액과 다름 | `AMOUNT_MISMATCH` 실패 후 PG 전액 취소, `PAYMENT_002`. 다른 요청이 먼저 처리했으면 취소 안 함 | `PaymentFacadeTest` |
| PAY-03 | P0 | 만료 시각 이후 PG 결제 완료 | `TIMEOUT` 실패 후 PG 취소, `PAYMENT_006`. 만료 시각과 같으면 승인 | `PaymentFacadeTest`, `PaymentTest` |
| PAY-04 | P0 | 같은 주문의 결제 두 건을 동시에 승인 | 하나만 결제 완료, 나머지는 `ORDER_NOT_PAYABLE` 실패 후 PG 취소 | `PaymentCommandServiceConcurrencyTest` |
| PAY-05 | P0 | 같은 주문의 결제 준비를 동시에 여러 번 | 결제 대기 한 건만 생성, 나머지 `PAYMENT_008` | `PaymentCommandServiceConcurrencyTest` |
| PAY-06 | P0 | 결제를 읽은 뒤 주문이 취소됨 | 승인하지 않고 `ORDER_NOT_PAYABLE` 실패 | `PaymentCommandServiceConcurrencyTest` |
| PAY-07 | P0 | 이미 결제 완료·실패한 결제를 다시 확정 | 완료면 PG 조회 없이 같은 응답, 실패면 실패 사유에 맞는 오류 | `PaymentFacadeTest` |
| PAY-08 | P0 | 타인 결제 확정, 다른 주문의 결제 ID로 확정 | `ORDER_003`·`C001`, PG 조회 안 함 | `PaymentFacadeTest`, `PaymentControllerTest` |
| PAY-09 | P0 | 남은 결제 대기 건이 있는 주문의 결제 준비 | PG 미결제면 `SUPERSEDED` 정리 후 새로 생성, PG 결제됨이면 승인하고 새 결제 안 만듦, PG 조회 실패면 생성 안 함 | `PaymentFacadeTest` |
| PAY-10 | P0 | 웹훅 서명 불일치·헤더 없음·5분 경과 | 401, 기록 안 함 | `PortOneWebhookVerifierTest`, `WebhookControllerTest` |
| PAY-11 | P0 | 같은 웹훅 재수신·동시 수신, 처리 중 오류 | 한 번만 처리. 오류면 `FAILED` 기록 후 오류 응답, 재전송 때 다시 처리 | `WebhookHandlerTest`, `WebhookEventServiceTest` |
| PAY-12 | P0 | 실패 처리한 결제가 PG에서 결제됨, 보상 취소 실패 | 보상 취소, 실패하면 이력 `PAID`만 남아 5분 뒤 재시도. 다른 경로로 취소됐으면 기록만 | `PaymentFacadeTest`, `PaymentTimeoutSchedulerTest` |
| PAY-13 | P1 | 만료 스케줄러 실행 | PG 미결제면 `TIMEOUT`, PG 결제됨이면 웹훅과 같은 규칙. 한 건 실패해도 나머지 처리, id 커서로 이어 읽음 | `PaymentFacadeTest`, `PaymentTimeoutSchedulerTest` |
| PAY-14 | P1 | PG 취소 요청 타임아웃·4xx | 타임아웃은 같은 멱등 키로 3번까지, 4xx는 재시도 없이 `PAYMENT_009`. 이미 취소된 결제의 전액 취소는 성공 | `PortOneClientTest` |

## 다른 담당과 공동 검증

- 요리 기록과 재료 차감의 일관성.
- 상품·재고·행사 상태가 조회 후 바뀐 경우 주문의 서버 재검증.
- 중복 결제·환불 요청, 환불 누적 상한, 구매 확정과 환불 경합.
- 정산 재집계의 동일 결과, 분석의 확정 시점·입력 cutoff 준수.
- 관리자 UI 접근과 실제 API 권한 검증 일치.
- 회원 본인 데이터의 소유권 검증.

## 실행 게이트

실행할 명령은 [프론트 검증](../../frontend/AGENTS.md#검증)과 [백엔드 검증](../../backend/AGENTS.md#검증)을 따릅니다. 명령이 바뀌면 그 두 곳만 고칩니다.

브라우저 확인 범위도 [프론트 검증](../../frontend/AGENTS.md#검증)을 따르며, 키보드·포커스, 가로 넘침, 로딩·빈 상태·실패를 함께 봅니다.

## 분석 평가·부하 테스트

예측은 모델·데이터 버전, dataCutoff, 평가 기간, 기준선, MAE 모집단, TOP 10 동률 규칙을 기록합니다. 가상 데이터 결과를 실제 고객의 할인 반응으로 해석하지 않습니다.

부하 목표와 실행 환경은 미정입니다. 측정 시 데이터 규모, 동시 사용자·요청 패턴, 지연 분포, 오류율, 자원 사용량을 함께 기록합니다.

## 결과 기록 양식

| 실행일·커밋 | 환경·명령 또는 시나리오 | 결과 | 증거 | 미검증·후속 |
|---|---|---|---|---|
| 미실행 | 아직 프로젝트 미구성 | 미실행 | 없음 | 구성 후 실행 |
