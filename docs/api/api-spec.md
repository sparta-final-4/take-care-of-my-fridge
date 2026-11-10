# API 명세서

## 공통 규칙

- 모든 API 경로는 `/api`로 시작합니다. 프론트 baseURL은 서버 주소(origin)만 갖고, `/api`는 생성된 API 경로에 들어 있습니다.
- 프론트는 API와 같은 출처로 부릅니다(로컬 Vite proxy, 운영 CloudFront `/api/*`). 그래서 백엔드에 CORS 설정은 없습니다.
- 인증은 HttpOnly 쿠키이고 프론트는 `withCredentials: true`로 보냅니다. 로그인 쿠키와 CSRF 계약은 아래 회원 API 절을 따릅니다.
- 성공·실패 응답은 모두 `ApiResponse<T>`입니다. 업무 오류는 `ErrorCode`를 담은 `BusinessException`을 공통 처리기가 바꿉니다. 인증 필터·권한 거부 응답도 같은 모양입니다.
- 공통 오류 코드:

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | 요청 검증 실패, 본문·파라미터 형식 오류, 필수 파라미터 누락 |
| 401 | A003 | 로그인이 필요함(쿠키 없음·세션 만료) |
| 403 | A004 | 로그인했지만 권한이 없음(USER가 `/api/admin/**`) |
| 403 | AUTH_002 | CSRF 실패, 메서드 단위 권한 거부 |
| 404 | COMMON_003 | 없는 경로 |
| 500 | COMMON_002 | 처리하지 못한 서버 오류 |

- 날짜·시간 형식:
  - 기본은 시간대 없는 ISO-8601입니다(`2026-11-09T17:04:30`, 날짜만이면 `2026-11-09`).
  - 상담·메시지·후기 응답처럼 `@JsonFormat`을 붙인 칸은 `yyyy-MM-dd HH:mm:ss`입니다. 각 API 명세의 응답 표에 적습니다.
  - 할인 `endsAt`은 `+09:00`이 붙은 시각입니다.
- 경로 변수 인코딩, 조회 요청 취소 신호(`signal`)는 [프론트 규칙](../../take-care-of-my-fridge-fe/CLAUDE.md#81-api-호출)을 따릅니다. 요청·응답 타입은 `/v3/api-docs`로 생성한 클라이언트를 씁니다.

## 도메인별 API

| 도메인 | 명세 |
|---|---|
| 회원·인증·관리자 | [member-auth-admin-api.md](member-auth-admin-api.md) |
| 냉장고 | [fridge-api.md](fridge-api.md) |
| 요리 기록 | [cooking-api.md](cooking-api.md) |
| 후기 | [review-api.md](review-api.md) |
| AI 상담 | [chat-api.md](chat-api.md) |
| 레시피 | [recipe-api.md](recipe-api.md) |
| 상품 | [product-api.md](product-api.md) |
| 통합검색 | [search-api.md](search-api.md) |
| 장바구니 | [cart-api.md](cart-api.md) |
| 주문 | [order-api.md](order-api.md) |
| 환불 | [refund-api.md](refund-api.md) |
| 클릭 수 기반 할인 상품 | [sale-event-api.md](sale-event-api.md#클릭-수-기반-할인-상품-api) |
| 할인 행사 | [sale-event-api.md](sale-event-api.md#할인-행사-api) |
| 결제 | [payment-api.md](payment-api.md) |
| 정산 | [settlement-api.md](settlement-api.md) |
| 알림 | [notification-api.md](notification-api.md) |

## 공유된 응답 형태

아래 값은 예시이며 실제 API 응답이 아닙니다.

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "C001",
    "message": "적절하지 않은 요청 값입니다."
  }
}
```

검증 실패는 400 `C001`이고, 첫 번째 필드 오류의 메시지 하나만 `error.message`에 담깁니다. 필드 이름은 오지 않으므로 `form.setFields` 매핑에 쓸 수 없습니다(필드별 구조는 Q-10).

## 페이지 응답

공유 구조: content, pageNumber, pageSize, totalElements, totalPages, isLast. 요청 page와 응답 pageNumber는 1부터 시작하며, 서비스 내부의 Spring Page 번호는 0부터 시작합니다.

## 상품 할인·품절 필드

상품 목록·상세(`/api/products`), 통합 검색의 상품 결과, 관리자 상품 목록·상세·등록·수정 응답(`ProductListItemResponse`, `ProductDetailResponse`)에 아래 두 필드가 있습니다. 기존 `price`(정가)와 `status`(상품 상태)는 그대로입니다.

| 필드 | 설명 |
|---|---|
| sale | 진행 중인 할인(`SaleResponse`). 없으면 null |
| soldOut | 지금 보이는 가격으로 살 수 없으면 true |

- `SaleResponse`: type(`SPECIAL`·`FLASH_DROP`), salePrice(할인가), discountRate(할인율), endsAt(종료 시각, +09:00 포함), remainingQuantity(번개 드롭 남은 수량), limitedQuantity(번개 드롭 한정 수량). 특가는 remainingQuantity·limitedQuantity가 null입니다.
- **품절**: 상품이 품절·판매 중지 상태이거나 번개 드롭 남은 수량이 0이면 `soldOut=true`입니다. 번개 드롭이 다 팔려도 `status`는 판매 중으로 남습니다. 장바구니의 `soldOut`과 같은 기준입니다.

## 필요한 API 기능 목록

HTTP 메서드·경로·DTO 이름은 구현 전 합의합니다.

| 영역 | 기능 | 권한·주요 조건 |
|---|---|---|
| 가입 | 이메일·닉네임 확인, 계정 생성 | 공개 가입 USER 고정, 닉네임·전화번호 필수·중복 불가, 약관 없음, 서버 최종 중복 검증 |
| 인증 | 로그인·갱신·로그아웃·현재 사용자 | 정지 상태 처리, 30분 미사용 만료, 세션 폐기·경합 |
| 내 정보 | 닉네임·비밀번호·맛 선호 변경 | 본인, 다른 기기 세션 처리. 회원 탈퇴 API 없음 |
| 회원 권한 | 목록·승격 | ADMIN, 정지 회원 제외, 작업 이력 기록 |
| 상품(상품 도메인 제공) | 목록·상세·등록·논리 삭제 | 쓰기 ADMIN, 유통기한·보관 방법 필수, 주문 스냅샷 보존 |
| 상품 가격(상품 도메인 제공) | 정상가 수정 | ADMIN, 1원 이상, 변경 전후 작업 이력 기록, 기존 주문 가격 불변 |
| 분석 | 예측·실제 판매량·기준선 조회 | ADMIN, 버전·시점·가상 데이터 표시 |
| 할인 후보 | 분석 조회·검토 저장·행사 등록 | ADMIN, 선택한 후보만 기존 행사 생성 기능으로 등록 |

할인 후보 분석 실행: `POST /api/admin/deal-analyses`. 요청은 `targetWeekStart`(월요일), `dayOfWeek`(MON~SUN), `discountRate`(1~99)이며 대상일은 오늘 이후다. 실제 일반 특가 이력이 부족하면 D007(422)로 중단하며, 메시지에는 부족한 종료 특가 건수와 할인율 종류만 표시한다. 학습·예측 자료 값이 올바르지 않으면 D008(500)로 중단한다.
할인 후보 최신 조회에서 본인의 저장된 분석이 없으면 D002(404)와 함께 현재 분석 대상 상품·재고 기준 상품 수, 학습 가능한 종료 일반 특가 건수(최소 3건), 할인율 종류(최소 2개)를 오류 메시지로 안내한다.
할인 후보 행사 등록: `POST /api/admin/deal-analyses/{analysisId}/event-requests`.
요청은 `candidateId`, 행사 종류(`SPECIAL` 또는 `FLASH_DROP`), `startsAt`, `endsAt`, 번개 드롭일 때 `limitedQuantity`를 받습니다.
할인율은 분석 결과의 `discountRate`를 사용합니다. 선택된 후보 중 가상 데이터가 아닌 결과만 등록할 수 있으며,
행사 생성과 후보 상태·행사 ID 기록은 한 트랜잭션에서 처리합니다.
생성 성공 시 `201`과 `candidateId`, `saleEventId`, `eventRequestStatus=APPROVED`를 반환합니다.
행사 생성에 실패하면 후보의 요청 상태는 바뀌지 않습니다.
| 관리자 작업 이력 | 이력 조회 | ADMIN, 기간·동작·결과·대상 검색. 기록 수정·삭제 API 없음 |
| 연동 | 후기용 회원 정보, 재고·판매·검색 지표 | 제공 주체·전달 방식·집계 시점 합의 |

## 회원 API 제안

상태: **제안** · 팀 합의 전 · 근거: [회원 도메인 설계](../architecture/domains/member.md)

정책이 결정된 회원 기능만 경로를 제안합니다. 경로는 prefix(/api)를 뺀 값입니다. 요청·응답·오류 코드 상세는 [회원·인증·관리자 API 명세](member-auth-admin-api.md)를 기준으로 하며, 이 표는 요약입니다.

| 기능 | Method / Path | 권한 | 요청 | 성공 응답 | 주요 오류 |
|---|---|---|---|---|---|
| 이메일 중복 확인 | GET /members/email-availability?email= | 공개 | email | `{ available: boolean }` | 400 형식 오류 |
| 닉네임 중복 확인 | GET /members/nickname-availability?nickname= | 공개 | nickname | `{ available: boolean }` | 400 형식 오류 |
| 회원가입 | POST /members | 공개 | email, password, nickname, phone, tastes[] | 201, 회원 ID | 400 입력 오류, 409 이메일·닉네임·전화번호 중복 |
| 로그인 | POST /auth/login | 공개 | email, password | 200, 쿠키 발급, 내 정보 | 401 공통 실패 문구, 403 정지 계정 |
| CSRF 토큰 | GET /auth/csrf | 공개 | 없음 | 200, headerName·token과 XSRF-TOKEN 쿠키 | 없음 |
| 토큰 갱신 | POST /auth/refresh | 갱신 쿠키·CSRF 헤더 | 본문 없음 | 200, 새 액세스·갱신 쿠키 | 401 토큰 누락·만료·재사용, 403 CSRF 실패 |
| 로그아웃 | POST /auth/logout | 인증 쿠키·CSRF 헤더 | 본문 없음 | 200, 현재 세션 폐기·인증 쿠키 삭제 | 403 CSRF 실패 |
| 내 정보 | GET /members/me | 로그인 | 없음 | 이메일, 닉네임, 역할, 상태, 맛 선호, 가입일 | 401 |
| 닉네임 변경 | PATCH /members/me/nickname | 로그인 | nickname | 200 | 400, 409 중복 |
| 비밀번호 변경 | PATCH /members/me/password | 로그인 | currentPassword, newPassword | 200, 다른 기기 세션 폐기 | 400 C001 입력 오류·M003 현재 비밀번호 불일치·M004 기존과 같음 |
| 맛 선호 변경 | PUT /members/me/tastes | 로그인 | tastes[] (0개 이상) | 200 | 400 허용하지 않는 값 |
| 회원 목록 | GET /admin/members?keyword=&role=&page=&size= | ADMIN | keyword(이메일 일부, 대소문자 무시), role(USER/ADMIN), page(기본 1), size(기본 20) | PageResponse<MemberSummary> | 400, 403 |
| 관리자 승격 | POST /admin/members/{memberId}/promotion | ADMIN | 없음 | 200, ROLE_CHANGE 성공 이력 | 403 A004 권한 거부, 404 M005 회원 없음, 409 M007 이미 ADMIN·M006 정지 |
| 작업 이력 조회 | GET /admin/audit-logs | ADMIN | from, to, action, result, targetType, targetId, page, size | PageResponse<AuditLogResponse> | 400, 403 |

- 회원 목록은 회원 ID 내림차순으로 정렬합니다. MemberSummary는 memberId, email, nickname, role, status, createdAt, promotedAt, promotedBy를 포함하고 비밀번호는 노출하지 않습니다. promotedAt·promotedBy는 가장 최근 성공한 ROLE_CHANGE 이력에서 가져오며, 이력이 없는 관리자는 null입니다. page·size는 1 이상이어야 합니다.
- 승격의 성공은 역할 변경과 이력을 한 트랜잭션으로 저장합니다. 승격 실패는 FAILED, 로그인한 일반 회원의 승격 시도는 DENIED로 기록합니다.
- 작업 이력 조회는 오늘을 포함한 최근 7일이 기본 기간입니다. from·to는 포함 날짜이며 from이 to보다 늦으면 400입니다. to만 주면 해당 날짜까지 7일을 조회합니다. result=FAILED는 FAILED·DENIED를 함께 검색하며, 최신 시각·ID 내림차순입니다. page=1, size=20이 기본값입니다. 응답에는 작업 당시 actorRole과 민감 값이 제외된 beforeValue·afterValue가 담깁니다.

- 로그인 실패는 없는 이메일과 틀린 비밀번호를 구분하지 않습니다. 정지 계정은 비밀번호가 맞을 때만 403으로 알립니다.
- 로그인 자격 증명 오류는 401 `A001`, 비밀번호가 일치하는 정지 계정은 403 `A002`입니다.
- 로그인용 JWT는 서버 환경 변수 `JWT_SECRET`(UTF-8 32바이트 이상)으로 HS256 서명합니다. 액세스 토큰은 15분, 갱신 토큰은 최대 7일 유효하며 토큰 종류는 서로 바꿔 사용할 수 없습니다. 30분 미사용 제한은 서버 세션에서 별도로 확인합니다.
- `POST /auth/login` 요청은 `email`, `password`만 받습니다. 성공 응답의 `data`는 `memberId`, `email`, `nickname`, `role`, `status`, `tastes`, `createdAt`입니다.
- 성공 시 `access_token`(Path `/api`, 15분)과 `refresh_token`(Path `/api/auth`, 7일) 쿠키를 발급합니다. 둘 다 HttpOnly, SameSite=Lax, 기본 Secure입니다. 로컬 HTTP 개발에서만 `AUTH_COOKIE_SECURE=false`로 설정할 수 있습니다.
- 로그인 POST는 익명 요청과 CSRF 예외를 허용합니다. 다른 POST 경로의 CSRF 보호는 유지합니다.
- 프론트는 `GET /auth/csrf`로 `XSRF-TOKEN` 쿠키를 받은 뒤 보호 대상 POST/PUT/PATCH/DELETE에 같은 값을 `X-XSRF-TOKEN` 헤더로 보냅니다. 로그인 후나 토큰이 사라졌을 때 다시 조회합니다.
- 보호 API는 `access_token` 쿠키의 서명·만료·세션 ID를 확인한 뒤 회원 역할을 설정합니다. 세션이 폐기·만료됐거나 30분간 사용되지 않았다면 JSON 401 `A003`입니다. 유효한 요청은 세션의 마지막 사용 시각을 갱신합니다.
- 갱신 토큰은 사용 때마다 교체합니다. 같은 토큰으로 동시에 갱신하면 세션 행 잠금으로 한 요청만 성공하며 이전 토큰 재사용은 401 `A005`입니다. 정지된 회원의 갱신은 401 `A006`입니다. 교체해도 최초 로그인으로부터 7일 최대 만료는 연장되지 않습니다.
- 갱신 요청은 만료된 액세스 쿠키 없이도 가능하지만, `refresh_token` 쿠키와 `X-XSRF-TOKEN` 헤더가 필요합니다. 성공 시 두 JWT 쿠키를 다시 발급하며 갱신 쿠키 Max-Age는 최초 7일 한도 중 남은 기간입니다. 프론트는 동시 갱신 요청을 한 번으로 모아야 합니다.
- 로그아웃은 유효한 액세스 쿠키가 있으면 해당 세션을, 액세스 쿠키가 만료되었으면 갱신 쿠키가 가리키는 세션을 폐기합니다. 인증 쿠키가 없거나 세션이 이미 폐기·만료돼도 200이며 두 쿠키를 삭제합니다. CSRF 헤더가 없거나 잘못되면 403입니다.
- tastes 값: SALTY, SWEET, SPICY, UMAMI, MILD.
- 가입 요청에 role이 들어와도 무시하고 USER로 만듭니다. /api/admin 및 그 하위 경로는 ADMIN만 접근하며, 익명 요청은 401 A003, USER 요청은 403 A004입니다. 403 `AUTH_002`는 CSRF 실패와 메서드 단위 권한 거부에 쓰며, 프론트는 이 코드를 받으면 CSRF 토큰을 다시 받아 한 번 재시도합니다.
- 가입 비밀번호는 8~30자이며 ASCII 영문·숫자·특수문자를 각각 하나 이상 포함합니다. 공백·한글은 허용하지 않습니다(2026-10-16 사용자 확정).
- 가입 요청의 nickname은 앞뒤 공백을 제거하며 1~30자입니다. tastes는 빈 목록을 허용하지만 중복 값은 거부합니다.
- 가입 요청의 phone은 `010-XXXX-XXXX` 또는 하이픈 없는 `010XXXXXXXX` 형식이며 필수입니다. 서버는 숫자 11자리로 저장하고 중복 시 409 `M009`를 반환합니다. 기존 회원과 초기 관리자의 phone은 null일 수 있습니다.
- 닉네임 변경 요청도 앞뒤 공백을 제거한 뒤 1~30자로 검사합니다. 본인 닉네임을 그대로 보내면 200이며, 다른 회원과 중복되면 409 `M002`입니다. 성공 본문은 공통 `ApiResponse.ok()` 형식입니다.
- 비밀번호 변경은 PATCH /members/me/password에 currentPassword와 newPassword를 보냅니다. 새 비밀번호는 가입과 같은 8~30자·ASCII 영문/숫자/특수문자 필수 규칙을 적용합니다. 현재 비밀번호가 틀리면 400 M003이며 변경하지 않습니다. 성공하면 현재 기기 세션은 유지하고 다른 기기의 활성 세션을 폐기하며, 공통 ApiResponse.ok() 형식으로 응답합니다(2026-10-17 사용자 결정).
- 맛 선호 변경은 `PUT /members/me/tastes` 요청 본문 `{"tastes":["SALTY","UMAMI"]}`으로 목록 전체를 교체합니다. 빈 목록은 모든 맛 선호를 지웁니다. 중복 값은 허용하고 서버에서 한 번만 저장합니다. 목록 누락·null, null 항목, 허용하지 않는 맛 값은 400입니다. 성공 본문은 공통 `ApiResponse.ok()` 형식입니다.
- 가입 성공 데이터는 `{ memberId: number }`입니다. 이메일 중복은 `M001`, 닉네임 중복은 `M002`로 응답합니다.
- `POST /members`는 익명 요청을 허용합니다. 다른 POST 경로의 CSRF 보호는 유지합니다.

## 엔드포인트 작성 템플릿

### <기능명>

- 상태·담당:
- 관련 화면·요구사항:
- Method / Path:
- 인증·권한·리소스 소유권:
- 경로·쿼리·본문 필드(타입, 필수, null, 범위):
- 성공 HTTP 상태·응답 DTO:
- 오류 HTTP 상태·ErrorCode·필드 오류:
- 페이지·정렬 규칙:
- 동시 요청·중복 요청·재시도 동작:
- 트랜잭션 및 외부 연동:
- 예시 요청·응답(예시임을 표기):
- 관련 테스트·실제 응답 확인 결과:
- 변경 영향·합의한 담당자:
