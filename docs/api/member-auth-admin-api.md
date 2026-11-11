# 회원·인증·관리자 API 명세

상태: 구현 완료 · 담당: 이현정 · 공통 규칙: [API 계약](api-spec.md#공통-규칙) · 자세한 동작: [회원 설계](../architecture/domains/member.md)

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
