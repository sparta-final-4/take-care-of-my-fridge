# api-spec 내 담당 절 정리

브랜치: `docs/api-spec-cleanup` · 범위: 냉장고·요리 기록·후기·AI 상담 API

## 한 일
- 동작·서버 규칙은 도메인 문서로 옮겼습니다: [chat.md](../architecture/domains/chat.md), [fridge.md](../architecture/domains/fridge.md), [cooking.md](../architecture/domains/cooking.md), [review.md](../architecture/domains/review.md).
- api-spec.md에는 API마다 Method/Path·권한, 요청 표, 응답 DTO 표, 에러 표, 주의 사항(3줄 이내)만 남겼습니다.
- 날짜 표시("2026-11-0x 추가", "(기존)")는 지우고, 큰 변경 3개만 도메인 문서 변경 이력에 남겼습니다.
- 명세에 없던 API 4개를 넣었습니다: 상담 시작, 상담 끝내기, 인기 레시피, 후기 요약 실행.

## 전후 대조표
| 지금까지 api-spec.md에 있던 항목 | 옮긴 곳 |
|---|---|
| 냉장고 6개 API 요약 표 | api-spec 냉장고 API의 API별 표 |
| 냉장고 `productId` 설명 | api-spec `FridgeItemResponse.productId`, fridge.md 상품 ID |
| 본인 소유 검증 404 | api-spec 에러 표, fridge.md 본인 소유 |
| 표준 재료 매핑, 기본값 자동 설정 | fridge.md 표준 재료 매핑·기본값 (api-spec 주의 사항 한 줄) |
| 선택적 재매핑 | fridge.md 수정 (api-spec 주의 사항 한 줄) |
| 0.05 미만 보정·자동 삭제 | fridge.md 0 보정 (api-spec 주의 사항 한 줄) |
| 비우기 사유별 이력, 빼기 이력 | fridge.md 비우기 이력·빼기 |
| 비관적 락 | fridge.md 동시성 |
| 요리 기록 5개 API 요약 표 | api-spec 요리 기록 API의 API별 표 |
| 요리 완료 5단계(레시피 확인·상담 잇기·저장·차감·상담 종료) | cooking.md 요리 완료 (api-spec 주의 사항 두 줄) |
| 요리 기록 수정 제한 | cooking.md 기록 수정, api-spec 주의 사항 |
| 레시피 요리 통계 | cooking.md, api-spec 응답 표 |
| 마이 레시피 필터·요약 | cooking.md 마이 레시피, api-spec 요청·응답 표 |
| AI 상담 4개 API 요약 표 | api-spec AI 상담 API의 API별 표 |
| `ChatSessionResponse`·`cookedRecipe` | api-spec 상담 응답 DTO, chat.md 상담 목록 |
| `ChatSessionListResponse`·`ChatSessionSummaryResponse` | api-spec 내 상담 목록 응답 표 |
| 목록 상태(ACTIVE·ENDED)·정렬·메시지 0개 제외 | chat.md 상담 목록 |
| 걸러진 메시지와 목록 미리보기 | chat.md 상담 목록 |
| 목록 쿼리 수 | chat.md 상담 목록 |
| 메시지 보내기 순서·답 받기 잠금 | chat.md 메시지 처리, api-spec 스트림 전 에러 표 |
| 단어 차단(정규화·제외 목록·순서·안내 답·저장·로그) | chat.md 단어 차단 |
| 건강 안내 | chat.md 건강 안내 |
| 링크 지우기 | chat.md 링크 지우기 |
| 알레르기 재료 빼기(말 모양·넓히기·넓히지 않기·상담 전체·밀키트·안내 문구) | chat.md 알레르기 |
| "못 먹" 부정어 | chat.md 서버 단어 안전망의 맛 단어 |
| 종료·만료 상담도 메시지 조회 가능 | api-spec 메시지 조회 요청 표, chat.md 메시지 처리 |
| 추천 요청 필드(mode·selectedIngredientIds·useSelectedOnly) | api-spec 메시지 보내기 요청 표, chat.md 의도 종류 |
| 추천 카드 이벤트와 `data` 모양 | api-spec SSE 이벤트 표 |
| `RecommendedRecipe` 필드와 카드 이유 우선순위 | api-spec 레시피 카드 표, chat.md 카드 이유 |
| 장볼 재료 상품(`ShoppingProduct`)·고르는 순서·다시 계산·담기 API | api-spec `ShoppingProduct` 표, chat.md 장볼 재료 상품 |
| 밀키트 카드(말한 경우·번거로움·부정·고르기·서버 문장·`RecommendedMealkit`·저장) | chat.md 밀키트 카드·저장, api-spec `RecommendedMealkit` 표 |
| 냉장고 보기(범위·순서·문장 규칙·`FridgeItemCard`·저장) | chat.md 냉장고 보기, api-spec `FridgeItemCard` 표·의도별 순서 |
| 사기(상품 고르기·문장·`ProductCard`·저장) | chat.md 사기·저장, api-spec `ProductCard` 표 |
| 할인 질문 | chat.md 할인 질문 |
| 냉장고 보기·사기 서버 단어 안전망 | chat.md 서버 단어 안전망 |
| 카드 설명 이벤트(`recommendation_text`) | api-spec SSE 이벤트 표, chat.md 카드 설명·저장 |
| 하루 횟수와 `recommendations` | chat.md 하루 횟수 |
| 이어지는 말: 의도 종류 | chat.md 의도 종류 |
| 서버 안내 의도(OFF_TOPIC·BLOCKED·SELF_HARM, 안내 답, 저장) | chat.md 서버 안내 의도, api-spec 의도별 순서 |
| OFF_TOPIC 되살리기·아닌 예 | chat.md OFF_TOPIC 되살리기·서버 안내 의도 |
| 밀키트 달라는 말·이름만 말하기·분류 묶음 말 | chat.md 서버 단어 안전망 |
| GENERAL 이전 대화 | chat.md 이어지는 말 GENERAL |
| REFINE 합치기·removed·피할 맛·매운 레시피·직전 카드 | chat.md REFINE |
| 맛 부딪힘 | chat.md 맛 부딪힘 |
| RECIPE_DETAIL 찾기·카드·질문 종류·지워진 레시피 | chat.md RECIPE_DETAIL, api-spec 의도별 순서 |
| 직전 추천 Redis | chat.md 직전 추천 |
| AI 호출 수·하루 횟수 | chat.md 메시지 처리·하루 횟수 |
| 후기 4개 API 요약 표 | api-spec 후기 API의 API별 표 |
| 목록 비로그인·`mine=true` 401 | api-spec 후기 목록 에러 표, review.md 목록 |
| 본인 글 여부 | api-spec `ReviewResponse.mine` |
| 1회원 1후기·유니크 제약 409 | review.md 1회원 1레시피 1후기, api-spec 에러 표 |
| 레시피 존재 검증, 본인 후기 검증 | review.md, api-spec 에러 표 |
| 날짜 표시("2026-11-0x 추가·변경", "(기존)") | 지움. 큰 변경 3개는 chat.md·cooking.md·fridge.md 변경 이력 |

## 코드와 달라서 고친 것
| 항목 | 이전 명세 | 코드·openapi |
|---|---|---|
| 후기 응답 본인 글 필드 | `isMine` | `mine` |
| 마이 레시피 `reviewStatus` | ALL·PENDING·COMPLETED | ALL·WRITTEN·NOT_WRITTEN |
| 마이 레시피 `sort` | 값 없음 | RECENT·MOST |
| 마이 레시피 요약 | 총 요리 수·고유 레시피 수·성공 수·작성 후기 수 | recipeCount·totalCookCount·writtenCount·notWrittenCount·mostCookedRecipe·consumedIngredientCount (성공 수 없음) |
| 후기 작성 에러 | 403 없음 | 403 `REVIEW_003` 요리 완료 기록 없음 |
| 에러 코드 표기 | enum 이름(`FRIDGE_ITEM_NOT_FOUND`, `RECIPE_NOT_FOUND`, `COOKING_LOG_NOT_FOUND`, `REVIEW_ALREADY_EXISTS`, `REVIEW_NOT_FOUND`, `UNAUTHORIZED`) | 응답 코드(`FRIDGE_001`, `RECIPE_001`, `COOKING_001`, `REVIEW_002`, `REVIEW_001`, `A003`) |
| 재료 빼기 에러 | 없음 | 400 `FRIDGE_005` 밀키트를 정수가 아닌 개수로 뺌 |
| 재료 수정 | 네 칸을 보냄 | 보낸 칸만 바꾸고 null은 그대로 |
| 요리 완료 `usedIngredients` | 개수 제한 없음 | 30개 이하 |
| 페이지 크기 | 적지 않음 | 냉장고 이력·요리 기록 기본 20·최대 100, 마이 레시피 기본 10·최대 50, 후기 기본 10·최대 50, 인기 레시피 기본 6·최대 20 |
| 상담 목록 `recommendedRecipes` | 없음 | 최근 추천 레시피 최대 3개 |
| 레시피 카드 `description` | 필드 목록에 없음 | 있음(스트림에서는 null) |
| SSE `error` 코드 | 적지 않음 | `CHAT_010`·`CHAT_011`·`CHAT_012`·`COMMON_002` |
| SSE `done` data | 적지 않음 | `userMessageId`, `assistantMessageId` |
| 상담 끝내기 409 | API가 없었음 | 끝난 상담과 만료된 상담 모두 `CHAT_005` |
| `ProductCard.product` | null 가능처럼 읽힘 | 파는 상품이 없는 이름은 카드를 만들지 않음 |

## openapi 대조
- 기준: 로컬 `bootRun`의 `/v3/api-docs` (2026-11-09). 명세 표의 필드 이름은 모두 스키마 이름과 같습니다. `FridgeItemCard`는 SSE에만 나와 스키마에 없어서 코드 record와 비교했습니다.
- 코드 쪽에서 본 것 (이번에 고치지 않음):
  - `@ModelAttribute`로 받는 조회 조건(`GET /fridge/items`, `/fridge/logs`, `/cooking-logs`, `/me/recipes`, `/recipes/popular`, `/reviews`, `/chat-sessions`)이 openapi에 `request` 파라미터 하나로 나옵니다. `@ParameterObject`를 붙이면 칸별로 나옵니다.
  - `CookingCompleteRequest`의 `@AssertTrue` 검사 메서드가 openapi에 `ingredientIdsUnique` 속성으로 나옵니다.

## 분량
글자 수는 코드 포인트 기준입니다.

| 절 | 전 | 후 |
|---|---|---|
| 냉장고 API | 26줄 / 2,421자 | 154줄 / 4,109자 |
| 요리 기록 API | 31줄 / 2,642자 | 155줄 / 4,078자 |
| AI 상담 API | 85줄 / 17,321자 | 226줄 / 8,195자 |
| 후기 API | 19줄 / 1,400자 | 121줄 / 2,753자 |
| 합계 | 161줄 / 23,784자 | 656줄 / 19,135자 |

- 도메인 문서(새로 만듦): chat.md 289줄 / 16,972자, cooking.md 37줄 / 2,161자, fridge.md 26줄 / 1,633자, review.md 19줄 / 1,327자.
