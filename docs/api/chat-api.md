# AI 상담 API 명세

상태: 구현 완료 · 담당: 이현정 · 공통 규칙: [API 계약](api-spec.md#공통-규칙) · 자세한 동작: [AI 상담 설계](../architecture/domains/chat.md)

공통 응답·오류 코드·페이지·날짜 형식은 [공통 규칙](api-spec.md#공통-규칙)을 따릅니다. 이 문서의 경로는 `/api`를 뺀 값이고, 에러 표에는 공통 오류(401 `A003`, 400 `C001`)를 빼고 이 API만의 오류를 적습니다.

상담 상태가 바뀌었다는 신호(시작·끝냄·만료)는 이 문서의 API가 아니라 [알림 API](api-spec.md#알림-api)의 실시간 알림 스트림에 SSE `chat` 이벤트로 옵니다.

## 목차

- [상담 시작](#상담-시작)
- [내 상담 목록](#내-상담-목록)
- [상담 단건](#상담-단건)
- [상담 끝내기](#상담-끝내기)
- [메시지 조회](#메시지-조회)
- [메시지 보내기 (SSE)](#메시지-보내기-sse)
- [상담 응답 DTO](#상담-응답-dto)

## 상담 시작
`POST /chat-sessions` · 로그인

요청: 없음

응답: `ChatSessionResponse` (`cookedRecipe`는 늘 null)

주의 사항
- 진행 중 상담이 있으면 그 상담을, 30분이 지났으면 만료시키고 새 상담을 돌려줍니다. 자세한 동작: [상담 목록](../architecture/domains/chat.md#상담-목록)

## 내 상담 목록
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

## 상담 단건
`GET /chat-sessions/{sessionId}` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| sessionId (path) | long | Y | 상담 ID |

응답: `ChatSessionResponse`

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | CHAT_001 | 없는 상담이거나 남의 상담 |

## 상담 끝내기
`PATCH /chat-sessions/{sessionId}/complete` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| sessionId (path) | long | Y | 상담 ID |

응답: `ChatSessionResponse` (`status`=`COMPLETED`, `cookedRecipe`는 null)

| HTTP | 코드 | 조건 |
|---|---|---|
| 404 | CHAT_001 | 없는 상담이거나 남의 상담 |
| 409 | CHAT_005 | 이미 끝났거나 만료된 상담 |

## 메시지 조회
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

## 메시지 보내기 (SSE)
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

## 상담 응답 DTO

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
