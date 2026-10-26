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

정책이 결정된 회원 기능만 경로를 제안합니다. 경로는 prefix(/api)를 뺀 값입니다. 인증 쿠키·재발급 세부(Q-02, Q-08)와 필드 오류 구조(Q-10)가 정해지면 각 항목을 아래 템플릿 수준으로 채웁니다.

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
| 비밀번호 변경 | PATCH /members/me/password | 로그인 | currentPassword, newPassword | 200, 다른 기기 세션 폐기 | 400, 401 현재 비밀번호 불일치 |
| 맛 선호 변경 | PUT /members/me/tastes | 로그인 | tastes[] (0개 이상) | 200 | 400 허용하지 않는 값 |
| 회원 목록 | GET /admin/members?keyword=&role=&page=&size= | ADMIN | keyword(이메일 일부, 대소문자 무시), role(USER/ADMIN), page(기본 1), size(기본 20) | PageResponse<MemberSummary> | 400, 403 |
| 관리자 승격 | POST /admin/members/{memberId}/promotion | ADMIN | 없음 | 200, ROLE_CHANGE 성공 이력 | 403 권한 거부, 404 M005 회원 없음, 409 M007 이미 ADMIN·M008 정지 |
| 작업 이력 조회 | GET /admin/audit-logs | ADMIN | from, to, action, result, targetType, targetId, page, size | PageResponse<AuditLogResponse> | 400, 403 |

- 회원 목록은 회원 ID 내림차순으로 정렬합니다. MemberSummary는 memberId, email, nickname, role, status, createdAt, promotedAt, promotedBy를 포함하고 비밀번호는 노출하지 않습니다. promotedAt·promotedBy는 가장 최근 성공한 ROLE_CHANGE 이력에서 가져오며, 이력이 없는 관리자는 null입니다. page·size는 1 이상이어야 합니다.
- 승격의 성공은 역할 변경과 이력을 한 트랜잭션으로 저장합니다. 승격 실패는 FAILED, 로그인한 일반 회원의 승격 시도는 DENIED로 기록합니다. M006은 기존 비밀번호 오류 코드이므로 승격에 사용하지 않습니다.
- 작업 이력 조회는 오늘을 포함한 최근 7일이 기본 기간입니다. from·to는 포함 날짜이며 from이 to보다 늦으면 400입니다. to만 주면 해당 날짜까지 7일을 조회합니다. result=FAILED는 FAILED·DENIED를 함께 검색하며, 최신 시각·ID 내림차순입니다. page=1, size=20이 기본값입니다. 응답에는 작업 당시 actorRole과 민감 값이 제외된 beforeValue·afterValue가 담깁니다.

- 로그인 실패는 없는 이메일과 틀린 비밀번호를 구분하지 않습니다. 정지 계정은 비밀번호가 맞을 때만 403으로 알립니다.
- 로그인 자격 증명 오류는 401 `A001`, 비밀번호가 일치하는 정지 계정은 403 `A002`입니다.
- 로그인용 JWT는 서버 환경 변수 `JWT_SECRET`(UTF-8 32바이트 이상)으로 HS256 서명합니다. 액세스 토큰은 15분, 갱신 토큰은 최대 7일 유효하며 토큰 종류는 서로 바꿔 사용할 수 없습니다. 30분 미사용 제한은 서버 세션에서 별도로 확인합니다.
- `POST /auth/login` 요청은 `email`, `password`만 받습니다. 성공 응답의 `data`는 `memberId`, `email`, `nickname`, `role`, `status`, `tastes`, `createdAt`입니다.
- 성공 시 `access_token`(Path `/api`, 15분)과 `refresh_token`(Path `/api/auth`, 7일) 쿠키를 발급합니다. 둘 다 HttpOnly, SameSite=Lax, 기본 Secure입니다. 로컬 HTTP 개발에서만 `AUTH_COOKIE_SECURE=false`로 설정할 수 있습니다.
- 로그인 POST는 익명 요청과 CSRF 예외를 허용합니다. 다른 POST 경로의 CSRF 보호는 유지합니다.
- 프론트는 `GET /auth/csrf`로 `XSRF-TOKEN` 쿠키를 받은 뒤 보호 대상 POST/PUT/PATCH/DELETE에 같은 값을 `X-XSRF-TOKEN` 헤더로 보냅니다. 로그인 후나 토큰이 사라졌을 때 다시 조회합니다.
- 보호 API는 `access_token` 쿠키의 서명·만료·세션 ID를 확인한 뒤 회원 역할을 설정합니다. 세션이 폐기·만료됐거나 30분간 사용되지 않았다면 JSON 401 `AUTH_001`입니다. 유효한 요청은 세션의 마지막 사용 시각을 갱신합니다.
- 갱신 토큰은 사용 때마다 교체합니다. 같은 토큰으로 동시에 갱신하면 세션 행 잠금으로 한 요청만 성공하며 이전 토큰 재사용은 401 `MEMBER_004`입니다. 교체해도 최초 로그인으로부터 7일 최대 만료는 연장되지 않습니다.
- 갱신 요청은 만료된 액세스 쿠키 없이도 가능하지만, `refresh_token` 쿠키와 `X-XSRF-TOKEN` 헤더가 필요합니다. 성공 시 두 JWT 쿠키를 다시 발급하며 갱신 쿠키 Max-Age는 최초 7일 한도 중 남은 기간입니다. 프론트는 동시 갱신 요청을 한 번으로 모아야 합니다.
- 로그아웃은 유효한 액세스 쿠키가 있으면 해당 세션을, 액세스 쿠키가 만료되었으면 갱신 쿠키가 가리키는 세션을 폐기합니다. 인증 쿠키가 없거나 세션이 이미 폐기·만료돼도 200이며 두 쿠키를 삭제합니다. CSRF 헤더가 없거나 잘못되면 403입니다.
- tastes 값: SALTY, SWEET, SPICY, UMAMI, MILD.
- 가입 요청에 role이 들어와도 무시하고 USER로 만듭니다. /api/admin 및 그 하위 경로는 ADMIN만 접근하며, 익명 요청은 401 A003, USER 요청은 403 AUTH_002입니다.
- 가입 비밀번호는 8~30자이며 ASCII 영문·숫자·특수문자를 각각 하나 이상 포함합니다. 공백·한글은 허용하지 않습니다(2026-10-16 사용자 확정).
- 가입 요청의 nickname은 앞뒤 공백을 제거하며 1~30자입니다. tastes는 빈 목록을 허용하지만 중복 값은 거부합니다.
- 가입 요청의 phone은 `010-XXXX-XXXX` 또는 하이픈 없는 `010XXXXXXXX` 형식이며 필수입니다. 서버는 숫자 11자리로 저장하고 중복 시 409 `M009`를 반환합니다. 기존 회원과 초기 관리자의 phone은 null일 수 있습니다.
- 닉네임 변경 요청도 앞뒤 공백을 제거한 뒤 1~30자로 검사합니다. 본인 닉네임을 그대로 보내면 200이며, 다른 회원과 중복되면 409 `M002`입니다. 성공 본문은 공통 `ApiResponse.ok()` 형식입니다.
- 비밀번호 변경은 PATCH /members/me/password에 currentPassword와 newPassword를 보냅니다. 새 비밀번호는 가입과 같은 8~30자·ASCII 영문/숫자/특수문자 필수 규칙을 적용합니다. 현재 비밀번호가 틀리면 401 M006이며 변경하지 않습니다. 성공하면 현재 기기 세션은 유지하고 다른 기기의 활성 세션을 폐기하며, 공통 ApiResponse.ok() 형식으로 응답합니다(2026-10-17 사용자 결정).
- 맛 선호 변경은 `PUT /members/me/tastes` 요청 본문 `{"tastes":["SALTY","UMAMI"]}`으로 목록 전체를 교체합니다. 빈 목록은 모든 맛 선호를 지웁니다. 중복 값은 허용하고 서버에서 한 번만 저장합니다. 목록 누락·null, null 항목, 허용하지 않는 맛 값은 400입니다. 성공 본문은 공통 `ApiResponse.ok()` 형식입니다.
- 가입 성공 데이터는 `{ memberId: number }`입니다. 이메일 중복은 `M001`, 닉네임 중복은 `M002`로 응답합니다.
- `POST /members`는 익명 요청을 허용합니다. 다른 POST 경로의 CSRF 보호는 유지합니다.

## 냉장고 API

상태: **구현 완료** · 담당: 이현정 · 근거: 실제 백엔드 컨트롤러 및 서비스 구현

경로는 prefix(/api)를 뺀 값입니다. 냉장고 재료 관리는 본인(`memberId`) 소유의 재료만 접근 가능합니다.

| 기능 | Method / Path | 권한 | 요청 | 성공 응답 | 주요 오류 |
|---|---|---|---|---|---|
| 냉장고 재료 목록 조회 | GET /fridge/items | 로그인 | Query: storage, sort, imminentOnly | 200, `List<FridgeItemResponse>` | 400 정렬/보관방식 오류, 401 |
| 냉장고 재료 이력 조회 | GET /fridge/logs | 로그인 | Query: action, from, to, page, size | 200, `PageResponse<FridgeItemLogResponse>` | 400 from>to 또는 page/size 오류, 401 |
| 냉장고 재료 일괄 등록 | POST /fridge/items | 로그인 | Body: `items`: `[{ rawName, quantity, storage?, expirationDate? }]` (1~20개) | 200, `List<FridgeItemResponse>` | 400 수량<=0 또는 누락, 401 |
| 냉장고 재료 정보 수정 | PATCH /fridge/items/{fridgeId} | 로그인 | Path: fridgeId<br>Body: rawName, quantity, storage, expirationDate | 200, `FridgeItemResponse` | 400 검증 오류, 401, 404 재료 없음/타인 재료 |
| 냉장고 재료 빼기(일부/전부) | POST /fridge/items/{fridgeId}/take-out | 로그인 | Path: fridgeId<br>Body: action, quantity? (null이면 전량) | 200, `FridgeItemTakeOutResponse` | 400 수량<=0, 401, 404 재료 없음/타인 재료 |
| 냉장고 재료 비우기(삭제) | DELETE /fridge/items/{fridgeId} | 로그인 | Path: fridgeId<br>Query: reason (CONSUMED, DISCARDED, WRONG_ENTRY) | 200, 공통 성공 응답 | 400 사유 누락/오류, 401, 404 재료 없음/타인 재료 |

- **본인 소유 검증**: 타인의 냉장고 재료이거나 존재하지 않는 재료에 대한 수정·삭제·빼기 요청은 리소스 존재 여부 노출을 방지하기 위해 404 `FRIDGE_ITEM_NOT_FOUND`로 통일합니다.
- **표준 재료 매핑**: 등록 시 원문 재료명(`rawName`)은 `IngredientMatcher`를 통해 표준 사전 재료(`Ingredient`)와 자동 매핑되며, 사전에 없는 이름은 직접 입력 재료(`ING_CUSTOM`)로 매핑됩니다.
- **기본값 자동 설정**: 재료 등록 시 보관방식(`storage`)과 소비기한(`expirationDate`)이 비어 있으면 매핑된 표준 재료의 기본 보관방식 및 기본 소비기한 일수(`오늘 + defaultShelfDays`)로 자동 계산되어 저장됩니다.
- **선택적 재매핑**: 재료 정보 수정(`PATCH /fridge/items/{fridgeId}`) 시 `rawName`이 기존과 달라진 경우에만 표준 재료를 다시 매핑하고, 이름이 같으면 기존 매핑을 유지합니다.
- **수량 차감 및 0.05 미만 보정**: 재료 수량 차감 후 남은 양이 0.05 미만(`ZERO_THRESHOLD`)이면 잔여 수량을 0으로 보정하며, 수량이 0이 된 재료는 냉장고 테이블(`fridge`)에서 자동 삭제됩니다.
- **이력 기록 구분**:
  - `DELETE /fridge/items/{fridgeId}`: 사유(`reason`)가 `CONSUMED`(다 먹었어요) 또는 `DISCARDED`(버렸어요)이면 `fridge_item_logs`에 이력을 저장한 뒤 삭제하고, `WRONG_ENTRY`(잘못 등록)는 이력 없이 즉시 삭제합니다.
  - `POST /fridge/items/{fridgeId}/take-out`: `quantity`가 null이거나 남은 양 이상이면 전체를 빼며, 실제로 뺀 수량을 이력에 기록합니다.
- **동시성 제어**: 단건 수정·삭제·빼기 시 `SELECT ... FOR UPDATE` 비관적 락을 적용하여 수량 동시성 경합을 방지합니다.

## 요리 기록 API

상태: **구현 완료** · 담당: 이현정 · 근거: 실제 백엔드 컨트롤러 및 서비스 구현

경로는 prefix(/api)를 뺀 값입니다. 요리 완료 기록과 본인의 요리 이력 관리 기능을 제공합니다.

| 기능 | Method / Path | 권한 | 요청 | 성공 응답 | 주요 오류 |
|---|---|---|---|---|---|
| 요리 완료 등록 | POST /cooking-logs | 로그인 | Body: recipeId, result, memo?, chatSessionId?, usedIngredients?: `[{ ingredientId, quantity? }]` | 200, `CookingCompleteResponse` | 400 재료 중복, 401, 404 레시피/상담세션 없음 |
| 요리 기록 목록 조회 | GET /cooking-logs | 로그인 | Query: recipeId, from, to, page, size | 200, `PageResponse<CookingLogResponse>` | 400 from>to 또는 page/size 오류, 401 |
| 요리 기록 결과·메모 수정 | PATCH /cooking-logs/{cookingLogId} | 로그인 | Path: cookingLogId<br>Body: result, memo? (최대 50자) | 200, `CookingLogResponse` | 400 result 누락, 401, 404 기록 없음/타인 기록 |
| 레시피 요리 통계 조회 | GET /recipes/{recipeId}/cooking-stats | 공개 | Path: recipeId | 200, `CookingStatsResponse` | 404 레시피 없음 |
| 마이 레시피 목록 및 통계 조회 | GET /me/recipes | 로그인 | Query: reviewStatus, sort, page, size | 200, `MyRecipeListResponse` | 400 정렬/필터 오류, 401 |

- **요리 완료 원자적 처리 (`POST /cooking-logs`)**:
  1. 레시피 존재 여부를 `RecipeService.existsById`로 검증하며, 없으면 404 `RECIPE_NOT_FOUND`입니다.
  2. `chatSessionId`가 전달된 경우 본인 소유의 상담 세션인지 먼저 검증하며, 없거나 타인의 세션이면 404 `CHAT_SESSION_NOT_FOUND`로 즉시 실패합니다.
  3. 요리 기록(`CookingLog`)을 생성·저장합니다.
  4. 냉장고 재료를 자동 차감(`FridgeService.consumeForCooking`)합니다:
     - 데드락(교착) 방지를 위해 `ingredientId` 오름차순으로 정렬하여 비관적 락을 획득합니다.
     - 동일 재료가 여러 개면 소비기한 빠른 순(FIFO)으로 순차 차감합니다.
     - 기본 양념(`basicSeasoning=true`), 직접 입력 재료(`ING_CUSTOM`), 밀키트(`ING_MEALKIT`)는 차감 대상에서 자동 제외됩니다.
     - 냉장고에 보유하지 않은 재료는 에러 없이 건너뛰며, 실제 차감된 내역만 응답의 `deductions` 목록에 반환됩니다.
  5. 연결된 상담 세션이 `ACTIVE` 상태이면 요리 완료에 따라 세션을 종료(`complete`) 처리합니다.
- **요리 기록 수정 (`PATCH /cooking-logs/{cookingLogId}`)**: 본인 기록만 수정 가능(미존재 또는 타인 기록 시 404 `COOKING_LOG_NOT_FOUND`). 냉장고 재료 차감 내역은 되돌릴 수 없으므로 결과(`result`)와 메모(`memo`)만 수정할 수 있습니다.
- **레시피 요리 통계 (`GET /recipes/{recipeId}/cooking-stats`)**: 비로그인 요청을 허용합니다. 레시피가 없으면 404이며, 요리 기록이 하나도 없으면 모든 카운트(총 횟수, 성공, 실패)가 0으로 반환됩니다.
- **마이 레시피 (`GET /me/recipes`)**: 회원이 요리 완료한 레시피들을 그룹화하여 후기 작성 상태(`reviewStatus`: ALL, PENDING, COMPLETED) 필터링과 전체 요약 통계(총 요리 수, 고유 레시피 수, 성공 수, 작성 후기 수)를 함께 반환합니다.

## 후기 API

상태: **구현 완료** · 담당: 이현정 · 근거: 실제 백엔드 컨트롤러 및 서비스 구현

경로는 prefix(/api)를 뺀 값입니다. 레시피별 후기 작성·수정·삭제 및 목록 조회를 제공합니다.

| 기능 | Method / Path | 권한 | 요청 | 성공 응답 | 주요 오류 |
|---|---|---|---|---|---|
| 레시피 후기 목록 조회 | GET /reviews | 공개 | Query: recipeId, mine, sort, page, size | 200, `PageResponse<ReviewResponse>` | 400 sort/page/size 오류, 401 (mine=true 비로그인) |
| 레시피 후기 작성 | POST /reviews | 로그인 | Body: recipeId, rating (1~5), content (최대 100자) | 200, `ReviewResponse` | 400 별점/내용 오류, 401, 404 레시피 없음, 409 이미 후기 작성함 |
| 레시피 후기 수정 | PATCH /reviews/{reviewId} | 로그인 | Path: reviewId<br>Body: rating (1~5), content (최대 100자) | 200, `ReviewResponse` | 400 별점/내용 오류, 401, 404 후기 없음/타인 후기 |
| 레시피 후기 삭제 | DELETE /reviews/{reviewId} | 로그인 | Path: reviewId | 200, 공통 성공 응답 | 401, 404 후기 없음/타인 후기 |

- **목록 조회 비로그인 허용**: `GET /reviews`는 비로그인 사용자도 조회할 수 있습니다. 단, 내 후기만 필터링하는 `mine=true` 파라미터는 비로그인 시 401 `UNAUTHORIZED`가 발생합니다.
- **응답 내 본인 글 여부**: 목록 응답의 각 후기 항목에는 로그인 사용자가 작성한 글인지 나타내는 `isMine` (boolean) 필드가 포함됩니다.
- **1회원 1레시피 1후기 제한**: 동일 회원이 동일 레시피에 2건 이상의 후기를 작성할 수 없습니다. 이미 존재하는 경우 409 `REVIEW_ALREADY_EXISTS`를 반환하며, 동시 요청으로 인한 DB 유니크 제약(`uk_recipe_reviews_member_id_recipe_id`) 위반 시에도 409로 변환 처리합니다.
- **레시피 존재 검증**: 후기 작성 시 레시피 존재 여부를 `RecipeService.existsById`로 확인하며, 미존재 시 404 `RECIPE_NOT_FOUND`를 반환합니다.
- **본인 후기 검증**: 후기 수정 및 삭제는 작성자 본인만 가능하며, 타인의 후기이거나 존재하지 않는 후기인 경우 404 `REVIEW_NOT_FOUND`로 통일하여 존재 여부를 노출하지 않습니다.

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
