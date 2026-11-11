# 회원·인증·관리자 API 명세

상태: 구현 완료 · 담당: cookiez · 공통 규칙: [API 계약](api-spec.md#공통-규칙) · 자세한 동작: [회원 설계](../architecture/domains/member.md)

공통 응답·오류 코드·페이지·날짜 형식은 [공통 규칙](api-spec.md#공통-규칙)을 따릅니다. 이 문서의 경로는 `/api`를 뺀 값이고, 오류 표에는 공통 오류(401 `A003`, 400 `C001`)를 빼고 이 API만의 오류를 적습니다. 관리자 API(`/admin/**`)는 USER가 부르면 403 `A004`입니다.

## 목차

- [이메일 중복 확인](#이메일-중복-확인)
- [닉네임 중복 확인](#닉네임-중복-확인)
- [회원가입](#회원가입)
- [인증 쿠키](#인증-쿠키)
- [CSRF 토큰](#csrf-토큰)
- [로그인](#로그인)
- [토큰 갱신](#토큰-갱신)
- [로그아웃](#로그아웃)
- [내 정보 조회](#내-정보-조회)
- [닉네임 변경](#닉네임-변경)
- [비밀번호 변경](#비밀번호-변경)
- [맛 선호 변경](#맛-선호-변경)
- [회원 목록](#회원-목록)
- [관리자 승격](#관리자-승격)
- [작업 이력 조회](#작업-이력-조회)
- [회원 응답 DTO](#회원-응답-dto)
- [오류 코드](#오류-코드)

## 이메일 중복 확인
`GET /members/email-availability` · 공개

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| email (query) | string | Y | 이메일 형식, 255자 이하 |

응답: `EmailAvailabilityResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| available | boolean | N | 쓸 수 있으면 true |

주의 사항
- 결과는 참고용입니다. 가입 요청 때 서버가 다시 검사합니다.

## 닉네임 중복 확인
`GET /members/nickname-availability` · 공개 (로그인해서 불러도 됨)

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| nickname (query) | string | Y | 앞뒤 공백을 지운 뒤 1~30자 |

응답: `NicknameAvailabilityResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| available | boolean | N | 쓸 수 있으면 true |

주의 사항
- 로그인한 회원이 자기 현재 닉네임을 보내면 true입니다.

## 회원가입
`POST /members` · 공개 (CSRF 헤더 없이 부를 수 있음)

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| email | string | Y | 이메일 형식, 255자 이하, 중복 불가 |
| password | string | Y | 8~30자, ASCII 영문·숫자·특수문자 각 1개 이상, 공백·한글 불가 |
| nickname | string | Y | 앞뒤 공백을 지운 뒤 1~30자, 중복 불가 |
| phone | string | Y | `010-XXXX-XXXX` 또는 `010XXXXXXXX`. 숫자 11자리로 저장, 중복 불가 |
| tastes | `Taste[]` | Y | 0개 이상(`[]` 가능), 같은 값 중복 불가, null 항목 불가 |

응답: `201` `SignUpResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| memberId | long | N | 만든 회원 ID |

| HTTP | 코드 | 조건 |
|---|---|---|
| 409 | M001 | 이메일 중복 |
| 409 | M002 | 닉네임 중복 |
| 409 | M009 | 전화번호 중복 |

주의 사항
- 역할은 항상 USER, 상태는 ACTIVE입니다. 요청에 `role`이 있어도 무시합니다.
- 가입만 하고 로그인 쿠키는 주지 않습니다.

## 인증 쿠키
로그인·갱신 응답이 주는 쿠키입니다. 프론트는 `withCredentials: true`로 보냅니다.

| 이름 | Path | 수명 | 속성 |
|---|---|---|---|
| access_token | `/api` | 15분 | HttpOnly, SameSite=Lax, Secure |
| refresh_token | `/api/auth` | 최초 로그인부터 7일까지 (갱신 때 남은 기간) | HttpOnly, SameSite=Lax, Secure |
| XSRF-TOKEN | `/` | 세션 | JavaScript가 읽음. [CSRF 토큰](#csrf-토큰)으로 받음 |

주의 사항
- Secure는 로컬 HTTP 개발에서만 `AUTH_COOKIE_SECURE=false`로 끕니다.
- CSRF 보호 대상(POST·PUT·PATCH·DELETE)은 `XSRF-TOKEN` 값을 `X-XSRF-TOKEN` 헤더로 보냅니다. 예외는 `POST /members`, `POST /auth/login`입니다.
- 403 `AUTH_002`(CSRF 실패)를 받으면 토큰을 다시 받아 한 번 재시도합니다.

## CSRF 토큰
`GET /auth/csrf` · 공개

요청: 없음

응답: `CsrfResponse` (`XSRF-TOKEN` 쿠키도 함께 설정)

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| headerName | string | N | 보낼 헤더 이름 (`X-XSRF-TOKEN`) |
| token | string | N | 헤더에 넣을 값 |

주의 사항
- 앱 시작, 로그인 직후, 토큰이 없어졌을 때 다시 받습니다.

## 로그인
`POST /auth/login` · 공개 (CSRF 헤더 없이 부를 수 있음)

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| email | string | Y | 이메일 형식 |
| password | string | Y | |

응답: `MyInfoResponse` ([회원 응답 DTO](#회원-응답-dto)) + `access_token`·`refresh_token` 쿠키

| HTTP | 코드 | 조건 |
|---|---|---|
| 401 | A001 | 없는 이메일 또는 틀린 비밀번호. 둘을 구분하지 않음 |
| 403 | A002 | 정지 계정. 비밀번호가 맞을 때만 |

주의 사항
- 이 API의 401·403은 세션 만료·권한 없음이 아니라 폼 오류로 보여 줍니다.
- `role`이 ADMIN이면 관리자 화면으로 이동합니다.

## 토큰 갱신
`POST /auth/refresh` · `refresh_token` 쿠키와 `X-XSRF-TOKEN` 헤더

요청: 본문 없음

응답: 본문 없음 + 새 `access_token`·`refresh_token` 쿠키

| HTTP | 코드 | 조건 |
|---|---|---|
| 401 | A005 | 갱신 쿠키 없음·위조·만료·폐기, 이미 교체된 이전 토큰, 30분 미사용 |
| 401 | A006 | 계정이 정지됨 |
| 403 | AUTH_002 | CSRF 헤더 없음·틀림 |

주의 사항
- 프론트는 동시에 난 401을 갱신 요청 한 번으로 모읍니다. 동작은 [회원 설계](../architecture/domains/member.md#인증-쿠키토큰세션)를 봅니다.

## 로그아웃
`POST /auth/logout` · 인증 쿠키와 `X-XSRF-TOKEN` 헤더

요청: 본문 없음

응답: 본문 없음 + 두 인증 쿠키 삭제

| HTTP | 코드 | 조건 |
|---|---|---|
| 403 | AUTH_002 | CSRF 헤더 없음·틀림 |

주의 사항
- 쿠키가 없거나 세션이 이미 폐기·만료돼도 200입니다.

## 내 정보 조회
`GET /members/me` · 로그인

요청: 없음

응답: `MyInfoResponse` ([회원 응답 DTO](#회원-응답-dto))

주의 사항
- 프론트는 앱 시작 때 불러 로그인 여부를 정합니다. 401 `A003`이면 비로그인입니다.

## 닉네임 변경
`PATCH /members/me/nickname` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| nickname | string | Y | 앞뒤 공백을 지운 뒤 1~30자 |

응답: 본문 없음

| HTTP | 코드 | 조건 |
|---|---|---|
| 409 | M002 | 다른 회원이 쓰는 닉네임 |

주의 사항
- 자기 닉네임을 그대로 보내면 200입니다.

## 비밀번호 변경
`PATCH /members/me/password` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| currentPassword | string | Y | 지금 비밀번호 |
| newPassword | string | Y | 가입과 같은 규칙(8~30자, ASCII 영문·숫자·특수문자 각 1개 이상) |

응답: 본문 없음

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | M003 | 현재 비밀번호가 틀림 (401이 아님) |
| 400 | M004 | 새 비밀번호가 현재와 같음 |

주의 사항
- 성공하면 이 기기의 로그인은 유지되고 다른 기기의 로그인은 끊깁니다.

## 맛 선호 변경
`PUT /members/me/tastes` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| tastes | `Taste[]` | Y | 목록 전체를 바꿈. 빈 목록이면 모두 지움. null 항목 불가 |

응답: 본문 없음

주의 사항
- 같은 값을 두 번 보내도 오류가 아니고 한 번만 저장합니다 (가입은 중복 시 400).

## 회원 목록
`GET /admin/members` · ADMIN

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| keyword (query) | string | N | 이메일 일부. 대소문자 무시 |
| role (query) | `USER`·`ADMIN` | N | 비우면 전체 |
| page (query) | int | N | 기본 1, 1 이상 |
| size (query) | int | N | 기본 20, 1 이상 |

응답: `PageResponse<MemberSummaryResponse>` (회원 ID 내림차순)

## 관리자 승격
`POST /admin/members/{memberId}/promotion` · ADMIN

요청: 본문 없음

응답: 본문 없음

| HTTP | 코드 | 조건 |
|---|---|---|
| 403 | A004 | 요청자가 ADMIN이 아님 (작업 이력에 DENIED) |
| 404 | M005 | 회원 없음 |
| 409 | M006 | 정지된 회원 |
| 409 | M007 | 이미 ADMIN |

주의 사항
- 권한 해제 API는 없습니다.

## 작업 이력 조회
`GET /admin/audit-logs` · ADMIN

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| from (query) | date | N | 이 날짜부터(포함). 기본 `to` 기준 7일 |
| to (query) | date | N | 이 날짜까지(포함). 기본 오늘 |
| action (query) | `PRODUCT_CREATE`·`PRODUCT_DELETE`·`PRICE_UPDATE`·`DEAL_ANALYSIS`·`DEAL_REVIEW`·`EVENT_REQUEST`·`ROLE_CHANGE` | N | |
| result (query) | `SUCCESS`·`FAILED`·`DENIED` | N | `FAILED`는 FAILED·DENIED를 함께 찾음 |
| targetType (query) | string | N | 50자 이하 |
| targetId (query) | long | N | 1 이상 |
| page (query) | int | N | 기본 1, 1 이상 |
| size (query) | int | N | 기본 20, 1 이상 |

응답: `PageResponse<AuditLogResponse>` (최신순)

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | `from`이 `to`보다 늦음 |

주의 사항
- 기록을 고치거나 지우는 API는 없습니다.

## 회원 응답 DTO

`MyInfoResponse` (로그인·내 정보 조회)

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| memberId | long | N | 회원 ID |
| email | string | N | 이메일 |
| nickname | string | N | 닉네임 |
| phone | string | Y | 숫자 11자리. 전화번호 필수 이전 회원·첫 관리자는 null |
| role | `USER`·`ADMIN` | N | 역할 |
| status | `ACTIVE`·`SUSPENDED` | N | 상태 |
| tastes | `Taste[]` | N | 맛 선호. 없으면 빈 목록 |
| createdAt | datetime | N | 가입 시각 |

`MemberSummaryResponse` (회원 목록)

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| memberId | long | N | 회원 ID |
| email | string | N | 이메일 |
| nickname | string | N | 닉네임 |
| role | `USER`·`ADMIN` | N | 역할 |
| status | `ACTIVE`·`SUSPENDED` | N | 상태 |
| createdAt | datetime | N | 가입 시각 |
| promotedAt | datetime | Y | 가장 최근 승격 성공 시각. 승격 이력이 없으면 null |
| promotedBy | string | Y | 승격한 관리자 이메일 |

`AuditLogResponse` (작업 이력)

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| auditLogId | long | N | 이력 ID |
| createdAt | datetime | N | 기록 시각 |
| actorEmail | string | N | 작업한 회원 이메일 |
| actorRole | `USER`·`ADMIN` | N | 작업 당시 역할 |
| action | 위 `action` 값 | N | 작업 종류 |
| targetType | string | Y | 대상 종류 |
| targetId | long | Y | 대상 ID |
| result | `SUCCESS`·`FAILED`·`DENIED` | N | 결과 |
| beforeValue | object | Y | 바뀌기 전 값 (민감 값 제외) |
| afterValue | object | Y | 바뀐 뒤 값 (민감 값 제외) |

`Taste`: `SALTY`(짠맛)·`SWEET`(단맛)·`SPICY`(매운맛)·`UMAMI`(감칠맛)·`MILD`(슴슴한맛)

## 오류 코드

이 문서의 API가 쓰는 회원·인증 코드입니다. 메시지는 백엔드 `ErrorCode` 그대로입니다.

| 코드 | HTTP | 이름 | 메시지 |
|---|---|---|---|
| A001 | 401 | LOGIN_FAILED | 이메일 또는 비밀번호가 올바르지 않습니다. |
| A002 | 403 | ACCOUNT_SUSPENDED | 정지된 계정입니다. |
| A003 | 401 | UNAUTHORIZED | 로그인이 필요합니다. |
| A004 | 403 | ADMIN_ACCESS_DENIED | 관리자 계정만 볼 수 있는 페이지입니다. |
| A005 | 401 | INVALID_REFRESH_TOKEN | 로그인이 만료되었어요. 다시 로그인해 주세요. |
| A006 | 401 | SESSION_REVOKED_BY_SUSPENSION | 계정 이용이 제한되어 로그인이 해제되었습니다. |
| AUTH_002 | 403 | FORBIDDEN | 권한이 없습니다. |
| M001 | 409 | DUPLICATE_EMAIL | 이미 가입된 이메일입니다. |
| M002 | 409 | DUPLICATE_NICKNAME | 이미 사용 중인 닉네임입니다. |
| M003 | 400 | INVALID_CURRENT_PASSWORD | 현재 비밀번호가 일치하지 않습니다. |
| M004 | 400 | SAME_AS_CURRENT_PASSWORD | 현재 비밀번호와 다른 비밀번호를 입력해 주세요. |
| M005 | 404 | MEMBER_NOT_FOUND | 회원을 찾을 수 없습니다. |
| M006 | 409 | PROMOTION_SUSPENDED | 정지된 회원은 관리자로 승격할 수 없습니다. |
| M007 | 409 | ALREADY_ADMIN | 이미 관리자인 회원입니다. |
| M009 | 409 | DUPLICATE_PHONE | 이미 사용 중인 전화번호입니다. |

---

아래 판매 예측·할인 후보 절은 다음 커밋에서 forecast-api.md·deal-analysis-api.md로 옮깁니다.

## 5. 판매 예측

예측 생성(일요일 22:00)과 실제 판매량 확정(월요일 06:00)은 서버 배치가 합니다. 화면은 조회만 합니다.

### 5-1. 주간 예측 조회

| 항목 | 내용 |
|---|---|
| **Method / Path** | `GET /admin/forecasts/{weekStart}` |
| **권한** | ADMIN |
| **화면** | A-04 다음 주 예상 TOP 10, A-05 예측 vs 실제 판매량 |

**경로**: `weekStart` = 대상 주의 월요일(`2026-10-05`). 월요일이 아니면 400.

**성공 응답** `200`

```json
{
  "weekStart": "2026-10-05",
  "weekEnd": "2026-10-11",
  "modelVersion": "fc-v0.3",
  "dataCutoffAt": "2026-10-04T18:00:00",
  "createdAt": "2026-10-04T22:00:00",
  "confirmed": true,
  "confirmedAt": "2026-10-12T06:00:00",
  "syntheticData": false,
  "previousWeekStart": "2026-09-28",
  "nextWeekStart": "2026-10-12",
  "summary": {
    "top10MatchCount": 7,
    "baselineTop10MatchCount": 5,
    "meanAbsoluteError": 27.1,
    "baselineMeanAbsoluteError": 34.6,
    "enteredCount": 3,
    "droppedCount": 3
  },
  "items": [
    {
      "productId": 1042,
      "productName": "국산 두부 300g",
      "predictedRank": 1,
      "predictedQty": 412,
      "baselineQty": 405,
      "actualRank": 1,
      "actualQty": 398,
      "rankChange": 0,
      "qtyError": 14,
      "status": "MATCHED"
    }
  ]
}
```

| 필드 | 설명 |
|---|---|
| confirmed | false면 실제 판매량이 아직 없음(A-04). `summary`와 `actual*`, `rankChange`, `qtyError`, `status`는 null |
| previousWeekStart / nextWeekStart | 실제 예측이 있는 이전·다음 주. 없으면 null(화면의 ‹ › 버튼 비활성) |
| summary.meanAbsoluteError | 예상 TOP 10 상품의 \|예상 − 실제\| 평균 |
| summary.baselineMeanAbsoluteError | 같은 예상 TOP 10 상품의 \|기준선 − 실제\| 평균. 실제 판매량이 비어 있으면 0으로 계산 |
| items | 예상 TOP 10(예상 순위순) + 실제 TOP 10에 새로 든 상품(뒤쪽, `predictedRank` null) |
| qtyError | 예상 − 실제 |
| status | `MATCHED`(일치), `DROPPED`(이탈), `ENTERED`(진입) |

**오류**: 400 C001 · 403 A004 · 404 F001 해당 주 예측 없음

- 실제 판매량은 결제 완료 수량에서 취소·환불을 뺀 값이며, 확정 시각 이후 변경은 반영하지 않습니다.
- A-04의 "전주 순위"는 전주의 실제 순위입니다. 이 값을 `items[].previousActualRank`로 함께 줄지 정해야 합니다(결정 대기).

### 5-2. 최신 예측 주 조회

| 항목 | 내용 |
|---|---|
| **Method / Path** | `GET /admin/forecasts/latest` |
| **권한** | ADMIN |
| **화면** | A-04 첫 진입 |

**성공 응답** `200`: [5-1](#5-1-주간-예측-조회)과 같은 형식으로 가상 데이터를 제외한 가장 최근 대상 주를 돌려줍니다. 특정 주 조회도 실제 예측만 반환합니다.

**오류**: 404 F001 실제 예측이 하나도 없음. `error.message`에 대상 주·입력 마감·예측 대상 상품 수·최근 84일 순판매 수량·학습 표본 수·마지막 배치 시도 상태를 표시합니다. 배치 기록 기능 도입 전 주차는 시도 기록이 없을 수 있습니다.

- dev 시연 결과: `GET /admin/forecasts/demo`는 `syntheticData=true`인 저장 결과를 조회합니다. 없으면 404 F001입니다. 실제 예측 배치의 대상 주와 겹치지 않는 지난 주차에 저장합니다. 시연 응답의 이전·다음 주는 null입니다.

---

## 6. 할인 후보

이 API들은 실제 상품 가격을 바꾸지 않습니다. 가격은 할인 행사가 등록된 뒤 행사 쪽에서 바뀝니다.

### 6-1. 후보 분석 실행

| 항목 | 내용 |
|---|---|
| **Method / Path** | `POST /admin/deal-analyses` |
| **권한** | ADMIN |
| **화면** | A-06 "후보 분석" |

**요청 본문**

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| targetWeekStart | date | O | 월요일, 오늘 이후 주 |
| dayOfWeek | DayOfWeek | O | |
| discountRate | int | O | 10, 20, 30, 40 중 하나 |
| minStock | int | X | 기본 50, 0 이상 |

**성공 응답** `201` [DealAnalysis](#dealanalysis-응답-형식)

**오류**: 400 C001 · 403 A004 · 503 D001 분석 모델·지표 조회 실패

- 실행할 때마다 새 분석 1건이 저장됩니다(`deal_analyses`). 작업 이력에는 남기지 않습니다.

### 6-2. 분석 조회

| Method / Path | 설명 |
|---|---|
| `GET /admin/deal-analyses/{analysisId}` | 특정 분석 |
| `GET /admin/deal-analyses/latest` | 내가 마지막으로 실행한 분석(A-06 재진입). 없으면 404 D002 |
| `GET /admin/deal-analyses/demo` | dev 시연 결과(`syntheticData=true`). 없으면 404 D002 |

**성공 응답** `200` [DealAnalysis](#dealanalysis-응답-형식) · **오류**: 403 A004 · 404 D002

#### DealAnalysis 응답 형식

```json
{
  "analysisId": 310,
  "targetWeekStart": "2026-10-19",
  "dayOfWeek": "WED",
  "discountRate": 30,
  "minStock": 50,
  "modelVersion": "fc-v0.3",
  "dataCutoffAt": "2026-10-13T06:00:00",
  "createdAt": "2026-10-13T14:20:00",
  "lastSavedAt": "2026-10-13T14:32:00",
  "syntheticData": true,
  "reviewCounts": { "selected": 1, "held": 1, "unreviewed": 2 },
  "candidates": [
    {
      "candidateId": 9001,
      "productId": 1043,
      "productName": "싱싱 애호박 1개",
      "regularPrice": 2200,
      "discountPrice": 1540,
      "expectedQtyRegular": 38,
      "expectedQtyDiscount": 71,
      "expectedIncrease": 33,
      "searchClicks": 1240,
      "purchases": 96,
      "currentStock": 180,
      "candidateReason": "검색 클릭 대비 구매 적음",
      "reviewStatus": "SELECTED",
      "eventRequestStatus": "NONE"
    }
  ],
  "excluded": [
    {
      "candidateId": 9010,
      "productId": 1042,
      "productName": "국산 두부 300g",
      "excludeReason": "TOP_SELLER_ON_DAY",
      "avgSalesOnDay": 64,
      "currentStock": 210
    }
  ]
}
```

| 필드 | 설명 |
|---|---|
| candidates | 후보 상품, 예상 증가가 큰 순 |
| excluded | 제외 상품. `excludeReason`: `TOP_SELLER_ON_DAY`(평소 그 요일 판매 상위), `LOW_STOCK`(최소 재고 미만) |
| reviewStatus | `UNREVIEWED`, `SELECTED`, `HELD` |
| eventRequestStatus | `NONE`, `REQUESTED`, `APPROVED`, `REJECTED` |
| searchClicks, purchases | 최근 4주 같은 요일 합계 |

### 6-3. 검토 결과 저장

| 항목 | 내용 |
|---|---|
| **Method / Path** | `PUT /admin/deal-analyses/{analysisId}/reviews` |
| **권한** | ADMIN |
| **화면** | A-06 "검토 결과 저장" |

**요청 본문**

```json
{
  "reviews": [
    { "candidateId": 9001, "reviewStatus": "SELECTED" },
    { "candidateId": 9003, "reviewStatus": "HELD" }
  ]
}
```

- 보낸 후보만 바꿉니다. 보내지 않은 후보는 그대로입니다. 미검토가 남아 있어도 저장할 수 있습니다.

**성공 응답** `200`: `{ "lastSavedAt": "2026-10-13T14:32:00", "reviewCounts": { "selected": 1, "held": 1, "unreviewed": 2 } }`

**오류**

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | 형식 오류, `reviewStatus`가 `SELECTED`·`HELD`가 아닌 값(`UNREVIEWED`로는 저장할 수 없음) |
| 400 | D003 | 이 분석에 없는 후보 또는 제외 상품 |
| 404 | D002 | 분석 없음 |
| 409 | D004 | 이미 행사 요청한 후보의 검토 상태 변경 |

- 작업 이력에 `DEAL_REVIEW`로 남습니다.

### 6-4. 할인 행사 등록 요청

| 항목 | 내용 |
|---|---|
| **Method / Path** | `POST /admin/deal-analyses/{analysisId}/event-requests` |
| **권한** | ADMIN |
| **화면** | A-06에서 선택한 후보의 행사 등록 |

**요청 본문**

```json
{
  "candidateId": 9001,
  "type": "SPECIAL",
  "startsAt": "2026-11-02T10:00:00",
  "endsAt": "2026-11-02T14:00:00"
}
```

- `type`은 `SPECIAL` 또는 `FLASH_DROP`입니다. 번개 드롭은 1 이상인 `limitedQuantity`가 필요합니다.
- 시작 시각은 현재 이후, 종료 시각은 시작 시각 이후여야 합니다.
- `SELECTED`이고 아직 행사에 등록되지 않은 후보 한 건을 기존 행사 생성 서비스로 등록합니다. 성공하면 행사 ID와 `APPROVED` 상태를 저장합니다.
- dev에서는 `syntheticData=true`인 시연 후보도 실제 dev 행사로 등록할 수 있습니다. main에서는 계속 거부합니다. dev 등록은 상품의 할인 표시와 번개 드롭 알림에 영향을 줍니다.

**성공 응답** `201`

```json
{ "candidateId": 9001, "saleEventId": 501, "eventRequestStatus": "APPROVED" }
```

**오류**: 400 C001·D003 · 404 D002 · 409 D005(미선택·이미 등록), D006(main에서 가상 분석), SALE_001(상품에 기존 행사) 등. 행사 생성에 실패하면 후보 상태는 바뀌지 않습니다.

- 작업 이력에 `EVENT_REQUEST`로 남습니다.

---
### 판매 예측·할인 후보 오류 코드

| 코드 | HTTP | 이름 | 메시지 예 |
|---|---|---|---|
| F001 | 404 | FORECAST_NOT_FOUND | 해당 주의 예측이 없습니다. |
| D001 | 503 | DEAL_ANALYSIS_UNAVAILABLE | 분석에 필요한 데이터를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요. |
| D002 | 404 | DEAL_ANALYSIS_NOT_FOUND | 분석 결과를 찾을 수 없습니다. |
| D003 | 400 | INVALID_DEAL_CANDIDATE | 이 분석의 후보 상품이 아닙니다. |
| D004 | 409 | EVENT_ALREADY_REQUESTED | 이미 행사 등록을 요청한 상품입니다. |
| D005 | 400 | NO_SELECTED_CANDIDATE | 요청할 선택 상품이 없습니다. |
| D006 | 503 | EVENT_REQUEST_FAILED | 행사 등록 요청에 실패했습니다. 잠시 후 다시 시도해 주세요. |
