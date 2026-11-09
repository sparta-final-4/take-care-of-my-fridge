# API 계약 초안

상태: 초안 · 회원가입 컨트롤러 구현, DB 통합 검증 대기 · 다른 회원 API는 제안 단계 · 냉장고/요리 기록/후기 API 구현 완료 · 근거: S1, S3, S4, S5

## 공통 규칙

- 프론트 baseURL은 /api prefix를 포함하고, 도메인 API 함수에는 prefix를 반복하지 않습니다.
- 성공·실패는 ApiResponse<T>로 통일합니다.
- HTTP 인증 실패는 401, 인증된 사용자의 권한 부족은 403으로 구분합니다.
- 업무 오류는 ErrorCode·BusinessException·공통 처리기를 사용합니다. 인증 필터의 응답도 같은 규격으로 맞출 방법을 정합니다.
- 쿠키 인증·withCredentials는 공유 프론트 규칙입니다. 로그인 쿠키와 CSRF 계약은 아래 회원 API 절을 따릅니다. CORS 정책은 배포 주소 확정 전까지 미정입니다.
- 날짜·시간은 기본 ISO-8601. 시간대·날짜 전용 값·null 허용을 필드별로 확정합니다.
- 경로 변수 인코딩, 조회 요청 취소 신호, 요청·응답 타입 단일 정의는 프론트 규칙을 따릅니다.

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

필드별 검증 실패를 담을 구조는 Q-10에서 확정합니다. 기존 두 필드만으로 form.setFields 매핑이 가능하다고 가정하지 않습니다.

## 페이지 응답

공유 구조: content, pageNumber, pageSize, totalElements, totalPages, isLast. 요청 page와 응답 pageNumber는 1부터 시작하며, 서비스 내부의 Spring Page 번호는 0부터 시작합니다(2026-10-17 회원 목록 조회 구현).

## 관리자 상품 조회

| 기능 | Method / Path | 권한 | 요청 | 성공 응답 |
|---|---|---|---|---|
| 상품 목록 | GET /api/admin/products | ADMIN | keyword(상품명 일부), type(INGREDIENT/MEALKIT), status(ON_SALE/SOLD_OUT/STOPPED), page(기본 1), size(기본 20, 최대 100) | PageResponse<ProductListItemResponse> |
| 상품 상세 | GET /api/admin/products/{productId} | ADMIN | productId | ProductDetailResponse, 없으면 PRODUCT_001 |

관리자 목록에서 status를 생략하면 세 상태를 모두 조회합니다. 상세 조회는 판매 중지 상품도 반환합니다. 공개 `/api/products` 조회는 기존처럼 판매 중지 상품을 제외합니다.

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

## 냉장고 API

상태: 구현 완료 · 담당: 이현정 · 자세한 동작: [냉장고 설계](../architecture/domains/fridge.md)

이 절부터 [AI 상담 API](#ai-상담-api)까지 같은 약속을 씁니다.
- 경로는 `/api`를 뺀 값입니다. 권한이 "로그인"인 API는 모두 401 `A003`이 있어 에러 표에서 뺍니다.
- 요청 검증 실패(형식·범위·필수 누락·모르는 enum 값)는 400 `C001`입니다. 에러 표에는 그 밖의 오류만 씁니다.
- 페이지는 1부터 시작하고 응답은 공통 `PageResponse`입니다. 시각 형식을 따로 적지 않은 칸은 ISO-8601입니다.

### 재료 목록
`GET /fridge/items` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| sort (query) | `EXPIRATION`·`CREATED`·`NAME` | N | 기본 `EXPIRATION`(소비기한 순). `CREATED`는 최신 등록순 |

응답: `List<FridgeItemResponse>` (회원 재료 전체. 탭 나누기는 프론트)

주의 사항
- 기본 양념 탭과 개수 제외는 `basicSeasoning`으로 프론트가 나눕니다.

### 재료 이력
`GET /fridge/logs` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| action (query) | `CONSUMED`·`DISCARDED` | N | 비우면 둘 다 |
| from (query) | date | N | 이 날짜부터(`created_at` 기준) |
| to (query) | date | N | 이 날짜 하루 끝까지 |
| page (query) | int | N | 기본 1, 1 이상 |
| size (query) | int | N | 기본 20, 1~100 |

응답: `PageResponse<FridgeItemLogResponse>` (최신순)

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | `from`이 `to`보다 늦음 |

### 재료 등록
`POST /fridge/items` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| items | `FridgeItemCreateItem[]` | Y | 1~20개 |
| items[].rawName | string | Y | 재료 이름, 200자 이하 |
| items[].quantity | number | Y | 0보다 큼 |
| items[].storage | `REFRIGERATED`·`FROZEN`·`ROOM_TEMP` | N | 비우면 이은 재료의 기본 보관방식 |
| items[].expirationDate | date | N | 비우면 오늘 + 재료 기본 보관 일수 |

응답: `List<FridgeItemResponse>`

주의 사항
- 이름은 표준 재료에 자동으로 잇고, 사전에 없으면 직접 입력 재료로 잇습니다.

### 재료 수정
`PATCH /fridge/items/{fridgeId}` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| fridgeId (path) | long | Y | 냉장고 재료 ID |
| rawName | string | N | 공백만은 안 됨, 200자 이하 |
| quantity | number | N | 0보다 큼 |
| storage | `REFRIGERATED`·`FROZEN`·`ROOM_TEMP` | N | |
| expirationDate | date | N | |

응답: `FridgeItemResponse`

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | FRIDGE_001 | 없는 재료이거나 남의 재료 |

주의 사항
- 보낸 칸만 바꾸고 null인 칸은 그대로 둡니다.
- `rawName`이 바뀌었을 때만 표준 재료를 다시 잇습니다(밀키트는 다시 잇지 않음).

### 재료 빼기
`POST /fridge/items/{fridgeId}/take-out` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| fridgeId (path) | long | Y | 냉장고 재료 ID |
| action | `CONSUMED`·`DISCARDED` | Y | 먹었어요·버렸어요 |
| quantity | number | N | 0보다 큼. null이거나 남은 양 이상이면 전부 |

응답: `FridgeItemTakeOutResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| fridgeId | long | N | 냉장고 재료 ID |
| removed | boolean | N | 남은 양이 없어 지워졌는지 |
| remainingQuantity | number | N | 남은 양(지워졌으면 0) |

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | FRIDGE_005 | 밀키트를 정수가 아닌 개수로 뺌 |
| 404 | FRIDGE_001 | 없는 재료이거나 남의 재료 |

주의 사항
- 남은 양이 0.05 미만이 되면 0으로 보고 줄을 지웁니다.

### 재료 비우기
`DELETE /fridge/items/{fridgeId}` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| fridgeId (path) | long | Y | 냉장고 재료 ID |
| reason (query) | `CONSUMED`·`DISCARDED`·`WRONG_ENTRY` | Y | `WRONG_ENTRY`는 이력 없이 지움 |

응답: 없음(`ApiResponse.ok()`)

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | FRIDGE_001 | 없는 재료이거나 남의 재료 |

### 냉장고 응답 DTO

`FridgeItemResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| id | long | N | 냉장고 재료 ID |
| ingredientId | long | Y | 이은 표준 재료 ID |
| ingredientName | string | Y | 표준 재료 이름 |
| baseUnit | string | Y | 기본 단위 |
| rawName | string | N | 등록 이름 |
| quantity | number | N | 남은 양 |
| storage | `REFRIGERATED`·`FROZEN`·`ROOM_TEMP` | N | 보관방식 |
| expirationDate | date | N | 소비기한 |
| source | `MANUAL`·`COMMERCE` | N | 직접 등록·구매 확정 입고 |
| orderItemId | long | Y | 구매 확정 입고만 있음 |
| imminent | boolean | N | 사용 중단 예정, `status`를 씀 |
| expired | boolean | N | 사용 중단 예정, `status`를 씀 |
| dDay | long | N | 소비기한까지 남은 일수(오늘 0, 지나면 음수) |
| status | `NORMAL`·`IMMINENT`·`EXPIRED` | N | 임박은 D-3 이내 |
| ingredientType | `NORMAL`·`CUSTOM`·`MEALKIT` | N | 일반·직접 입력·밀키트 |
| basicSeasoning | boolean | N | 기본 양념 |
| createdAt | date-time | N | |
| updatedAt | date-time | N | |
| productId | long | Y | 구매 확정 줄의 상품 ID. 직접 등록·예전 줄은 null |

`FridgeItemLogResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| id | long | N | 이력 ID |
| ingredientId | long | Y | 표준 재료 ID |
| ingredientName | string | Y | 표준 재료 이름 |
| baseUnit | string | Y | 기본 단위 |
| rawName | string | N | 등록 이름 |
| quantity | number | N | 실제로 뺀 양 |
| action | `CONSUMED`·`DISCARDED` | N | |
| cookedByRecipe | boolean | N | 요리 완료로 쓴 기록인지 |
| createdAt | date-time | N | |

## 요리 기록 API

상태: 구현 완료 · 담당: 이현정 · 자세한 동작: [요리 기록 설계](../architecture/domains/cooking.md)

### 요리 완료
`POST /cooking-logs` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| recipeId | long | Y | 레시피 ID |
| result | `SUCCESS`·`FAILED` | Y | 요리 결과 |
| memo | string | N | 50자 이하 |
| chatSessionId | long | N | 이을 상담. 비우면 서버가 찾음 |
| usedIngredients | `UsedIngredientRequest[]` | N | 30개 이하, 같은 재료 두 번 안 됨 |
| usedIngredients[].ingredientId | long | Y | 표준 재료 ID |
| usedIngredients[].quantity | number | N | 0보다 큼 |

응답: `CookingCompleteResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| cookingLogId | long | N | 요리 기록 ID |
| deductions | `IngredientDeductionResponse[]` | N | 실제로 차감한 재료만 |
| deductions[].ingredientId | long | N | 표준 재료 ID |
| deductions[].deductedQuantity | number | N | 차감한 양 |
| deductions[].remainingQuantity | number | N | 남은 양 |

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | RECIPE_001 | 없는 레시피 |

주의 사항
- 상담을 잇지 못해도(남의 상담·끝남·만료) 오류 없이 요리 완료를 진행하고, 이은 상담은 커밋 뒤 끝냅니다.
- 기본 양념·직접 입력·밀키트와 냉장고에 없는 재료는 차감하지 않습니다.

### 요리 기록 목록
`GET /cooking-logs` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| recipeId (query) | long | N | 비우면 전체 |
| from (query) | date | N | 이 날짜부터 |
| to (query) | date | N | 이 날짜 하루 끝까지 |
| page (query) | int | N | 기본 1, 1 이상 |
| size (query) | int | N | 기본 20, 1~100 |

응답: `PageResponse<CookingLogResponse>`

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | `from`이 `to`보다 늦음 |

### 요리 결과 수정
`PATCH /cooking-logs/{cookingLogId}` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| cookingLogId (path) | long | Y | 요리 기록 ID |
| result | `SUCCESS`·`FAILED` | Y | |
| memo | string | N | 50자 이하 |

응답: `CookingLogResponse`

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | COOKING_001 | 없는 기록이거나 남의 기록 |

주의 사항
- 재료 차감은 되돌리지 않으므로 결과와 메모만 바꿉니다.

### 레시피 요리 통계
`GET /recipes/{recipeId}/cooking-stats` · 공개

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| recipeId (path) | long | Y | 레시피 ID |

응답: `CookingStatsResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| recipeId | long | N | |
| totalCount | long | N | 기록이 없으면 0 |
| successCount | long | N | |
| failedCount | long | N | |

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | RECIPE_001 | 없는 레시피 |

### 마이 레시피
`GET /me/recipes` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| reviewStatus (query) | `ALL`·`WRITTEN`·`NOT_WRITTEN` | N | 기본 `ALL`. 후기 쓴·안 쓴 레시피 |
| sort (query) | `RECENT`·`MOST` | N | 기본 `RECENT`(최근 요리순). `MOST`는 많이 만든 순 |
| page (query) | int | N | 기본 1, 1 이상 |
| size (query) | int | N | 기본 10, 1~50 |

응답: `MyRecipeListResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| summary | `MyRecipeSummaryResponse` | N | 필터와 상관없는 전체 요약 |
| summary.recipeCount | long | N | 요리한 레시피 수 |
| summary.totalCookCount | long | N | 총 요리 수 |
| summary.writtenCount | long | N | 후기 쓴 레시피 수 |
| summary.notWrittenCount | long | N | 후기 안 쓴 레시피 수 |
| summary.mostCookedRecipe | `{ recipeId, recipeName, cookCount }` | Y | 가장 많이 만든 레시피. 기록이 없으면 null |
| summary.consumedIngredientCount | long | N | 요리로 쓴 재료 수 |
| recipes | `PageResponse<MyRecipeResponse>` | N | |
| recipes.content[].recipeId | long | N | |
| recipes.content[].recipeName | string | Y | |
| recipes.content[].recipeImageUrl | string | Y | |
| recipes.content[].cookCount | long | N | 이 레시피를 만든 수 |
| recipes.content[].lastCookedAt | date-time | N | |
| recipes.content[].fromChat | boolean | N | 상담에서 이어진 기록이 있는지 |
| recipes.content[].review | `{ id, rating, content, updatedAt }` | Y | 내 후기. 없으면 null |

### 인기 레시피
`GET /recipes/popular` · 공개

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| size (query) | int | N | 기본 6, 1~20 |

응답: `List<PopularRecipeResponse>` (최근 30일 요리한 회원 수 순)

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| recipeId | long | N | |
| recipeName | string | N | |
| imageUrl | string | Y | |
| tipSummary | string | Y | 후기 요약 |
| cookedMemberCount | long | N | 최근 30일 요리한 회원 수 |
| averageRating | number | Y | 후기가 없으면 null |
| reviewCount | long | N | |

주의 사항
- 순위는 24시간 캐시하고, 이름·이미지·요약·평점은 요청마다 읽습니다.

### 요리 기록 응답 DTO

`CookingLogResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| id | long | N | 요리 기록 ID |
| recipeId | long | N | |
| result | `SUCCESS`·`FAILED` | N | |
| memo | string | Y | |
| fromChat | boolean | N | 상담에 이어진 기록인지 |
| createdAt | date-time | N | |

## 후기 API

상태: 구현 완료 · 담당: 이현정 · 자세한 동작: [후기 설계](../architecture/domains/review.md)

### 후기 목록
`GET /reviews` · 공개 (`mine=true`는 로그인)

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| recipeId (query) | long | N | 비우면 전체 레시피 |
| mine (query) | boolean | N | true면 내 후기만 |
| sort (query) | `LATEST`·`RATING` | N | 기본 `LATEST` |
| page (query) | int | N | 기본 1, 1 이상 |
| size (query) | int | N | 기본 10, 1~50 |

응답: `PageResponse<ReviewResponse>`

| HTTP | 코드 | 조건 |
|---|---|---|
| 401 | A003 | 로그인하지 않고 `mine=true` |

### 후기 작성
`POST /reviews` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| recipeId | long | Y | 레시피 ID |
| rating | int | Y | 1~5 |
| content | string | Y | 공백만은 안 됨, 100자 이하 |

응답: `ReviewResponse`

| HTTP | 코드 | 조건 |
|---|---|---|
| 403 | REVIEW_003 | 요리 완료 기록이 없는 레시피 |
| 404 | RECIPE_001 | 없는 레시피 |
| 409 | REVIEW_002 | 이 레시피에 이미 후기를 씀 |

### 후기 수정
`PATCH /reviews/{reviewId}` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| reviewId (path) | long | Y | 후기 ID |
| rating | int | Y | 1~5 |
| content | string | Y | 공백만은 안 됨, 100자 이하 |

응답: `ReviewResponse`

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | REVIEW_001 | 없는 후기이거나 남의 후기 |

### 후기 삭제
`DELETE /reviews/{reviewId}` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| reviewId (path) | long | Y | 후기 ID |

응답: 없음(`ApiResponse.ok()`)

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | REVIEW_001 | 없는 후기이거나 남의 후기 |

### 후기 요약 실행
`POST /admin/review-summaries/run` · ADMIN

요청: 없음

응답: `ReviewSummaryResult`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| targetCount | int | N | 대상 레시피 수 |
| successCount | int | N | |
| skippedCount | int | N | 시간이 다 돼 다음으로 넘긴 수 포함 |
| failedCount | int | N | |
| elapsedMillis | long | N | |
| totalInputTokens | long | N | |
| totalOutputTokens | long | N | |
| totalCostUsd | number | N | |
| items | `ReviewSummaryItem[]` | N | 레시피마다 한 줄 |
| items[].recipeId | long | N | |
| items[].recipeName | string | Y | |
| items[].status | `SUCCESS`·`SKIPPED`·`FAILED` | N | |
| items[].reason | string | Y | 건너뜀·실패 이유 |
| items[].reviewCount | int | Y | 요약에 쓴 후기 수 |
| items[].inputTokens | int | Y | |
| items[].outputTokens | int | Y | |
| items[].costUsd | number | Y | |
| items[].previousSummary | string | Y | |
| items[].newSummary | string | Y | |

| HTTP | 코드 | 조건 |
|---|---|---|
| 401 | A003 | 로그인 안 함 |
| 403 | A004 | 관리자가 아님 |
| 409 | REVIEW_004 | 요약이 이미 도는 중 |
| 500 | COMMON_002 | 실행 중 예상하지 못한 오류 |

주의 사항
- 한 번에 20초까지만 돌고, 남은 레시피는 SKIPPED로 다음 실행에 넘깁니다.

### 후기 응답 DTO

`ReviewResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| id | long | N | 후기 ID |
| recipeId | long | N | |
| recipeName | string | Y | 레시피가 지워졌으면 null |
| rating | int | N | 1~5 |
| content | string | N | |
| nickname | string | Y | 작성자 닉네임 |
| mine | boolean | N | 로그인한 사람이 쓴 글인지(비로그인은 false) |
| createdAt | string(`yyyy-MM-dd HH:mm:ss`) | N | |
| updatedAt | string(`yyyy-MM-dd HH:mm:ss`) | N | |

## AI 상담 API

상태: 구현 완료 · 담당: 이현정 · 자세한 동작: [AI 상담 설계](../architecture/domains/chat.md)

### 상담 시작
`POST /chat-sessions` · 로그인

요청: 없음

응답: `ChatSessionResponse` (`cookedRecipe`는 늘 null)

주의 사항
- 진행 중 상담이 있으면 그 상담을, 30분이 지났으면 만료시키고 새 상담을 돌려줍니다. 자세한 동작: [상담 목록](../architecture/domains/chat.md#상담-목록)

### 내 상담 목록
`GET /chat-sessions` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| page (query) | int | N | 기본 1, 1 이상 |
| size (query) | int | N | 기본 10, 1~100 |
| status (query) | `ALL`·`ACTIVE`·`ENDED` | N | 기본 `ALL`. `ENDED`는 종료 + 만료 |

응답: `ChatSessionListResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| counts.total | long | N | 탭 건수. 상태 필터와 상관없음 |
| counts.active | long | N | |
| counts.ended | long | N | 종료 + 만료 |
| sessions | `PageResponse<ChatSessionSummaryResponse>` | N | 최근 시작순 |
| sessions.content[].sessionId | long | N | |
| sessions.content[].status | `ACTIVE`·`COMPLETED`·`EXPIRED` | N | 시간이 지난 진행 중 상담은 `EXPIRED` |
| sessions.content[].startedAt | string(`yyyy-MM-dd HH:mm:ss`) | N | |
| sessions.content[].lastMessageAt | string(`yyyy-MM-dd HH:mm:ss`) | N | |
| sessions.content[].messageCount | long | N | 걸러진 메시지 포함 |
| sessions.content[].firstQuestion | string | Y | 첫 사용자 말 앞 50자. 걸러진 말은 뺌 |
| sessions.content[].lastMessagePreview | string | Y | 마지막 메시지 앞 50자. 걸러진 말은 뺌 |
| sessions.content[].recommendedRecipes | `{ recipeId, name }[]` | N | 최근 추천 레시피 최대 3개 |

주의 사항
- 메시지가 0개인 상담은 목록과 건수에서 빠집니다. 자세한 동작: [상담 목록](../architecture/domains/chat.md#상담-목록)

### 상담 단건
`GET /chat-sessions/{sessionId}` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| sessionId (path) | long | Y | 상담 ID |

응답: `ChatSessionResponse`

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | CHAT_001 | 없는 상담이거나 남의 상담 |

### 상담 끝내기
`PATCH /chat-sessions/{sessionId}/complete` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| sessionId (path) | long | Y | 상담 ID |

응답: `ChatSessionResponse` (`status`=`COMPLETED`, `cookedRecipe`는 null)

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | CHAT_001 | 없는 상담이거나 남의 상담 |
| 409 | CHAT_005 | 이미 끝났거나 만료된 상담 |

### 메시지 조회
`GET /chat-sessions/{sessionId}/messages` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| sessionId (path) | long | Y | 상담 ID. 끝나거나 만료된 상담도 됨 |

응답: `List<ChatMessageResponse>` (순서대로)

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| id | long | N | 메시지 ID |
| sequence | int | N | 상담 안 순서 |
| role | `USER`·`ASSISTANT` | N | |
| content | string | N | AI 답은 소개 글(추천) 또는 답 글 |
| createdAt | string(`yyyy-MM-dd HH:mm:ss`) | N | |
| recommendations | `RecommendedRecipe[]` | N | 레시피 카드. 없으면 빈 목록 |
| mealkits | `RecommendedMealkit[]` | N | 밀키트 카드. 없으면 빈 목록 |
| products | `ProductCard[]` | N | 상품 카드. 없으면 빈 목록 |

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | CHAT_001 | 없는 상담이거나 남의 상담 |

주의 사항
- 카드의 가격·품절·냉장고 여부는 지금 기준으로 다시 계산하고, 판매 중지된 상품은 빠집니다. 냉장고 카드는 저장하지 않아 나오지 않습니다. 자세한 동작: [저장](../architecture/domains/chat.md#저장)

### 메시지 보내기 (SSE)
`POST /chat-sessions/{sessionId}/messages` · 로그인 · 응답 `text/event-stream`

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| sessionId (path) | long | Y | 상담 ID |
| content | string | Y | 공백만은 안 됨, 500자 이하 |
| mode | `AUTO`·`RECOMMEND` | N | 기본 `AUTO`(AI가 의도 판단). `RECOMMEND`는 추천 칩 |
| selectedIngredientIds | long[] | N | 고른 냉장고 재료 ID, 50개 이하. null·중복은 뺌 |
| useSelectedOnly | boolean | N | 기본 false. true면 고른 재료를 쓰는 레시피만 |

스트림 전 에러 (JSON `ApiResponse`)

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | CHAT_001 | 없는 상담이거나 남의 상담 |
| 409 | CHAT_005 | 끝난 상담 |
| 409 | CHAT_006 | 마지막 대화에서 30분이 지난 상담 |
| 409 | CHAT_009 | 다른 창에서 답을 받는 중 |
| 429 | CHAT_007 | 하루 횟수를 다 씀 |
| 503 | CHAT_008 | Redis 장애 |

SSE 이벤트

| 이벤트 | 보내는 때 | data |
|---|---|---|
| `recommendations` | 카드가 있는 답에서 글보다 먼저 한 번 | `{ recipes: RecommendedRecipe[], mealkits: RecommendedMealkit[], fridgeItems: FridgeItemCard[], products: ProductCard[], relaxed: string[], empty: boolean }`. 레시피 카드는 최대 3개, 냉장고 카드는 최대 10개. `relaxed`는 지금 늘 빈 목록, `empty`는 보여 줄 카드가 하나도 없으면 true |
| `message` | 답 글 조각. 여러 번 | `{ text: string }` |
| `recommendation_text` | 추천 답의 카드 설명 조각. 여러 번 | `{ index: int (카드 순서, 0부터), text: string }` |
| `done` | 저장을 마친 뒤 마지막에 한 번 | `{ userMessageId: long, assistantMessageId: long }` |
| `error` | 스트림을 연 뒤 실패. 마지막에 한 번 | `{ code: string }`. `CHAT_010` AI 답 없음·빈 답, `CHAT_011` AI 요청이 많음, `CHAT_012` 답이 끊김, `COMMON_002` 그 밖 |

의도별 이벤트 순서

| 의도 | 이벤트 순서 | 자세한 동작 |
|---|---|---|
| 추천(`RECOMMEND`, 추천 칩), 다시 추천(`REFINE`) | `recommendations` → `message`(소개) → `recommendation_text`(카드별) → `done` | [이어지는 말](../architecture/domains/chat.md#이어지는-말), [추천 카드](../architecture/domains/chat.md#추천-카드) |
| 추천 후보 없음 | `recommendations`(`empty=true`) → `message`(서버 문장) → `done` | [추천 카드](../architecture/domains/chat.md#추천-카드) |
| 밀키트만 | `recommendations`(`mealkits`만) → `message`(서버 문장 하나) → `done` | [밀키트 카드](../architecture/domains/chat.md#밀키트-카드) |
| 일반(`GENERAL`) | `message` 조각 → `done` | [의도 판단](../architecture/domains/chat.md#의도-판단) |
| 레시피 자세히(`RECIPE_DETAIL`) | `recommendations`(카드 1개) → `message` 조각 → `done`. 레시피가 지워졌으면 `message` → `done` | [RECIPE_DETAIL](../architecture/domains/chat.md#recipe_detail-레시피-자세히) |
| 냉장고 보기(`FRIDGE`) | `recommendations`(`fridgeItems`, 없는 재료면 `products`) → `message`(서버 문장) → `done` | [냉장고 보기와 사기](../architecture/domains/chat.md#냉장고-보기와-사기) |
| 사기(`SHOP`), 할인 질문 | `recommendations`(`products`) → `message`(서버 문장) → `done` | [냉장고 보기와 사기](../architecture/domains/chat.md#냉장고-보기와-사기) |
| 단어 차단, `OFF_TOPIC`·`BLOCKED`·`SELF_HARM` | `message`(안내 답 하나) → `done` | [안전 장치](../architecture/domains/chat.md#안전-장치) |

주의 사항
- 건강 관련 말이 있으면 답 끝에 안내 `message`가 하나 더 오고, 알레르기 재료를 뺐으면 추천 답 끝에 안내가 붙습니다. [건강 안내](../architecture/domains/chat.md#건강-안내), [알레르기](../architecture/domains/chat.md#알레르기)
- 하루 횟수는 단어 차단만 세지 않고, 첫 답 조각 전에 실패하면 돌려줍니다. [하루 횟수](../architecture/domains/chat.md#하루-횟수)
- AI 답의 링크는 지우고 보냅니다. [링크 지우기](../architecture/domains/chat.md#링크-지우기)

### 상담 응답 DTO

`ChatSessionResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| id | long | N | 상담 ID |
| status | `ACTIVE`·`COMPLETED`·`EXPIRED` | N | |
| lastMessageAt | string(`yyyy-MM-dd HH:mm:ss`) | N | |
| createdAt | string(`yyyy-MM-dd HH:mm:ss`) | N | |
| cookedRecipe | `{ recipeId, name }` | Y | 요리 완료로 끝난 상담의 요리. 단건 조회만 채움 |

`RecommendedRecipe` (레시피 카드)

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| recipeId | long | N | |
| name | string | N | |
| imageUrl | string | Y | |
| category | string | Y | 분류 |
| cookTimeMin | int | Y | 조리 시간(분) |
| usedIngredients | string[] | N | 쓰는 냉장고 재료 이름, 임박한 순 |
| imminentIngredients | `{ name, dDay }[]` | N | D-3 이내 재료. dDay 0은 오늘까지 |
| matchedTastes | string[] | N | 겹친 맛 한글 이름 |
| missingIngredients | `{ ingredientId, name, product: ShoppingProduct? }[]` | N | 없는 재료(기본 양념 제외). 파는 상품이 없으면 product null |
| missingSelectedIngredients | string[] | N | 고른·말한 재료 중 냉장고에 없는 것 |
| lacksManyIngredients | boolean | N | 재료를 많이 사야 함 |
| reason | string | N | 카드 이유 한 줄, 25자 안팎 |
| description | string | Y | 카드 설명. 추천 답 스트림에서는 null이고 `recommendation_text`로 옴(레시피 자세히 카드는 "물어보신 레시피예요"). 메시지 조회에서 채움 |

`RecommendedMealkit` (밀키트 카드)

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| product | `ShoppingProduct` | N | `unitAmount`·`unit`은 null |
| servings | int | N | 인분 |
| recipeId | long | Y | 연결 레시피 |
| recipeName | string | Y | |
| description | string | Y | 레시피 카드 아래 한 장일 때 서버 문장, 밀키트만이면 null |
| inFridge | boolean | N | 냉장고에 있는지 |
| fridgeDDay | long | Y | 냉장고에 있으면 남은 일수 |

`ShoppingProduct` (담을 상품)

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| productId | long | N | 담기는 `POST /cart-items` |
| name | string | N | |
| imageUrl | string | Y | |
| price | int | N | 정가 |
| salePrice | int | Y | 진행 중 할인 판매가 |
| saleType | `SPECIAL`·`FLASH_DROP` | Y | 할인이 없으면 null |
| soldOut | boolean | N | 지금 살 수 없음(품절·번개 드롭 0개) |
| unitAmount | number | Y | 상품 용량. 예: 300 |
| unit | string | Y | 예: g |

`ProductCard` (사기·냉장고 보기의 상품 카드)

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| name | string | N | 말한 이름(할인 목록이면 상품 이름) |
| product | `ShoppingProduct` | N | 파는 상품이 없는 이름은 카드를 만들지 않음 |

`FridgeItemCard` (냉장고 보기 카드, SSE에만 나옴)

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| fridgeId | long | N | |
| ingredientId | long | Y | |
| name | string | N | 일반 재료는 재료 이름, 밀키트·직접 입력은 등록 이름 |
| rawName | string | N | |
| quantity | number | N | |
| unit | string | Y | |
| dDay | long | N | 음수면 지남 |
| status | `NORMAL`·`IMMINENT`·`EXPIRED` | N | |
| mealkit | boolean | N | |
| productId | long | Y | `fridge.product_id` |
| imageUrl | string | Y | 밀키트 줄의 상품 이미지 |

## 장바구니 API

상태: **구현 완료** · 담당: 장준혁 · 근거: 실제 백엔드 컨트롤러 및 서비스 구현

경로는 prefix(/api)를 뺀 값입니다. 회원 번호는 로그인 정보에서만 읽고 요청에는 받지 않습니다.

| 기능 | Method / Path | 권한 | 성공 응답 |
|---|---|---|---|
| 장바구니 담기 | POST /cart-items | 로그인 | 새 항목 201, 수량만 더하면 200 |
| 장바구니 조회 | GET /cart-items | 로그인 | 200 |
| 수량 변경 | PATCH /cart-items/{cartItemId} | 로그인 | 200 |
| 항목 삭제 | DELETE /cart-items | 로그인 | 200 |

요청 값입니다.

| 기능 | 위치 | 이름 | 설명 |
|---|---|---|---|
| 장바구니 담기 | Body | productId | 담을 상품. 필수 |
| 장바구니 담기 | Body | quantity | 담을 수량. 1 이상, 생략하면 1 |
| 수량 변경 | Path | cartItemId | 바꿀 항목 |
| 수량 변경 | Body | quantity | 바꿀 수량. 1 이상, 필수 |
| 항목 삭제 | Query | cartItemIds | 지울 항목 번호. 1개 이상 |

응답 데이터입니다.

| 기능 | 응답 | 필드 |
|---|---|---|
| 장바구니 담기 | AddCartItemResponse | productId, quantity |
| 장바구니 조회 | CartItemsResponse | items(담은 순서) |
| 수량 변경 | ChangeCartItemQuantityResponse | cartItemId, quantity |
| 항목 삭제 | 공통 성공 응답 | 없음 |

오류입니다. 401은 모든 기능에 공통입니다.

| 기능 | HTTP | 코드 | 조건 |
|---|---|---|---|
| 장바구니 담기 | 400 | 공통 | 상품 번호가 없거나 수량이 1 미만 |
| 장바구니 담기 | 404 | `PRODUCT_001` | 상품 없음 |
| 장바구니 담기 | 409 | `PRODUCT_005` | 판매 중지 상품 |
| 장바구니 담기 | 409 | `CART_002` | 재고 초과 |
| 장바구니 담기 | 409 | `CART_003` | 번개 드롭 구매 한도 초과 |
| 장바구니 담기 | 409 | `CART_004` | 같은 회원의 담기가 겹쳐 한 번 다시 시도해도 충돌 |
| 수량 변경 | 400 | 공통 | 수량이 없거나 1 미만 |
| 수량 변경 | 404 | `CART_001` | 항목 없음 또는 타인 항목 |
| 수량 변경 | 404 | `PRODUCT_001` | 상품 없음 |
| 수량 변경 | 409 | `PRODUCT_005` | 판매 중지 상품 |
| 수량 변경 | 409 | `CART_002` | 재고 초과 |
| 수량 변경 | 409 | `CART_003` | 번개 드롭 구매 한도 초과 |
| 항목 삭제 | 400 | 공통 | cartItemIds가 없거나 비어 있거나 숫자가 아님 |

조회 항목(`CartItemResponse`): cartItemId, productId, productName, imageUrl, quantity, listPrice(정가), unitPrice(지금 단가), sale(진행 중인 할인, `SaleResponse`), soldOut(품절 여부). 할인이 없으면 sale은 null이고 unitPrice는 정가입니다. `SaleResponse`는 상품 목록·상세와 같은 모양입니다(위 `SaleResponse` 참고). 번개 드롭이 아니면 sale의 remainingQuantity·limitedQuantity는 null입니다.

- **담기**: 장바구니가 없으면 만들고, 이미 담긴 상품이면 수량을 더합니다. 장바구니는 회원당 하나, 한 장바구니에 같은 상품은 하나입니다. 같은 회원의 담기가 동시에 오면 충돌한 요청을 서버가 한 번 다시 시도해 수량을 더하고, 그래도 충돌이면 `CART_004`입니다.
- **재고 확인**: 담을 때는 기존 수량과 요청 수량의 합이 재고를 넘으면 거절합니다. 재고는 응답으로 주지 않습니다.
- **수량 변경**: 담은 뒤 재고가 줄었어도 수량을 내리는 것은 허용하고, 현재 수량보다 올리는 것은 재고까지만 허용합니다.
- **번개 드롭 한도**: 진행 중인 번개 드롭 상품은 1인 1개라 합친 수량이 1을 넘게 담거나 올리면 `CART_003`으로 거절합니다. 담은 뒤 번개 드롭이 되어 1을 넘게 남아 있으면 내리는 것은 허용합니다.
- **할인 조회**: 조회할 때 진행 중인 할인을 한 번에 읽어 항목마다 단가를 붙입니다. 장바구니에는 할인 가격을 저장하지 않으므로 할인이 끝나면 다음 조회부터 정가가 보입니다.
- **품절 표시**: 상품이 품절·판매 중지 상태이거나 번개 드롭 남은 수량이 0이면 그 항목만 `soldOut=true`입니다.
- **본인 소유 검증**: 타인의 항목이거나 없는 항목의 수량 변경은 `CART_001`(404)로 통일합니다. 삭제는 내 항목만 지우고, 남의 항목·없는 번호는 오류 없이 무시합니다. 같은 요청을 두 번 보내도 결과가 같습니다.
- **재고를 잡지 않음**: 장바구니는 재고를 줄이거나 잡아 두지 않습니다. 재고는 주문을 만들 때 줄어듭니다.

## 주문 API

상태: **구현 완료** · 담당: 장준혁 · 근거: 실제 백엔드 컨트롤러 및 서비스 구현

경로는 prefix(/api)를 뺀 값입니다. 회원 번호는 로그인 정보에서만 읽고 요청에는 받지 않습니다.

| 기능 | Method / Path | 권한 | 성공 응답 |
|---|---|---|---|
| 주문하기 | POST /orders | 로그인 | 새 주문 201, 같은 키로 만든 주문 200 |
| 주문 확인 조회 | GET /orders/{orderId}/checkout | 로그인 | 200 |
| 주문 취소 | PATCH /orders/{orderId}/cancel | 로그인 | 200(데이터 없음) |
| 주문 목록 | GET /orders | 로그인 | 200 |
| 주문 상세 | GET /orders/{orderId} | 로그인 | 200 |
| 구매 확정 | PATCH /orders/{orderId}/confirm | 로그인 | 200 |

요청 값입니다.

| 기능 | 위치 | 이름 | 설명 |
|---|---|---|---|
| 주문하기 | Header | Idempotency-Key | 주문하기 클릭마다 만든 값. 필수, 64자 이하 |
| 주문하기 | Body | items | 주문할 장바구니 항목. 1~50개 |
| 주문하기 | Body | items[].cartItemId | 장바구니 항목 번호. 필수 |
| 주문하기 | Body | items[].unitPrice | 장바구니에서 본 단가. 필수 |
| 주문하기 | Body | items[].saleType | 장바구니에서 본 할인 구분. 할인이 없었으면 생략 |
| 주문 확인 조회·취소·상세·구매 확정 | Path | orderId | 대상 주문 |
| 주문 목록 | Query | page | 1부터. 기본 1 |
| 주문 목록 | Query | size | 기본 10, 최대 100 |

응답 데이터입니다.

| 기능 | 응답 | 필드 |
|---|---|---|
| 주문하기·주문 확인 조회 | OrderCheckoutResponse | orderId, orderNumber, status, orderedAt, listPriceAmount(정가 합), discountAmount(할인 금액), payAmount(결제할 금액), items |
| 주문 목록 | PageResponse<OrderSummaryResponse> | orderId, orderNumber, orderedAt, payAmount, status |
| 주문 상세 | OrderDetailResponse | OrderCheckoutResponse의 필드, confirmedAt(구매 확정 시각, 확정 전 null). items는 OrderDetailItemResponse |
| 구매 확정 | OrderConfirmResponse | orderId, status |
| 주문 상품(items) | OrderItemResponse | productId, productName, productImageUrl, quantity, listPrice(주문 당시 정가), unitPrice(실제 단가), lineAmount(단가 × 수량), saleType(`NONE`·`SPECIAL`·`FLASH_DROP`) |
| 주문 상세 상품(items) | OrderDetailItemResponse | OrderItemResponse의 필드, confirmedQuantity(확정 수량, 확정 전 null) |

오류입니다. 401은 모든 기능에 공통입니다.

| 기능 | HTTP | 코드 | 조건 |
|---|---|---|---|
| 주문하기 | 400 | 공통 | 키 없음·64자 초과, 항목 없음·50개 초과 |
| 주문하기 | 404 | `CART_001` | 장바구니 항목 없음 또는 타인 항목 |
| 주문하기 | 404 | `PRODUCT_001` | 상품 없음 |
| 주문하기 | 409 | `PRODUCT_002` | 재고 부족·품절(번개 드롭 남은 수량 0 포함) |
| 주문하기 | 409 | `PRODUCT_005` | 판매 중지 상품 |
| 주문하기 | 409 | `ORDER_010` | 단가나 할인 구분이 주문 시점과 다름 |
| 주문하기 | 409 | `CART_003` | 번개 드롭 구매 한도 초과 |
| 주문하기 | 409 | `ORDER_006` | 같은 키로 다른 상품·수량을 보냄 |
| 주문하기 | 409 | `ORDER_005` | 주문번호 충돌. 다시 주문 |
| 주문하기 | 503 | `ORDER_013` | 주문번호를 만들 수 없음(Redis 장애). 다시 주문 |
| 주문 확인 조회 | 404 | `ORDER_001` | 주문 없음 또는 타인 주문 |
| 주문 확인 조회 | 409 | `ORDER_007` | 이미 결제 완료 |
| 주문 확인 조회 | 409 | `ORDER_008` | 취소된 주문 |
| 주문 취소 | 404 | `ORDER_001` | 주문 없음, 타인 주문, 이미 취소해 지운 주문 |
| 주문 취소 | 409 | `ORDER_007` | 이미 결제 완료 |
| 주문 취소 | 409 | `ORDER_008` | 취소된 주문 |
| 주문 취소 | 409 | `ORDER_012` | 결제를 시도한 주문 |
| 주문 목록 | 400 | 공통 | page·size 범위 오류 |
| 주문 상세 | 404 | `ORDER_001` | 주문 없음 또는 타인 주문 |
| 구매 확정 | 404 | `ORDER_001` | 주문 없음 또는 타인 주문 |
| 구매 확정 | 409 | `ORDER_009` | 결제 완료 전 |
| 구매 확정 | 409 | `ORDER_008` | 취소된 주문 |
| 구매 확정 | 409 | `ORDER_011` | 요청 상태 환불이 있음 |
| 구매 확정 | 409 | `ORDER_014` | 전액 환불된 주문 |

- 상태 값: `PENDING`(결제 대기), `PAID`(결제 완료), `CANCELLED`(취소), `CONFIRMED`(구매 확정), `REFUNDED`(전액 환불).
- **재고는 주문을 만들 때 줄임**: 주문하기에서 상품 재고를 빼고, 번개 드롭은 남은 수량도 뺍니다. 하나라도 실패하면 모두 롤백됩니다. 장바구니 항목은 주문하기에서 지우지 않습니다.
- **가격 확인**: 요청의 `unitPrice`·`saleType`이 주문 시점의 가격·할인 구분과 하나라도 다르면 오르든 내리든 `ORDER_010`으로 거절합니다. 오류 문구는 "가격 변경", "할인 시작", "할인 종료" 중 하나와 상품 이름입니다.
- **중복 요청**: 같은 회원이 같은 `Idempotency-Key`로 다시 보내면 새 주문을 만들지 않고 처음 주문을 200으로 돌려줍니다. 상품·수량이 처음 요청과 다르면 `ORDER_006`입니다. 같은 회원의 주문하기는 장바구니 행을 잠가 하나씩 처리합니다.
- **번개 드롭**: 1인 1개이고 이미 산 번개 드롭을 다시 주문하면 `CART_003`입니다. 남은 수량이 0이면 품절로 거절합니다.
- **취소**: 결제를 시도하지 않은 결제 대기 주문만 취소할 수 있습니다. 상품 재고와 번개 드롭 남은 수량을 되돌린 뒤 주문 상품과 주문을 지웁니다. 지운 주문을 다시 취소하면 404(`ORDER_001`)이고 재고는 한 번만 돌아옵니다. 결제를 시도한 주문은 결제가 실패했어도 `ORDER_012`로 거절합니다.
- **주문 만료**: 결제를 시도하지 않은 결제 대기 주문은 생성 5분 뒤 서버가 취소와 같은 방법으로 지웁니다. 주문 확인 화면을 탭 닫기 등으로 떠나 취소 요청이 오지 않은 주문도 이렇게 정리됩니다.
- **결제 정리**: 결제를 시도한 결제 대기 주문은 진행 중 결제가 없고 마지막 결제 뒤 10분이 지나면 서버가 취소 상태로 바꾸고 재고를 되돌립니다. 결제 기록은 남습니다. 그 전에는 같은 주문으로 다시 결제할 수 있습니다.
- **구매 확정**: 결제 완료 주문만 확정하고 확정 시각을 남깁니다. 이미 확정된 주문을 다시 확정하면 같은 200 응답을 줍니다. 요청 상태 환불이 있으면 `ORDER_011`로 거절하고, 실패한 환불은 막지 않습니다. 전액 환불된 주문은 `ORDER_014`로 거절합니다.
- **자동 구매 확정**: 결제 완료 시각(paidAt)부터 7일이 지난 PAID 주문을 매시간 확정합니다. 요청 상태 환불이 있으면 건너뛰고 다음 배치에서 다시 확인합니다.
- **확정 수량**: 구매 확정 때 주문 상품마다 주문 수량에서 완료된 환불 수량을 뺀 값을 저장하고, 주문 상세의 `confirmedQuantity`로 보여 줍니다. 확정 뒤에는 환불할 수 없어 바뀌지 않습니다.
- **전액 환불**: 환불 도메인이 전액 환불이라고 판단하면 주문 상태가 `REFUNDED`가 됩니다. API로 바꾸는 상태가 아닙니다.
- **목록**: 결제 대기·결제 완료·구매 확정·전액 환불 주문만 최신 주문부터 보여 주고 취소된 주문은 뺍니다. 페이지 번호는 1부터입니다.
- **본인 소유 검증**: 타인의 주문도 없는 주문과 같은 404(`ORDER_001`)로 답합니다.
- **주문 금액**: 주문 상품에 주문 당시 정가·단가·상품 이름·이미지를 저장하므로 상품이 바뀌어도 주문 화면의 금액은 바뀌지 않습니다.

## 환불 API

상태: **구현 완료** · 담당: 결제·환불 · 근거: 실제 백엔드 컨트롤러 및 서비스 구현

경로는 prefix(/api)를 뺀 값입니다. 회원 번호는 로그인 정보에서만 읽고 요청에는 받지 않습니다. 환불 금액은 요청으로 받지 않고 주문 당시 단가 × 수량으로 계산합니다.

| 기능 | Method / Path | 권한 | 성공 응답 |
|---|---|---|---|
| 환불 신청 | POST /refunds | 로그인 | 201(완료 또는 요청 상태) |
| 환불 내역 | GET /refunds?orderId= | 로그인 | 200 |
| 환불 요약 | GET /refunds/summaries?orderIds= | 로그인 | 200 |
| 환불 가능 수량 | GET /refunds/refundable-items?orderId= | 로그인 | 200 |

요청 값입니다.

| 기능 | 위치 | 이름 | 설명 |
|---|---|---|---|
| 환불 신청 | Body | orderId | 환불할 주문. 필수 |
| 환불 신청 | Body | items | 환불할 상품. 1개 이상, 같은 상품은 한 번만 |
| 환불 신청 | Body | items[].orderItemId | 주문 상품 번호. 필수 |
| 환불 신청 | Body | items[].quantity | 환불 수량. 1 이상 |
| 환불 신청 | Body | reasonType | `CHANGE_OF_MIND`·`ORDER_MISTAKE`·`NO_LONGER_NEEDED`·`ETC`. 필수 |
| 환불 신청 | Body | reasonDetail | 상세 사유. 500자 이하, 생략 가능 |
| 환불 내역·환불 가능 수량 | Query | orderId | 대상 주문 |
| 환불 요약 | Query | orderIds | 주문 번호 목록(쉼표 구분). 빈 값·중복을 뺀 고유 주문 1~50건 |

응답 데이터입니다.

| 기능 | 응답 | 필드 |
|---|---|---|
| 환불 신청 | RefundResponse | refundId, status(`COMPLETED`·`REQUESTED`), refundAmount, refundedAt(완료 전 null) |
| 환불 내역 | List<RefundHistoryResponse> | refundId, status, reasonType, reasonDetail, refundAmount, requestedAt, refundedAt, items |
| 환불 내역 상품(items) | RefundHistoryItemResponse | orderItemId, productName, productImageUrl, unitPrice, quantity, amount |
| 환불 요약 | List<RefundSummaryResponse> | orderId, refundStatus(`NONE`·`IN_PROGRESS`·`PARTIAL`·`FULL`), refundedAmount(완료된 환불 금액 합) |
| 환불 가능 수량 | List<RefundableItemResponse> | orderItemId, productId, productName, productImageUrl, unitPrice, orderedQuantity, refundedQuantity, refundableQuantity, saleType, refundable |

오류입니다. 401은 모든 기능에 공통입니다.

| 기능 | HTTP | 코드 | 조건 |
|---|---|---|---|
| 환불 신청 | 400 | `C001` | 값 누락·형식 오류, 수량 1 미만, 상세 사유 500자 초과, 같은 상품 중복 |
| 환불 신청 | 404 | `ORDER_001` | 주문 없음 또는 타인 주문 |
| 환불 신청 | 409 | `REFUND_010` | 구매 확정된 주문 |
| 환불 신청 | 400 | `REFUND_003` | 결제 완료가 아닌 주문 |
| 환불 신청 | 429 | `REFUND_007` | 같은 주문에 진행 중(요청 상태) 환불이 있음 |
| 환불 신청 | 404 | `REFUND_004` | 그 주문의 상품이 아님 |
| 환불 신청 | 400 | `REFUND_011` | 특가·번개 드롭 상품 |
| 환불 신청 | 400 | `REFUND_005` | 남은 환불 가능 수량 초과 |
| 환불 신청 | 409 | `REFUND_012` | PG가 취소를 거절함. 환불은 실패로 기록 |
| 환불 내역 | 404 | `ORDER_001` | 주문 없음 또는 타인 주문 |
| 환불 요약 | 400 | `REFUND_013` | 고유 주문이 없거나 50건 초과 |
| 환불 가능 수량 | 404 | `ORDER_001` | 주문 없음 또는 타인 주문 |
| 환불 가능 수량 | 409 | `REFUND_010` | 구매 확정된 주문 |
| 환불 가능 수량 | 400 | `REFUND_003` | 결제 완료가 아닌 주문 |

- 상태 값: `REQUESTED`(요청, PG 취소 대기), `COMPLETED`(완료), `FAILED`(실패).
- **환불 신청**: 신청하면 바로 PG에서 환불 금액만큼 부분 취소합니다. 성공하면 완료(`COMPLETED`)로 응답하고 환불 수량만큼 재고를 되돌립니다. PG 결과를 확정하지 못하면 요청 상태(`REQUESTED`)로 201을 주고, 취소 웹훅이나 10분 뒤 환불 정리가 완료 또는 실패로 마무리합니다.
- **환불 가능 조건**: 결제 완료 주문의 일반 상품만 환불합니다. 구매 확정 뒤에는 환불할 수 없고(`REFUND_010`), 전액 환불된 주문은 결제 완료가 아니라 `REFUND_003`입니다.
- **환불 뒤 주문 상태**: 환불이 완료돼 환불 가능한 일반 상품이 모두 환불되면 주문 상태가 바뀝니다. 특가·번개 드롭 상품이 없으면 `REFUNDED`(전액 환불), 있으면 바로 `CONFIRMED`(구매 확정, 확정 수량 기록)입니다. 부분 환불이면 `PAID`로 남습니다. 주문 상태 변경이 실패해도 환불은 완료로 응답하고, 서버의 환불 정리가 5분 주기로 주문 상태를 맞춥니다.
- **수량**: 요청·완료 상태 환불 수량을 누적으로 보고 주문 수량을 넘지 않게 합니다. 실패한 환불 수량은 다시 신청할 수 있습니다.
- **중복 신청**: 같은 주문에 진행 중 환불이 있으면 `REFUND_007`입니다. 같은 환불은 멱등 키로 PG에서 한 번만 취소됩니다.
- **환불 내역**: 상태와 관계없이 최신 환불부터 돌려줍니다. 구매 확정된 주문의 지난 환불도 보여 줍니다. PG 오류 원문(실패 사유)은 응답에 넣지 않습니다.
- **환불 요약**: 요청 상태 환불이 있으면 `IN_PROGRESS`, 환불 가능한 상품을 모두 환불했으면 `FULL`, 완료된 환불이 있으면 `PARTIAL`, 그 밖은 `NONE`입니다. 타인 주문과 없는 주문은 빼고 요청한 순서대로 돌려줍니다.
- **환불 가능 수량**: 주문 상품 순서대로 돌려줍니다. 특가·번개 드롭 상품은 `refundable=false`, `refundableQuantity=0`입니다.
- **본인 소유 검증**: 타인 주문은 없는 주문과 같은 404(`ORDER_001`)로 답합니다.

## 클릭 수 기반 할인 상품 API

상태: **구현 완료** · 담당: 장준혁 · 근거: 실제 백엔드 컨트롤러 및 서비스 구현

| 기능 | Method / Path | 성공 응답 |
|---|---|---|
| 클릭 수 기반 할인 상품 | GET /sales/popular | 200, `List<PopularSaleResponse>` |

- 메인 화면 할인 섹션이 씁니다. 로그인하지 않아도 부를 수 있고, 요청 값은 없습니다.
- `PopularSaleResponse`: productId, name, imageUrl, price(정가), sale(`SaleResponse`).
- **대상**: 진행 중인 특가·번개 드롭 상품입니다. 품절·판매 중지·번개 드롭 남은 수량 0인 상품은 뺍니다.
- **순서**: 오늘을 포함한 최근 7일 검색 결과 클릭이 많은 순입니다. 클릭이 같으면 할인율이 높은 순, 그것도 같으면 상품 ID 순입니다. 최대 6개입니다.

## 할인 행사 API

상태: **구현 완료** · 담당: 장준혁 · 근거: 실제 백엔드 컨트롤러 및 서비스 구현

경로는 prefix(/api)를 뺀 값입니다. 모두 관리자 전용입니다(일반 회원 403, 로그인 안 하면 401).

| 기능 | Method / Path | 성공 응답 |
|---|---|---|
| 특가 등록 | POST /admin/sale-events/special | 201, `SaleEventsCreateResponse`(saleEventIds) |
| 번개 드롭 등록 | POST /admin/sale-events/flash-drop | 201, `SaleEventsCreateResponse`(saleEventIds) |
| 등록 후보 상품 조회 | GET /admin/sale-events/products | 200, `PageResponse<SaleCandidateProductResponse>` |
| 할인 행사 목록 | GET /admin/sale-events | 200, `PageResponse<SaleEventListResponse>` |
| 할인 행사 상세 | GET /admin/sale-events/{saleEventId} | 200, `SaleEventDetailResponse` |
| 할인 행사 수정 | PATCH /admin/sale-events/{saleEventId} | 200, 공통 성공 응답 |
| 할인 행사 종료 | PATCH /admin/sale-events/{saleEventId}/end | 200, 공통 성공 응답 |
| 할인 행사 삭제 | DELETE /admin/sale-events/{saleEventId} | 200, 공통 성공 응답 |

요청 값:

| 기능 | 요청 |
|---|---|
| 특가 등록 | Body: `startsAt`, `endsAt`, `items`: `[{ productId, discountRate(1~99) }]` |
| 번개 드롭 등록 | Body: 특가와 같고 항목마다 `limitedQuantity`(1 이상) 추가 |
| 등록 후보 상품 | Query: keyword(50자 이하), type(상품 종류), page, size(최대 100) |
| 목록 | Query: status, type, keyword, startFrom·startTo, endFrom·endTo, sort, direction(기본 DESC), page(기본 1), size(기본 10, 최대 100) |
| 수정 | Body: `startsAt`, `endsAt`, `items`: `[{ productId, discountRate, limitedQuantity? }]` |

주요 오류:

| 기능 | 오류 |
|---|---|
| 등록 | 400 시작 시각이 지금보다 과거·종료 시각이 시작 시각보다 늦지 않음·상품 중복·할인율 범위·한정 수량이 재고보다 많음, 404 상품 없음(`PRODUCT_001`), 409 이미 행사가 있는 상품(`SALE_001`)·판매 중지·품절 상품(`PRODUCT_005`) |
| 목록·후보 | 400 기간 순서·page·size 범위 |
| 상세 | 404 없는 행사(`SALE_002`) |
| 수정 | 400 입력값·상품 목록이 행사와 다름·번개 드롭 한정 수량 누락·특가에 한정 수량·한정 수량이 재고보다 많음, 404 `SALE_002`, 409 시작한 행사(`SALE_003`) |
| 종료 | 404 `SALE_002`, 409 진행 중이 아님(`SALE_004`) |
| 삭제 | 404 `SALE_002`, 409 시작 전이 아님(`SALE_005`) |

응답 필드:
- `SaleEventListResponse`(한 행): saleEventId, type, status, startsAt, endsAt, productId, productName, listPrice, salePrice, discountRate, limitedQuantity, remainingQuantity, soldQuantity.
- `SaleEventDetailResponse`: saleEventId, type, status, startsAt, endsAt, items(productId, productName, listPrice, salePrice, discountRate, limitedQuantity, remainingQuantity).
- `SaleCandidateProductResponse`: productId, productType, name, imageUrl, price, stock.
- 종류 값: `SPECIAL`(특가), `FLASH_DROP`(번개 드롭). 상태 값: `SCHEDULED`(예정), `ACTIVE`(진행 중), `ENDED`(종료).

- **상품마다 행사 하나**: 등록하면 상품마다 같은 기간의 할인 행사가 하나씩 만들어집니다. 한 상품은 예정·진행 중인 할인 행사에 하나만 들 수 있습니다. 종료된 행사에 들었던 상품과, 종료 시각이 지났지만 상태 갱신이 아직 닫지 않은 행사의 상품은 다시 등록할 수 있습니다. 한 상품이라도 거절되면 모두 저장되지 않습니다.
- **할인가**: 정가와 할인율(1~99%)로 서버가 `정가 × (100 − 할인율) / 100`의 원 아래를 버려 계산합니다.
- **번개 드롭**: 한정 수량을 정해 등록하면 남은 수량이 한정 수량과 같게 시작합니다. 1인 1개입니다.
- **상태 변화**: 진행 여부는 시각(시작 ≤ 지금 < 종료)으로 판단합니다. 1분마다 도는 상태 갱신이 시작 시각이 지난 예정을 진행 중으로, 종료 시각이 지난 행사를 종료로 바꿉니다.
- **수정**: 시작 전 할인 행사만 고칠 수 있습니다. 행사에 든 상품 목록은 그대로 보내야 하고, 번개 드롭은 한정 수량이 필수이며 특가는 한정 수량을 보내지 않습니다. 정가는 상품의 지금 정가로 다시 읽으며 번개 드롭 남은 수량은 한정 수량과 같게 다시 맞춥니다. 하나라도 거절되면 아무것도 바뀌지 않습니다.
- **종료**: 시각상 진행 중인 할인 행사만 종료합니다(상태 갱신이 늦어 아직 예정으로 남아 있어도 시작 시각이 지났으면 종료할 수 있습니다). 상태만 종료로 바꾸고 종료 시각과 행사 항목은 그대로이며, 종료한 행사는 다시 열리지 않습니다.
- **삭제**: 시작 전 할인 행사만 지웁니다. 행사와 행사 항목이 함께 지워집니다.
- **동시 요청**: 수정·종료·삭제는 행사 행을 잠근 뒤 상태를 확인하므로 상태 갱신과 겹쳐도 한쪽이 끝난 뒤 처리됩니다.
- **정가 표시**: 목록·상세의 정가는 예정·진행 중이면 상품의 현재 정가, 종료되면 행사 항목에 저장된 등록 당시 정가입니다.
- **목록**: 상품마다 한 행으로 나옵니다. 시작·종료 날짜는 하나만 주면 그 하루로 봅니다. 정렬 기준은 상품 이름·종류·상태·정가·할인가·할인율·한정 수량·남은 수량·판매량·시작·종료이고, 정렬 기준을 주지 않으면 최신 행사 순이고, 기준이 같으면 최신 행사가 먼저입니다. 판매량은 결제 완료·구매 확정 주문의 수량만 셉니다.
- **등록 후보 상품**: 판매 중이고 재고가 있으며 예정·진행 중인 할인 행사에 들지 않은 상품만 나옵니다.

## 결제 API

상태: **구현 완료** · 담당: 결제·환불 · 근거: 실제 백엔드 컨트롤러 및 서비스 구현

경로는 prefix(/api)를 뺀 값입니다. 회원 번호는 로그인 정보에서만 읽고 요청에는 받지 않습니다. 결제 금액은 요청으로 받지 않고 주문 금액을 씁니다.

| 기능 | Method / Path | 권한 | 성공 응답 |
|---|---|---|---|
| 결제 준비 | POST /orders/{orderId}/payments | 로그인 | 200 |
| 결제 확정 | POST /payments/confirm | 로그인 | 200 |
| 결제창 닫기 | POST /payments/{portonePaymentId}/fail | 로그인 | 200(데이터 없음) |
| PortOne 웹훅 | POST /webhooks/portone | 공개(서명 검증), CSRF 제외 | 200(데이터 없음) |

요청 값입니다.

| 기능 | 위치 | 이름 | 설명 |
|---|---|---|---|
| 결제 준비 | Path | orderId | 결제할 주문 |
| 결제 준비 | Body | pgProvider | 결제 수단(결제대행사). `KAKAOPAY`(카카오페이)·`TOSSPAY`(토스페이)·`KICC`(이지페이)·`INICIS`(KG이니시스)·`TOSSPAYMENTS`(토스페이먼츠). 필수 |
| 결제 확정 | Body | orderId | 결제할 주문. 필수 |
| 결제 확정 | Body | portonePaymentId | 결제 준비에서 받은 결제 ID. 필수 |
| 결제창 닫기 | Path | portonePaymentId | 닫은 결제창의 결제 ID |
| PortOne 웹훅 | Header | webhook-id, webhook-timestamp, webhook-signature | PortOne 서명 헤더. 없거나 틀리면 401 |
| PortOne 웹훅 | Body | (원문) | 서명 검증을 위해 원문 그대로 받음 |

응답 데이터입니다.

| 기능 | 응답 | 필드 |
|---|---|---|
| 결제 준비 | PaymentPrepareResponse | orderId, portonePaymentId(결제창에 넘길 결제 ID, 40자), orderName(예: "대파 외 2건"), amount(결제 금액), expiresAt(결제 대기 만료 시각), pgProvider(고른 결제대행사), channelKey(그 결제대행사의 PortOne 채널 키), payMethod(`EASY_PAY`·`CARD`), easyPayProvider(`KAKAOPAY`·`TOSSPAY`, 간편결제가 아니면 null) |
| 결제 확정 | PaymentConfirmResponse | id, orderId, portonePaymentId, amount, status(`PAID`), paidAt, pgProvider(실제로 결제된 결제대행사. PG에서 확인하지 못하면 고른 값) |

오류입니다. 401은 웹훅을 뺀 모든 기능에 공통입니다.

| 기능 | HTTP | 코드 | 조건 |
|---|---|---|---|
| 결제 준비 | 400 | `C001` | 결제대행사 누락·모르는 값 |
| 결제 준비 | 404 | `ORDER_001` | 주문 없음 |
| 결제 준비 | 403 | `ORDER_003` | 타인 주문 |
| 결제 준비 | 409 | `PAYMENT_007` | 주문이 결제 대기가 아님(결제 완료·취소). 이전 결제가 PG에서 끝나 지금 승인된 경우 포함 |
| 결제 준비 | 409 | `PAYMENT_008` | 같은 주문의 결제 준비가 동시에 들어와 다른 요청이 먼저 만듦 |
| 결제 준비 | 500 | `COMMON_002` | 이전 결제 대기 건의 PG 조회 실패. 새 결제를 만들지 않음 |
| 결제 확정 | 400 | `C001` | 값 누락, 요청의 주문 ID가 결제의 주문과 다름 |
| 결제 확정 | 404 | `PAYMENT_001` | 결제 없음 |
| 결제 확정 | 403 | `ORDER_003` | 타인 결제 |
| 결제 확정 | 400 | `PAYMENT_004` | PG에 결제 없음·진행 중·실패, PG 응답에 금액·결제 시각 없음 |
| 결제 확정 | 400 | `PAYMENT_002` | PG 금액과 결제 금액이 다름. 실패 처리 후 PG 취소 |
| 결제 확정 | 409 | `PAYMENT_006` | 만료 시각 이후 결제됨. 실패 처리 후 PG 취소 |
| 결제 확정 | 409 | `PAYMENT_007` | 주문이 이미 다른 결제로 결제 완료됐거나 취소됨. 실패 처리 후 PG 취소 |
| 결제 확정 | 409 | `PAYMENT_005` | 이미 실패 처리된 결제(창 닫기·새 결제로 정리·PG 실패) |
| 결제창 닫기 | 404 | `PAYMENT_001` | 결제 없음 |
| 결제창 닫기 | 403 | `ORDER_003` | 타인 결제 |
| 결제창 닫기 | 409 | `PAYMENT_005` | 결제 대기가 아님, PG에서 이미 결제됨 |
| PortOne 웹훅 | 401 | `WEBHOOK_001` | 서명 헤더 없음·서명 불일치·5분이 지난 웹훅. 기록하지 않음 |
| PortOne 웹훅 | 5xx | `COMMON_002`, `PAYMENT_009` 등 | 처리 실패(PG 조회 실패, 보상 취소 거절 등). `FAILED`로 기록하고 PortOne 재전송을 받음 |

- 상태 값: `READY`(결제 대기), `PAID`(결제 완료), `FAILED`(실패).
- **결제 수단**: 카카오페이·토스페이는 간편결제, 이지페이·KG이니시스·토스페이먼츠는 카드 결제창입니다. 프론트는 응답의 channelKey·payMethod·easyPayProvider로 결제창을 엽니다. 채널 키는 서버 설정(`portone.channels`)에만 있습니다.
- **결제 준비**: 결제하기를 누를 때마다 고른 결제대행사로 새 결제 ID를 받습니다. 다시 결제할 때 다른 결제대행사를 고를 수 있습니다. 만료 시각은 10분 뒤입니다. 남은 결제 대기 건이 있으면 PG를 조회해, 이미 결제됐으면 그 결제를 승인하고 새 결제는 만들지 않습니다(`PAYMENT_007`). 아니면 이전 건을 실패(`SUPERSEDED`)로 정리한 뒤 새로 만듭니다.
- **결제 확정**: 서버가 PortOne에 결제 건을 직접 조회해 결제 완료·금액·결제 시각을 확인한 뒤에만 승인하고 주문을 결제 완료로 바꿉니다. 결제창이 성공으로 끝나도 이 응답이 200이어야 결제 성공입니다. PG가 알려 준 결제대행사가 고른 값과 달라도 거절하지 않고 실제 값을 남깁니다. 승인 뒤 서버가 결제한 상품을 장바구니에서 지웁니다.
- **중복 확정**: 이미 결제 완료된 결제를 다시 확정하면 PG 조회 없이 같은 200 응답을 줍니다. 확정 요청과 웹훅이 동시에 와도 상태는 한 번만 바뀝니다.
- **받지 않은 결제**: 금액 불일치·만료 후 결제·결제할 수 없는 주문은 실패로 기록한 뒤 PG에서 전액 취소합니다. 취소가 실패해도 거절 이유로 응답하고, 웹훅과 재시도 스케줄러가 다시 취소합니다.
- **실패한 결제의 오류**: 웹훅·스케줄러가 먼저 실패 처리한 결제도 실패 사유에 맞는 오류(`PAYMENT_002`·`PAYMENT_006`·`PAYMENT_007`·`PAYMENT_005`)로 답합니다.
- **결제창 닫기**: 결제 대기 건만 실패(`USER_CANCELLED`)로 바꾸고 주문은 결제 대기로 남아 다시 결제할 수 있습니다. 프론트는 지금 부르지 않고 만료 스케줄러가 10분 뒤 정리합니다.
- **웹훅**: `Transaction.Paid`·`Failed`·`Cancelled`·`PartialCancelled`만 처리하고 나머지는 기록만 합니다. 본문 값을 믿지 않고 PG를 다시 조회해 상태를 맞춥니다. 취소·부분 취소 웹훅은 환불 쪽에도 넘깁니다. 같은 `webhook-id`가 다시 오면 처리가 끝난 경우 200만 돌려줍니다.
- **본인 소유 검증**: 타인의 주문·결제는 403(`ORDER_003`)으로 답하고 PG를 조회하지 않습니다.

## 정산 API

상태: **구현 완료** · 담당: 결제·환불 · 근거: 실제 백엔드 컨트롤러 및 서비스 구현 · 설계: [정산 설계](../architecture/domains/settlement.md)

경로는 prefix(/api)를 뺀 값입니다. 날짜는 `YYYY-MM-DD`이고 한국 시간 기준이며, 시작일과 종료일을 모두 포함합니다.

| 기능 | Method / Path | 권한 | 성공 응답 |
|---|---|---|---|
| 정산 조회 | GET /admin/settlements?unit=&startDate=&endDate= | ADMIN | 200 |
| 결제 건별 조회 | GET /admin/settlements/payments?startDate=&endDate=&page=&size= | ADMIN | 200 |
| 오늘 매출 | GET /admin/settlements/today | ADMIN | 200 |

요청 값입니다.

| 기능 | 위치 | 이름 | 설명 |
|---|---|---|---|
| 정산 조회 | Query | unit | `DAY`·`WEEK`(월~일)·`MONTH`(1일~말일). 필수 |
| 정산 조회·결제 건별 | Query | startDate, endDate | 필수. 종료일은 시작일 이후·오늘까지, 시작일 + 3개월 전날까지 |
| 결제 건별 | Query | page | 1부터. 기본 1, 최대 10000 |
| 결제 건별 | Query | size | 기본 20, 1~100 |

응답 데이터입니다.

| 기능 | 응답 | 필드 |
|---|---|---|
| 정산 조회 | SettlementResponse | summary(기간 전체 합계, SalesAmountResponse), periods(List<SettlementPeriodResponse>) |
| 금액 묶음 | SalesAmountResponse | paidAmount(결제 금액), refundedAmount(환불 금액), netAmount(순매출, 음수 가능), paidCount(결제 건수), refundedCount(환불 건수, 취소 이력 한 건이 1건) |
| 기간 행 | SettlementPeriodResponse | startDate, endDate(일 단위면 같음. 조회 기간에서 잘린 주·월은 잘린 날짜), paidAmount, refundedAmount, netAmount, paidCount, refundedCount |
| 결제 건별 | PageResponse<SettlementPaymentResponse> | paymentId, paidAt(결제 완료 시각), orderNumber, buyerName(회원 닉네임, 없으면 null), paidAmount, refundedAmount, netAmount |
| 오늘 매출 | TodaySalesResponse | salesDate(오늘), sales(SalesAmountResponse) |

오류입니다. 로그인하지 않으면 401, 관리자가 아니면 403입니다.

| 기능 | HTTP | 코드 | 조건 |
|---|---|---|---|
| 정산 조회·결제 건별 | 400 | `C001` | 값 누락·형식 오류, 종료일이 시작일보다 빠름, 종료일이 내일 이후, 조회 기간 3개월 초과, page·size 범위 밖 |

- **매출 기준**: 결제 이력(`payment_histories`)으로 셉니다. 결제는 결제 완료 시점, 환불(콘솔 취소 포함)은 환불 완료 시점의 매출입니다. 결제 상태가 결제 완료인 결제만 세므로 받지 않은 결제와 그 보상 취소는 빠집니다.
- **정산 조회**: 어제까지는 일별 집계(daily_sales)를 읽고, 집계되지 않은 날은 그 자리에서 집계해 채운 뒤 응답합니다. 기간에 오늘이 있으면 오늘 지금까지의 매출을 붙입니다. 주·월은 일별 값을 묶어 더합니다.
- **결제 건별**: 기간 안에 결제·환불이 있었던 결제 한 건이 한 행이고, 마지막 결제·환불이 최근인 결제가 먼저입니다. 금액은 기간 안에서 일어난 것만 세므로 기간 전에 결제하고 기간 안에 환불했으면 paidAmount가 0입니다. 모든 행을 더하면 정산 조회의 summary와 같습니다.
- **오늘 매출**: 오늘 0시부터 조회 시점까지를 바로 셉니다. 저장하지 않습니다.

## 알림 API

상태: **구현 완료** · 담당: 장준혁 · 근거: 실제 백엔드 컨트롤러 및 서비스 구현

경로는 prefix(/api)를 뺀 값입니다. 회원 번호는 로그인 정보에서만 읽고 요청에는 받지 않습니다.

| 기능 | Method / Path | 권한 | 성공 응답 |
|---|---|---|---|
| 내 알림 목록 | GET /notifications | 로그인 | 200 |
| 안 읽은 알림 수 | GET /notifications/unread-count | 로그인 | 200 |
| 실시간 알림 스트림 | GET /notifications/stream | 로그인 | 200 (text/event-stream) |
| 전부 읽음 | PATCH /notifications/read-all | 로그인 | 200 |
| 알림 지우기 | DELETE /notifications/{notificationId} | 로그인 | 200 |
| 모두 지우기 | DELETE /notifications | 로그인 | 200 |

요청 값입니다.

| 기능 | 위치 | 이름 | 설명 |
|---|---|---|---|
| 내 알림 목록 | Query | beforeId | 이 ID보다 오래된 알림부터 받습니다. 1 이상, 생략하면 가장 최신부터 |
| 전부 읽음 | Body | lastNotificationId | 화면이 받은 가장 최신 알림 ID. 1 이상, 필수 |
| 알림 지우기 | Path | notificationId | 지울 알림 ID |

응답 데이터입니다.

| 기능 | 응답 | 필드 |
|---|---|---|
| 내 알림 목록 | NotificationListResponse | notifications(최신순), hasNext |
| 안 읽은 알림 수 | UnreadNotificationCountResponse | unreadCount |
| 실시간 알림 스트림 | SSE 이벤트 `notification` | 내용 없는 새 알림 신호(`refresh`). 화면이 목록·안 읽은 수 API로 직접 받아갑니다. 공통 응답으로 감싸지 않음 |
| 실시간 알림 스트림 | SSE 이벤트 `chat` | 상담 바뀜 신호. data는 JSON `{"sessionId":15,"status":"COMPLETED"}`(status: `ACTIVE`·`COMPLETED`·`EXPIRED`). 메시지 내용은 싣지 않음 |
| 전부 읽음 | 공통 성공 응답 | 없음 |
| 알림 지우기, 모두 지우기 | 공통 성공 응답 | 없음 |

오류입니다. 401은 모든 기능에 공통입니다.

| 기능 | HTTP | 코드 | 조건 |
|---|---|---|---|
| 내 알림 목록 | 400 | 공통 | beforeId가 1 미만이거나 숫자가 아님 |
| 전부 읽음 | 400 | 공통 | lastNotificationId가 없거나 1 미만 |
| 알림 지우기 | 400 | 공통 | notificationId가 숫자가 아님 |

알림 항목(`NotificationResponse`): notificationId, notificationType(`EXPIRATION`·`FLASH_DROP`·`COMMERCE_STOCKED`), content(문구), linkUrl(누르면 이동할 화면 경로), read(읽음 여부), createdAt(만든 시각).

- **목록 크기**: 한 번에 5개입니다. 크기는 요청으로 받지 않습니다.
- **더 보기**: 화면의 마지막 알림 ID를 beforeId로 보냅니다. 목록을 연 사이 새 알림이 와도 다음 알림이 밀리거나 겹치지 않습니다. `hasNext`가 false면 더 받을 알림이 없습니다.
- **전부 읽음**: 종 아이콘을 열 때 부릅니다. lastNotificationId 이하의 안 읽은 알림을 한 번에 읽음으로 바꾸고, 그 뒤 도착한 알림은 안 읽음으로 남습니다. 받은 알림이 없으면 부르지 않습니다. 같은 요청을 두 번 보내도 결과가 같습니다.
- **배지**: 전부 읽음 뒤에는 안 읽은 알림 수를 다시 불러옵니다. 화면 이동·탭 복귀·새로고침 때, 새 알림 신호가 올 때도 다시 불러옵니다.
- **실시간 알림 스트림**: `GET /notifications/stream`은 로그인한 회원의 SSE(`text/event-stream`) 연결입니다. 연결을 오래 쥐는 응답이라 공통 성공 응답으로 감싸지 않습니다. 새 알림이 저장되면 `notification` 이벤트로 내용 없는 `refresh` 신호를 보냅니다. 알림 내용은 싣지 않으므로 화면이 목록·안 읽은 수 API로 직접 받아갑니다. 연결 유지 시간은 30분, 조용한 연결이 끊기지 않도록 30초마다 하트비트(주석)를 보내며, 한 회원이 동시에 열 수 있는 연결은 5개입니다. 한도를 넘기면 가장 오래된 연결이 닫힙니다. 값은 `app.sse.*` 설정입니다. 비로그인은 401입니다. 끊기면 브라우저가 다시 연결하고, 다시 연결될 때 화면이 한 번 다시 조회해 놓친 알림을 메웁니다. 그 밖에 놓친 알림은 다음 조회 때 보입니다.
- **상담 바뀜 신호(2026-11-02 추가)**: 같은 스트림으로 `chat` 이벤트도 옵니다. 여러 창(브라우저·기기·서버)에서 AI 상담을 맞추는 신호입니다. data는 JSON 문자열 `{"sessionId":15,"status":"COMPLETED"}`이고, 회원 번호와 메시지 내용은 싣지 않으므로 화면이 상담·메시지 조회 API로 직접 받아갑니다. 커밋된 뒤에만 보내고, 실패해 되돌려진 변경은 보내지 않습니다. 보내는 때는 다음과 같습니다.
  - 새 상담을 만들었을 때 `ACTIVE`(상담 시작 API가 기존 진행 중 상담을 그대로 돌려줄 때는 보내지 않음)
  - 답(질문·답 한 쌍)을 저장했을 때 `ACTIVE`
  - 상담 종료 API, 요리 완료로 상담이 끝났을 때 `COMPLETED`
  - 30분 동안 대화가 없어 서버가 실제로 만료시켰을 때(다음 상담 시작·메시지 보내기 때) `EXPIRED`. 시간이 지나 화면에서만 만료로 보이는 상담은 신호가 없습니다.
  - 상담 시작 때 오래된 상담을 만료시키고 새로 만들면 `EXPIRED`, `ACTIVE` 순으로 두 번 옵니다.
  - 서버 간 전달은 알림(`notification:push`)과 따로인 Redis 채널 `chat:push`로 합니다. 놓친 신호는 다음 조회 때 메워집니다.
- **알림 지우기**: 지운 알림은 목록과 안 읽은 알림 수에서 빠집니다. 없는 알림, 다른 회원의 알림, 이미 지운 알림이면 아무것도 바꾸지 않고 200입니다.
- **모두 지우기**: 내 알림을 전부 지웁니다. 화면이 받지 않은 알림, 목록을 받은 뒤 도착한 알림도 함께 지워집니다.
- **본인 소유 검증**: 다른 회원의 알림 ID를 보내도 내 알림만 조회·변경되고 오류는 없습니다.

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
