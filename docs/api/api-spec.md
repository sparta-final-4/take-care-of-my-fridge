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
| 수량 변경 | 400 | 공통 | 수량이 없거나 1 미만 |
| 수량 변경 | 404 | `CART_001` | 항목 없음 또는 타인 항목 |
| 수량 변경 | 404 | `PRODUCT_001` | 상품 없음 |
| 수량 변경 | 409 | `PRODUCT_005` | 판매 중지 상품 |
| 수량 변경 | 409 | `CART_002` | 재고 초과 |
| 수량 변경 | 409 | `CART_003` | 번개 드롭 구매 한도 초과 |
| 항목 삭제 | 400 | 공통 | cartItemIds가 없거나 비어 있거나 숫자가 아님 |

조회 항목(`CartItemResponse`): cartItemId, productId, productName, imageUrl, quantity, listPrice(정가), unitPrice(지금 단가), saleType(`SPECIAL`·`FLASH_DROP`·null), discountRate, flashDropRemaining(남은 수량), flashDropLimit(한정 수량), saleEndsAt(종료 시각, +09:00 포함), soldOut(품절 여부). 할인이 없으면 saleType·discountRate·saleEndsAt은 null이고 unitPrice는 정가입니다. 번개 드롭이 아니면 flashDropRemaining·flashDropLimit은 null입니다.

- **담기**: 장바구니가 없으면 만들고, 이미 담긴 상품이면 수량을 더합니다. 장바구니는 회원당 하나, 한 장바구니에 같은 상품은 하나입니다.
- **재고 확인**: 담을 때는 기존 수량과 요청 수량의 합이 재고를 넘으면 거절합니다. 재고는 응답으로 주지 않습니다.
- **수량 변경**: 담은 뒤 재고가 줄었어도 수량을 내리는 것은 허용하고, 현재 수량보다 올리는 것은 재고까지만 허용합니다.
- **번개 드롭 한도**: 진행 중인 번개 드롭 상품은 1인 1개라 합친 수량이 1을 넘으면 `CART_003`으로 거절합니다.
- **할인 조회**: 조회할 때 진행 중인 할인을 한 번에 읽어 항목마다 단가를 붙입니다. 장바구니에는 할인 가격을 저장하지 않으므로 할인이 끝나면 다음 조회부터 정가가 보입니다.
- **품절 표시**: 재고 0, 판매 중지, 번개 드롭 남은 수량 0이면 그 항목만 `soldOut=true`입니다.
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

- 상태 값: `PENDING`(결제 대기), `PAID`(결제 완료), `CANCELLED`(취소), `CONFIRMED`(구매 확정).
- **재고는 주문을 만들 때 줄임**: 주문하기에서 상품 재고를 빼고, 번개 드롭은 남은 수량도 뺍니다. 하나라도 실패하면 모두 롤백됩니다. 장바구니 항목은 주문하기에서 지우지 않습니다.
- **가격 확인**: 요청의 `unitPrice`·`saleType`이 주문 시점의 가격·할인 구분과 하나라도 다르면 오르든 내리든 `ORDER_010`으로 거절합니다. 오류 문구는 "가격 변경", "할인 시작", "할인 종료" 중 하나와 상품 이름입니다.
- **중복 요청**: 같은 회원이 같은 `Idempotency-Key`로 다시 보내면 새 주문을 만들지 않고 처음 주문을 200으로 돌려줍니다. 상품·수량이 처음 요청과 다르면 `ORDER_006`입니다. 같은 회원의 주문하기는 장바구니 행을 잠가 하나씩 처리합니다.
- **번개 드롭**: 1인 1개이고 이미 산 번개 드롭을 다시 주문하면 `CART_003`입니다. 남은 수량이 0이면 품절로 거절합니다.
- **취소**: 결제를 시도하지 않은 결제 대기 주문만 취소할 수 있습니다. 상품 재고와 번개 드롭 남은 수량을 되돌린 뒤 주문 상품과 주문을 지웁니다. 지운 주문을 다시 취소하면 404(`ORDER_001`)이고 재고는 한 번만 돌아옵니다. 결제를 시도한 주문은 결제가 실패했어도 `ORDER_012`로 거절합니다.
- **주문 만료**: 결제를 시도하지 않은 결제 대기 주문은 생성 5분 뒤 서버가 취소와 같은 방법으로 지웁니다. 주문 확인 화면을 탭 닫기 등으로 떠나 취소 요청이 오지 않은 주문도 이렇게 정리됩니다.
- **결제 정리**: 결제를 시도한 결제 대기 주문은 진행 중 결제가 없고 마지막 결제 뒤 10분이 지나면 서버가 취소 상태로 바꾸고 재고를 되돌립니다. 결제 기록은 남습니다. 그 전에는 같은 주문으로 다시 결제할 수 있습니다.
- **구매 확정**: 결제 완료 주문만 확정하고 확정 시각을 남깁니다. 이미 확정된 주문을 다시 확정하면 같은 200 응답을 줍니다. 요청 상태 환불이 있으면 `ORDER_011`로 거절하고, 실패한 환불은 막지 않습니다.
- **자동 구매 확정**: 결제 완료 시각(paidAt)부터 7일이 지난 PAID 주문을 매시간 확정합니다. 요청 상태 환불이 있으면 건너뛰고 다음 배치에서 다시 확인합니다.
- **확정 수량**: 구매 확정 때 주문 상품마다 주문 수량에서 완료된 환불 수량을 뺀 값을 저장하고, 주문 상세의 `confirmedQuantity`로 보여 줍니다. 확정 뒤에는 환불할 수 없어 바뀌지 않습니다.
- **목록**: 결제 대기·결제 완료·구매 확정 주문만 최신 주문부터 보여 주고 취소된 주문은 뺍니다. 페이지 번호는 1부터입니다.
- **본인 소유 검증**: 타인의 주문도 없는 주문과 같은 404(`ORDER_001`)로 답합니다.
- **주문 금액**: 주문 상품에 주문 당시 정가·단가·상품 이름·이미지를 저장하므로 상품이 바뀌어도 주문 화면의 금액은 바뀌지 않습니다.

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
| 결제 확정 | Body | orderId | 결제할 주문. 필수 |
| 결제 확정 | Body | portonePaymentId | 결제 준비에서 받은 결제 ID. 필수 |
| 결제창 닫기 | Path | portonePaymentId | 닫은 결제창의 결제 ID |
| PortOne 웹훅 | Header | webhook-id, webhook-timestamp, webhook-signature | PortOne 서명 헤더. 없거나 틀리면 401 |
| PortOne 웹훅 | Body | (원문) | 서명 검증을 위해 원문 그대로 받음 |

응답 데이터입니다.

| 기능 | 응답 | 필드 |
|---|---|---|
| 결제 준비 | PaymentPrepareResponse | orderId, portonePaymentId(결제창에 넘길 결제 ID), orderName(예: "대파 외 2건"), amount(결제 금액), expiresAt(결제 대기 만료 시각) |
| 결제 확정 | PaymentConfirmResponse | id, orderId, portonePaymentId, amount, status(`PAID`), paidAt |

오류입니다. 401은 웹훅을 뺀 모든 기능에 공통입니다.

| 기능 | HTTP | 코드 | 조건 |
|---|---|---|---|
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
- **결제 준비**: 결제하기를 누를 때마다 새 결제 ID를 받습니다. 만료 시각은 10분 뒤입니다. 남은 결제 대기 건이 있으면 PG를 조회해, 이미 결제됐으면 그 결제를 승인하고 새 결제는 만들지 않습니다(`PAYMENT_007`). 아니면 이전 건을 실패(`SUPERSEDED`)로 정리한 뒤 새로 만듭니다.
- **결제 확정**: 서버가 PortOne에 결제 건을 직접 조회해 결제 완료·금액·결제 시각을 확인한 뒤에만 승인하고 주문을 결제 완료로 바꿉니다. 결제창이 성공으로 끝나도 이 응답이 200이어야 결제 성공입니다.
- **중복 확정**: 이미 결제 완료된 결제를 다시 확정하면 PG 조회 없이 같은 200 응답을 줍니다. 확정 요청과 웹훅이 동시에 와도 상태는 한 번만 바뀝니다.
- **받지 않은 결제**: 금액 불일치·만료 후 결제·결제할 수 없는 주문은 실패로 기록한 뒤 PG에서 전액 취소합니다. 취소가 실패해도 거절 이유로 응답하고, 웹훅과 재시도 스케줄러가 다시 취소합니다.
- **실패한 결제의 오류**: 웹훅·스케줄러가 먼저 실패 처리한 결제도 실패 사유에 맞는 오류(`PAYMENT_002`·`PAYMENT_006`·`PAYMENT_007`·`PAYMENT_005`)로 답합니다.
- **결제창 닫기**: 결제 대기 건만 실패(`USER_CANCELLED`)로 바꾸고 주문은 결제 대기로 남아 다시 결제할 수 있습니다. 프론트는 지금 부르지 않고 만료 스케줄러가 10분 뒤 정리합니다.
- **웹훅**: `Transaction.Paid`·`Failed`·`Cancelled`·`PartialCancelled`만 처리하고 나머지는 기록만 합니다. 본문 값을 믿지 않고 PG를 다시 조회해 상태를 맞춥니다. 취소·부분 취소 웹훅은 환불 쪽에도 넘깁니다. 같은 `webhook-id`가 다시 오면 처리가 끝난 경우 200만 돌려줍니다.
- **본인 소유 검증**: 타인의 주문·결제는 403(`ORDER_003`)으로 답하고 PG를 조회하지 않습니다.

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
