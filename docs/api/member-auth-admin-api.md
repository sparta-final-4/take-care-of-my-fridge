# 회원·인증·관리자 API 명세

상태: **구현 반영** · 작성일: 2026-10-15 · 갱신일: 2026-10-30

회원·인증·관리자 API(1~4장, 7장)와 9장의 A·M 오류 코드는 구현된 코드와 같으며 이 영역의 기준 명세입니다. 5장(판매 예측)·6장(할인 후보)·8장·10장은 구현 전 초안이라 실제와 다를 수 있습니다. 해당 영역의 실제 계약은 [API 계약](api-spec.md)과 백엔드 `ErrorCode`를 따릅니다.

개인 담당 범위(회원·인증, 내 정보, 회원 권한, 판매 예측, 할인 후보, 관리자 작업 이력)의 API입니다. 공통 규칙은 [API 계약](api-spec.md), 정책은 [회원 도메인 설계](../architecture/domains/member.md), 데이터는 [데이터 사전](../database/data-dictionary.md)을 따릅니다.

## 목차

| 영역 | API | 화면 |
|---|---|---|
| [1. 가입](#1-가입) | 이메일 중복 확인, 닉네임 중복 확인, 회원가입 | U-02 |
| [2. 인증](#2-인증) | 로그인, 토큰 갱신, 로그아웃 | U-01, U-05 |
| [3. 내 정보](#3-내-정보) | 조회, 닉네임·비밀번호·맛 선호 변경 | U-04 |
| [4. 회원 권한](#4-회원-권한) | 회원 목록, 관리자 승격 | A-10 |
| [5. 판매 예측](#5-판매-예측) | 주간 예측·실제 판매량 조회 | A-04, A-05 |
| [6. 할인 후보](#6-할인-후보) | 분석 실행·조회, 검토 저장, 행사 요청 | A-06 |
| [7. 관리자 작업 이력](#7-관리자-작업-이력) | 이력 조회 | A-09, A-02 |
| [8. 다른 도메인에 요청할 API](#8-다른-도메인에-요청할-api) | 상품, 연동 지표 | A-01~A-03 |
| [9. 오류 코드](#9-오류-코드) | 이 문서에서 쓰는 ErrorCode | |
| [10. 결정 대기](#10-결정-대기) | 아직 정하지 않은 것 | |

---

## 공통 사항

| 항목 | 내용 |
|---|---|
| **경로** | 아래 경로는 prefix를 뺀 값입니다. 실제 호출은 `/api` + 경로입니다. |
| **응답** | 모든 응답은 `ApiResponse<T>`(`success`, `data`, `error`)로 감쌉니다. 아래 "성공 응답"은 `data` 부분만 적습니다. |
| **인증** | HttpOnly 쿠키. 쿠키 이름·속성·CSRF는 Q-08에서 정합니다. |
| **권한 표기** | 공개 = 로그인 불필요 · 로그인 = USER 또는 ADMIN · ADMIN = 관리자만 |
| **401 / 403** | 401은 로그인 안 됨·세션 만료, 403은 로그인했지만 권한 없음. 프론트는 401을 받으면 세션 만료로 처리하므로, 업무 오류에는 401을 쓰지 않습니다(로그인 API 제외). |
| **날짜·시간** | ISO-8601. 날짜는 `2026-10-19`, 일시는 `2026-10-18T22:00:00`. 시간대는 미정. |
| **페이지** | 요청 `page`는 1부터, `size` 기본 20. 응답은 `PageResponse`(`content`, `pageNumber`, `pageSize`, `totalElements`, `totalPages`, `isLast`). |
| **입력 오류** | HTTP 400, 코드 C001. 필드별 오류 구조는 Q-10에서 정합니다. |
| **작업 이력** | 관리자 쓰기 작업과 권한 거부는 `admin_audit_logs`에 남습니다. 비밀번호 등 민감 값은 기록하지 않습니다. |

### 공통 값

| 이름 | 값 |
|---|---|
| Role | `USER`, `ADMIN` |
| MemberStatus | `ACTIVE`, `SUSPENDED` |
| Taste | `SALTY`(짠맛), `SWEET`(단맛), `SPICY`(매운맛), `UMAMI`(감칠맛), `MILD`(슴슴한맛) |
| DayOfWeek | `MON` ~ `SUN` |

---

## 1. 가입

### 1-1. 이메일 중복 확인

| 항목 | 내용 |
|---|---|
| **Method / Path** | `GET /members/email-availability` |
| **권한** | 공개 |
| **화면** | U-02 회원가입 "중복 확인" 버튼 |

**쿼리**

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| email | string | O | 이메일 형식, 최대 255자 |

**성공 응답** `200`

```json
{ "available": false }
```

**오류**: 400 C001 형식 오류

- 결과는 참고용입니다. 확인 뒤 다른 사람이 먼저 가입할 수 있어, 가입 요청 때 서버가 다시 검사합니다.

### 1-2. 닉네임 중복 확인

| 항목 | 내용 |
|---|---|
| **Method / Path** | `GET /members/nickname-availability` |
| **권한** | 공개 (마이페이지 닉네임 변경에서도 사용) |
| **화면** | U-02, U-04 |

**쿼리**

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| nickname | string | O | 앞뒤 공백 제거 후 1~30자. 세부 규칙 Q-09 |

**성공 응답** `200`

```json
{ "available": true }
```

**오류**: 400 C001

- 로그인한 회원이 자기 현재 닉네임을 확인하면 `available: true`를 돌려줍니다.

### 1-3. 회원가입

| 항목 | 내용 |
|---|---|
| **Method / Path** | `POST /members` |
| **권한** | 공개 |
| **화면** | U-02 "가입하기" |

**요청 본문**

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| email | string | O | 이메일 형식, 최대 255자, 중복 불가 |
| password | string | O | 8~30자, ASCII 영문·숫자·특수문자 각 1개 이상, 공백·한글 불가 |
| nickname | string | O | 앞뒤 공백 제거 후 1~30자, 중복 불가 |
| phone | string | O | `010-XXXX-XXXX` 또는 `010XXXXXXXX`. 숫자 11자리로 저장, 중복 불가 |
| tastes | Taste[] | O | 0개 이상, 같은 값 중복 불가. 고르지 않으면 `[]` |

```json
{
  "email": "cook@example.com",
  "password": "abcdefgh12!",
  "nickname": "요리왕",
  "phone": "010-1234-5678",
  "tastes": ["SWEET", "SPICY"]
}
```

**성공 응답** `201`

```json
{ "memberId": 1023 }
```

**오류**

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | 형식·규칙 위반 |
| 409 | M001 | 이메일 중복 |
| 409 | M002 | 닉네임 중복 |
| 409 | M009 | 전화번호 중복 |

- 역할은 항상 USER, 상태는 ACTIVE로 만듭니다. 요청에 `role`이 있어도 무시합니다.
- 비밀번호 확인 칸은 화면에서만 비교하고 서버로 보내지 않습니다.
- 가입만 하고 로그인은 하지 않습니다(쿠키 발급 없음).
- 회원 생성과 맛 선호 저장은 한 트랜잭션입니다.

---

## 2. 인증

### 2-1. 로그인

| 항목 | 내용 |
|---|---|
| **Method / Path** | `POST /auth/login` |
| **권한** | 공개 |
| **화면** | U-01 |

**요청 본문**

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| email | string | O | |
| password | string | O | |
| keepLoggedIn | boolean | X | 기본 false. 의미는 Q-01 남은 질문 |

**성공 응답** `200` + 인증 쿠키 발급. 본문은 [3-1 내 정보](#3-1-내-정보-조회)와 같습니다.

**오류**

| HTTP | 코드 | 조건 |
|---|---|---|
| 401 | A001 | 이메일 없음 또는 비밀번호 틀림. 둘을 구분하지 않음 |
| 403 | A002 | 정지 계정. 비밀번호가 맞을 때만 이 코드 |

- 프론트는 `role`이 ADMIN이면 관리자 상품 화면(A-01)으로, 아니면 원래 보던 화면으로 이동합니다.
- 로그인 API의 401·403은 세션 만료·권한 없음이 아니라 폼 오류로 표시합니다.

### 2-2. 토큰 갱신

| 항목 | 내용 |
|---|---|
| **Method / Path** | `POST /auth/refresh` |
| **권한** | 갱신 쿠키 필요 |
| **화면** | 공통(401을 받으면 1회 자동 호출) |

**요청 본문**: 없음 · **성공 응답** `200`, 본문 없음, 새 쿠키 발급

**오류**

| HTTP | 코드 | 조건 |
|---|---|---|
| 401 | A005 | 갱신 수단 없음·만료·폐기, 또는 30분 미사용 |
| 401 | A006 | 사용 중 계정이 정지됨(U-05 강제 로그아웃 안내) |

- 토큰 회전과 동시 갱신 처리는 Q-08에서 정합니다.

### 2-3. 로그아웃

| 항목 | 내용 |
|---|---|
| **Method / Path** | `POST /auth/logout` |
| **권한** | 로그인 |
| **화면** | U-04 왼쪽 메뉴 "로그아웃" |

**성공 응답** `200`, 본문 없음. 현재 세션을 폐기하고 쿠키를 지웁니다.

- 이미 만료된 세션이어도 200을 돌려줍니다. 화면은 로그아웃 상태로 바뀌면 됩니다.

---

## 3. 내 정보

### 3-1. 내 정보 조회

| 항목 | 내용 |
|---|---|
| **Method / Path** | `GET /members/me` |
| **권한** | 로그인 |
| **화면** | 앱 시작 시 로그인 확인, U-04 |

**성공 응답** `200`

```json
{
  "memberId": 1023,
  "email": "cook@example.com",
  "nickname": "요리왕",
  "role": "USER",
  "status": "ACTIVE",
  "tastes": ["SWEET", "SPICY"],
  "createdAt": "2026-10-13T10:12:00"
}
```

**오류**: 401 A003 비로그인(프론트는 `anonymous` 상태로 처리)

- `role`은 화면 이동에만 씁니다. 마이페이지에는 표시하지 않습니다.

### 3-2. 닉네임 변경

| 항목 | 내용 |
|---|---|
| **Method / Path** | `PATCH /members/me/nickname` |
| **권한** | 로그인 |

**요청 본문**: `{ "nickname": "새닉네임" }` (1~30자)

**성공 응답** `200`, 본문 없음

**오류**: 400 C001 · 409 M002 닉네임 중복

### 3-3. 비밀번호 변경

| 항목 | 내용 |
|---|---|
| **Method / Path** | `PATCH /members/me/password` |
| **권한** | 로그인 |

**요청 본문**

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| currentPassword | string | O | |
| newPassword | string | O | 가입과 같은 규칙, 현재 비밀번호와 달라야 함 |

**성공 응답** `200`, 본문 없음

**오류**

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | 새 비밀번호 규칙 위반 |
| 400 | M003 | 현재 비밀번호 불일치 (401이 아님) |
| 400 | M004 | 새 비밀번호가 현재 비밀번호와 같음 |

- 성공하면 다른 기기의 세션을 모두 폐기합니다. 현재 세션 유지 여부는 Q-08.

### 3-4. 맛 선호 변경

| 항목 | 내용 |
|---|---|
| **Method / Path** | `PUT /members/me/tastes` |
| **권한** | 로그인 |

**요청 본문**: `{ "tastes": ["SALTY", "UMAMI"] }` (0개 이상, 전체 교체)

**성공 응답** `200`, 본문 없음

**오류**: 400 C001 허용하지 않는 값·중복 값

- 후기 화면은 회원 정보를 읽어 표시하므로, 이미 쓴 후기에도 바뀐 맛이 보입니다.

### 3-5. 로그인 기기 관리 (제안 기능)

| Method / Path | 설명 |
|---|---|
| `GET /members/me/sessions` | 로그인된 기기 목록(기기 정보, 마지막 사용일시, 현재 기기 여부) |
| `DELETE /members/me/sessions/{sessionId}` | 해당 기기 로그아웃 |
| `DELETE /members/me/sessions` | 모든 기기 로그아웃 |

와이어프레임에서 "제안"으로 표시한 기능입니다. 팀이 채택하면 세부를 채웁니다.

---

## 4. 회원 권한

### 4-1. 회원 목록

| 항목 | 내용 |
|---|---|
| **Method / Path** | `GET /admin/members` |
| **권한** | ADMIN |
| **화면** | A-10 |

**쿼리**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| keyword | string | X | 이메일 부분 일치 |
| role | Role | X | 없으면 전체 |
| page, size | int | X | 기본 1, 20 |

**성공 응답** `200` `PageResponse<MemberSummary>`

```json
{
  "content": [
    {
      "memberId": 1023,
      "email": "cook@example.com",
      "nickname": "요리왕",
      "role": "USER",
      "status": "ACTIVE",
      "createdAt": "2026-10-13T10:12:00",
      "promotedAt": null,
      "promotedBy": null
    }
  ],
  "pageNumber": 1, "pageSize": 20, "totalElements": 1284, "totalPages": 65, "isLast": false
}
```

**오류**: 401 A003 · 403 A004

- `promotedAt`, `promotedBy`(승격한 관리자 이메일)는 작업 이력에서 가져옵니다.

### 4-2. 관리자 승격

| 항목 | 내용 |
|---|---|
| **Method / Path** | `POST /admin/members/{memberId}/promotion` |
| **권한** | ADMIN |
| **화면** | A-10 "관리자로 승격" → 확인 창 "승격" |

**요청 본문**: 없음 · **성공 응답** `200`, 본문 없음

**오류**

| HTTP | 코드 | 조건 |
|---|---|---|
| 403 | A004 | 요청자가 ADMIN 아님(작업 이력에 DENIED 기록) |
| 404 | M005 | 회원 없음 |
| 409 | M006 | 정지된 회원 |
| 409 | M007 | 이미 ADMIN |

- 역할 변경과 작업 이력(`ROLE_CHANGE`, `role: USER → ADMIN`)을 한 트랜잭션으로 저장합니다.
- 서버는 요청마다 DB의 역할로 권한을 검사하므로 승격은 바로 적용됩니다. 관리자 화면 전환은 대상 회원의 다음 로그인 때입니다.
- 권한 해제 API는 없습니다.

---

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
  "syntheticData": true,
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
| previousWeekStart / nextWeekStart | 예측이 있는 이전·다음 주. 없으면 null(화면의 ‹ › 버튼 비활성) |
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

**성공 응답** `200`: [5-1](#5-1-주간-예측-조회)과 같은 형식으로 가장 최근 대상 주를 돌려줍니다.

**오류**: 404 F001 예측이 하나도 없음

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
| **화면** | A-06 "선택 상품 이벤트 등록 요청" |

**요청 본문**: 없음. `SELECTED`이고 아직 요청하지 않은 후보 전체를 요청합니다.

**성공 응답** `200`

```json
{
  "requestedCount": 1,
  "requestedAt": "2026-10-13T14:35:00",
  "candidates": [ { "candidateId": 9001, "eventRequestStatus": "REQUESTED" } ]
}
```

**오류**

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | D005 | 요청할 선택 후보가 없음 |
| 404 | D002 | 분석 없음 |
| 503 | D006 | 할인 행사 쪽 요청 실패. 상태는 바뀌지 않음 |

- 요청 전에 현재 검토 상태를 함께 저장합니다.
- 행사 쪽 결과(등록·반려)는 할인 행사 담당이 알려 줘야 `APPROVED`·`REJECTED`로 바뀝니다. 전달 방식(API 호출·이벤트)은 Q-05에서 정합니다.
- 작업 이력에 `EVENT_REQUEST`로 남습니다.

---

## 7. 관리자 작업 이력

### 7-1. 이력 조회

| 항목 | 내용 |
|---|---|
| **Method / Path** | `GET /admin/audit-logs` |
| **권한** | ADMIN |
| **화면** | A-09 관리자 작업 이력, A-02 상품 상세의 변경 이력 |

**쿼리**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| from, to | date | X | 기간(포함). 기본 최근 7일 |
| action | string | X | `PRODUCT_CREATE`, `PRODUCT_DELETE`, `PRICE_UPDATE`, `DEAL_REVIEW`, `EVENT_REQUEST`, `ROLE_CHANGE` |
| result | string | X | `SUCCESS`, `FAILED`, `DENIED`. "실패만" 필터는 FAILED·DENIED 둘 다 |
| targetType, targetId | string, long | X | A-02에서 상품 하나의 이력만 볼 때 |
| page, size | int | X | 기본 1, 20. 최신순 |

**성공 응답** `200` `PageResponse<AuditLog>`

```json
{
  "content": [
    {
      "auditLogId": 5501,
      "createdAt": "2026-10-13T11:05:00",
      "actorEmail": "cook@example.com",
      "actorRole": "USER",
      "action": "PRODUCT_DELETE",
      "targetType": "Product",
      "targetId": 1042,
      "result": "DENIED",
      "beforeValue": null,
      "afterValue": null
    }
  ],
  "pageNumber": 1, "pageSize": 20, "totalElements": 5, "totalPages": 1, "isLast": true
}
```

**오류**: 400 C001 · 403 A004

- 기록을 수정·삭제하는 API는 없습니다.

### 7-2. 기록 시점

다른 담당의 API(상품 등록·삭제·정상가 수정)가 성공·실패·거부될 때도 이력이 남아야 합니다. 상품 도메인이 기록할지, 회원 도메인이 기록 API를 제공할지 정해야 합니다(결정 대기).

---

## 8. 다른 도메인에 요청할 API

관리자 상품 화면(A-01~A-03)은 화면만 담당하고 API는 상품 도메인 소유입니다. 화면에 필요한 기능을 상품 담당에게 요청합니다.

| 화면 | 필요한 기능 | 꼭 필요한 데이터·조건 |
|---|---|---|
| A-01 | 관리자 상품 목록 | 검색(상품명), 상태 필터(판매 중·판매 중지), 상태별 개수, 페이지 |
| A-01 | 상품 논리 삭제 | ADMIN, 기존 주문 명세 유지 |
| A-02 | 상품 상세 | 보관 방법, 유통기한, 정상가, 등록자·등록일, 재고·지난주 판매·현재 특가(기준 시각 포함) |
| A-02 | 정상가 수정 | 1원 이상, 기존 주문 가격 불변 |
| A-03 | 상품 등록 | 사진 업로드, 상품명, 설명, 정상가(1원 이상), 유통기한(오늘 이후), 보관 방법(상온·냉장·냉동) |

| 제공 담당 | 이 문서의 API가 쓰는 데이터 |
|---|---|
| 주문·결제 | 상품별 확정 판매 수량(취소·환불 제외)과 기준 시각 → 5장 |
| 재고 | 상품별 현재 재고와 기준 시각 → 6장 |
| 검색·추천 | 상품별·요일별 검색 클릭·구매 수 → 6장 |
| 할인 행사 | 행사 요청 접수와 등록·반려 결과 → 6-4 |
| 후기 | 작성자 닉네임·맛 선호를 [3-1](#3-1-내-정보-조회)과 같은 회원 정보로 조회 |

---

## 9. 오류 코드

공통 코드(C001, C002, S001)는 [백엔드 규칙](../conventions/backend.md)을 따릅니다. 아래는 이 문서에서 새로 제안하는 코드입니다.

| 코드 | HTTP | 이름 | 메시지 예 |
|---|---|---|---|
| A001 | 401 | LOGIN_FAILED | 이메일 또는 비밀번호가 일치하지 않습니다. |
| A002 | 403 | ACCOUNT_SUSPENDED | 이용이 제한된 계정입니다. 고객센터로 문의해 주세요. |
| A003 | 401 | AUTH_REQUIRED | 로그인이 필요합니다. |
| A004 | 403 | ACCESS_DENIED | 관리자 계정만 볼 수 있는 페이지입니다. |
| A005 | 401 | SESSION_EXPIRED | 로그인이 만료되었어요. 다시 로그인해 주세요. |
| A006 | 401 | SESSION_REVOKED_BY_SUSPENSION | 계정 이용이 제한되어 로그인이 해제되었습니다. |
| M001 | 409 | DUPLICATE_EMAIL | 이미 가입된 이메일입니다. |
| M002 | 409 | DUPLICATE_NICKNAME | 이미 사용 중인 닉네임입니다. |
| M003 | 400 | CURRENT_PASSWORD_MISMATCH | 현재 비밀번호가 일치하지 않습니다. |
| M004 | 400 | SAME_AS_CURRENT_PASSWORD | 현재 비밀번호와 다른 비밀번호를 입력해 주세요. |
| M005 | 404 | MEMBER_NOT_FOUND | 회원을 찾을 수 없습니다. |
| M006 | 409 | SUSPENDED_MEMBER_PROMOTION | 정지된 회원은 관리자로 승격할 수 없습니다. |
| M007 | 409 | ALREADY_ADMIN | 이미 관리자인 회원입니다. |
| M009 | 409 | DUPLICATE_PHONE | 이미 사용 중인 전화번호입니다. |

아래 F·D 코드는 구현 전 초안입니다. 실제 값은 백엔드 `ErrorCode`를 따릅니다.

| 코드 | HTTP | 이름 | 메시지 예 |
|---|---|---|---|
| F001 | 404 | FORECAST_NOT_FOUND | 해당 주의 예측이 없습니다. |
| D001 | 503 | DEAL_ANALYSIS_UNAVAILABLE | 분석에 필요한 데이터를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요. |
| D002 | 404 | DEAL_ANALYSIS_NOT_FOUND | 분석 결과를 찾을 수 없습니다. |
| D003 | 400 | INVALID_DEAL_CANDIDATE | 이 분석의 후보 상품이 아닙니다. |
| D004 | 409 | EVENT_ALREADY_REQUESTED | 이미 행사 등록을 요청한 상품입니다. |
| D005 | 400 | NO_SELECTED_CANDIDATE | 요청할 선택 상품이 없습니다. |
| D006 | 503 | EVENT_REQUEST_FAILED | 행사 등록 요청에 실패했습니다. 잠시 후 다시 시도해 주세요. |

---

## 10. 결정 대기

| 항목 | 영향 API | 관련 |
|---|---|---|
| 인증 쿠키 이름·속성, 토큰 회전, CSRF·CORS | 2장 전체 | Q-08 |
| 401 재발급 흐름, 비밀번호 변경 후 현재 세션 유지 | 2-2, 3-3 | Q-02, Q-08 |
| "로그인 상태 유지"의 의미 | 2-1 | Q-01 |
| 비밀번호·닉네임 세부 규칙, 이메일 인증 | 1장 | Q-09 |
| 필드별 오류 응답 구조 | 전체 400 | Q-10 |
| 페이지 요청 번호(1부터) 확정 | 4-1, 7-1 | API 계약 |
| 시간대 | 전체 | Q-07 |
| A-04 "전주 순위"를 서버가 줄지 | 5-1 | |
| 할인 행사 요청·결과 전달 방식 | 6-4 | Q-05 |
| 상품 작업 이력을 누가 기록할지 | 7-2 | |
| 로그인 기기 관리 채택 여부 | 3-5 | 제안 기능 |
