# 상담 냉장고 보기·사기 의도와 냉장고 밀키트 먼저 보여 주기

상태: 검증 대기 · 작성일: 2026-11-08 · 담당: zcookiez

브랜치 `feat/chat-fridge-shop`에서 한 작업이다. 이 브랜치는 `refactor/chat-spicy-from-es`(아직 dev에 안 들어감) 위에 있다. 커밋은 로컬에만 있고 push·PR은 하지 않았다.

## 목표와 완료 기준
- 사용자 문제: 냉장고를 보거나 상품을 사려는 말이 추천으로 처리됐다.
  - "내 냉장고에서 밀키트 어떤 거 있어?" → 밀키트 달라는 말 규칙(밀키트 + "있어")으로 살 수 있는 밀키트 추천. 냉장고에 있는 밀키트는 raw_name으로 오히려 뺐다
  - "당근 사고 싶어" → "사고싶"이 달라는 말이라 당근 레시피 추천 + "당근은 냉장고에 없어서 준비가 필요해요"
  - "냉장고에 뭐 있어", "곧 버려야 할 거 있어?" → 일반 답. 일반 답 AI는 냉장고 데이터를 받지 않는다
- 확인 가능한 완료 조건:
  - 냉장고 보기·사기 말이 새 의도 FRIDGE·SHOP으로 가고, 서버 카드 + 서버 문장으로 답한다 (답 생성 AI 없음)
  - 잘못 걸리기 쉬운 말은 바뀌지 않는다: "냉장고에 있는 재료로 뭐 먹지" → 추천, "냉장고에서 꺼낸 고기 어떻게 해동해?"·"유통기한 지난 거 먹어도 돼?"·"주문한 거 언제 와?" → 일반 답
  - 밀키트 추천에서 냉장고 밀키트를 빼지 않고 요청 조건 다음으로 앞에 두며, 같은 밀키트는 fridge.product_id로 가린다

## 근거와 범위
- 선행: fridge.product_id (docs/database/fridge-product-id.sql, dev 반영됨)
- 변경 영역: chat(의도·카드·저장·다시 불러오기), fridge(FridgeItemResponse.productId), 프론트 chat 카드
- DB 변경 없음 (냉장고 카드는 저장하지 않고, 상품 카드는 기존 chat_recommendations.product_id에 저장)
- 정한 것(사용자):
  1. 의도는 FRIDGE·SHOP을 새로 두고 fridgeScope(ALL / MEALKIT / IMMINENT) 칸을 더한다
  2. 냉장고 카드는 저장하지 않는다. 서버 문장에 목록을 담아 content로 저장하고, 다시 불러오면 그 글만 보인다
  3. 글은 남은 줄 6개·지난 줄 3개 + "외 N개", 카드는 최대 10장. 지난 줄은 맨 아래 "지났어요"
  4. 기본 양념은 전체 보기에서 빼고 이름으로 물으면 보여 준다
  5. 말한 재료가 냉장고에 없으면 "○○는 지금 냉장고에 없어요." + 그 재료 상품 카드
  6. 사기는 재료당 상품 1개, 사전에 없는 이름은 상품 ES 검색으로 보충, 서버가 장바구니에 직접 담지 않음
  7. 서버 단어 안전망(config/chat-words.yml). 냉장고 보기 말 + 사기 말이면 FRIDGE. 묻는 말이 있으면 FRIDGE·SHOP 둘 다 바꾸지 않음, 묻는 말에 "먹어도돼·괜찮" 추가, 사기 말은 "주문"이 아니라 "주문해", 요리 말에 "뭐먹·먹지·먹을까" 추가("먹" 한 글자는 넣지 않음)
  8. 섞인 말("두부 있으면 두부 요리 추천해줘")은 RECOMMEND
  9. 냉장고 밀키트 순서: 요청 조건 → 냉장고 밀키트 → 안 보여 준 것 → …

## 구현 결과
- 의도
  - `ChatIntent`에 FRIDGE·SHOP, `IntentResult.fridgeScope`, `FridgeScope`, `ChatIntentResolution.lookup`(범위·말한 이름)
  - `ChatIntentFacade.applyFridgeShopWords`: AI가 RECOMMEND·GENERAL이면(OFF_TOPIC을 음식 말로 되살린 것 포함) 단어로 FRIDGE·SHOP으로 바꾼다. 밀키트 달라는 말·이름만 말하기 규칙보다 먼저 본다. AI가 FRIDGE·SHOP이어도 요리 말이 있으면 RECOMMEND
  - `lookupOf`: 범위는 AI 값, AI가 ALL이면 서버 단어(밀키트 → MEALKIT, 버려야·지난·임박 → IMMINENT). 이름은 AI includeIngredients, 비었으면 서버가 재료 사전 이름을 찾는다
  - `FridgeShopWordChecker`, `ChatFridgeShopWordProperties`(fridge·imminent·shop·cook), `chat-words.yml`
- 답 (`ChatFridgeShopFacade`, `FridgeReplyWriter`)
  - 냉장고 보기: getItems(EXPIRATION) 한 번 → 범위·이름으로 고름(같은 종류 포함) → 남은 줄 소비기한 순 + 지난 줄 맨 아래 → 글·카드(최대 10). 밀키트 줄은 product_id로 상품 이미지
  - 사기: 재료 사전 → 재료 상품(ShoppingProductPicker, 장볼 재료와 같음) → 없으면 상품 검색 1등(ProductSearchService, ES를 쓰지 않으면 DB)
  - 남은 날 표시는 화면 임박 태그(formatImminentLabel)와 같은 말로 맞췄다 (오늘까지·내일까지·모레까지·3일 남음)
- 이벤트·저장
  - `ChatRecommendationsEventResponse`에 fridgeItems(FridgeItemCard)·products(ProductCard), `RecommendedMealkit`에 inFridge·fridgeDDay
  - `ChatStreamService`: FRIDGE·SHOP이면 서버 문장(fixedReply)으로 답하고, 상품 카드만 저장(`RecommendationToSave.product`, description에 말한 이름). 냉장고 카드는 저장하지 않는다. 하루 횟수는 지금처럼 1회(의도 판단 AI를 씀)
  - `SavedRecommendation.isMealkit` → `isProduct`. 다시 만들 때 상품 종류로 나눈다: MEALKIT은 `rebuildMealkitCards`, INGREDIENT는 `ChatFridgeShopFacade.rebuildProductCards`. `ChatMessageResponse.products` 추가
- 냉장고 밀키트 (`MealkitPicker`)
  - 거르지 않고 정렬에 넣는다 (요청 일치 → 냉장고 밀키트 → 안 보여 준 것 → …). 냉장고 밀키트는 품절이어도 두고, "연결 없는 밀키트 1장" 제한에서 세지 않는다
  - `FridgeMealkits`: product_id별·등록 이름별 남은 일수(기한 지난 줄 제외). `RecipeCandidateFacade.findFridgeMealkits`로 다시 불러올 때도 지금 냉장고 기준(`rebuildMealkitCards(memberId, …)`)
  - 서버 문장: 밀키트만 보여 주는 답에 "냉장고에 이미 있는 밀키트는 먼저 보여 드려요.", 레시피 아래 한 장이면 "요리하기 번거로우면 냉장고에 있는 ○○ 밀키트로 …"
  - `chat-general.txt` 추천 기준 4번: "냉장고에 이미 있는 밀키트는 빼요" → "말씀하신 조건 다음으로 먼저 보여 드리고"
- fridge: `FridgeItemResponse.productId` (냉장고 화면 응답에도 칸이 생김)
- 프롬프트 길이 (줄이기는 따로)
  - `chat-intent.txt`: 50줄 3,653자 → 60줄 4,671자 (+1,018자, 약 28%)
  - `chat-intent-followup.txt`: 한 줄 안에서 "FRIDGE·SHOP·" 추가 (+약 20자)
- 프론트
  - `recommendations.ts`: ChatFridgeItem·ChatProductCard 타입·파싱, 밀키트 inFridge·fridgeDDay, `toChatProductCards`, `IMMINENT_DAYS`
  - `ChatFridgeCards`(새 컴포넌트): 이름·수량, D-3 이내 남은 날·"지났어요" 태그, 밀키트 줄은 상품 이미지와 상품 상세 링크
  - `ChatMealkitCards`: heading props, 냉장고 밀키트는 "냉장고에 있어요"(임박이면 남은 날) 태그를 붙이고 담기 버튼을 숨김. 살 상품 카드도 이 카드로 그린다("살 수 있는 상품", 말한 이름을 인분 자리에)
  - `ChatRecommendationCards`·`ChatRoomSection`·`ChatSessionConversation`: fridgeItems(받는 중인 답만)·products 전달
  - oag 갱신: 임시 `@SpringBootTest`(local 프로필)로 `/v3/api-docs`를 받아 스크립트와 같이 servers를 지운 뒤 `pnpm oag:generate:offline`. 스펙 변경은 ProductCard, ChatMessageResponse.products, RecommendedMealkit.inFridge·fridgeDDay, FridgeItemResponse.productId뿐이다. 임시 테스트는 지웠다
- 문서: `docs/api/api-spec.md` (냉장고 목록 productId, 추천 이벤트 fridgeItems·products, 밀키트 고르기·카드, 냉장고 보기·사기·단어 안전망)

## 검증
| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `./gradlew clean test` | 로컬 | 통과 | 2,219개, 실패 0, 건너뜀 1(기존 수동 테스트) |
| `pnpm lint`, `pnpm typecheck`, `pnpm build`, `pnpm test` | 로컬 | 통과 | 테스트 파일 37개, 테스트 241개 |
| `pnpm format:check` | 로컬 | 실패(기존) | 이번에 건드리지 않은 23개 파일(.prettierrc.json, tsconfig 등)이 걸린다. 작업 트리에서 바뀌지 않은 파일이라 Windows 줄바꿈 문제로 보인다. 이번에 바꾼 파일은 `prettier --check` 통과 |
| 의도 판단 실제 AI 결과 | OpenAI | 미실행 | 키를 확인하는 명령이 권한에서 막혀 돌려 보지 못했다. 서버 규칙·프롬프트 문장은 단위 테스트로만 확인 |
| 화면 375·768·1440px | 브라우저 | 미실행 | 브라우저 도구로 확인하지 않았다. 렌더 결과는 컴포넌트 테스트(정적 마크업)로만 확인 |
| 실제 dev 데이터로 냉장고 보기·사기 | dev MySQL + 로컬 ES | 미실행 | 서버 흐름은 단위 테스트(목)로만 확인 |

## 사용자 확인 후 고친 것 (2026-11-08)
dev(회원 5번, 세션 75)에서 사용자가 확인한 결과와 dev DB를 읽어 본 원인이다.

| 보고 | 원인 | 처리 |
|---|---|---|
| "냉장고에 뭐 있어?"에 카드가 안 보임 | 서버는 `fridgeItems`를 보냈다. 화면은 `done` 뒤 기록을 다시 받아 그리고 받는 중인 카드를 지우는데, 냉장고 카드는 저장하지 않아 바로 사라졌다. 저장하지 않는 정책을 화면 흐름과 맞춰 보지 않았다 | 프론트 `ChatRoomSection`: `done`의 assistantMessageId에 받은 냉장고 카드를 붙여 그 화면에 있는 동안만 보여 준다 (새로고침하면 글만) |
| "곧 버려야 할 거 있어?"와 같은 답이 나옴 | 범위는 맞게 나뉘었다 (전체 12가지 → 외 6개, 임박 9가지 → 외 3개). 그런데 두 답 모두 소비기한 순으로 앞 6개만 써서 같은 이름이 나왔고, 지난 재료 문장도 같았다 | 전체 보기 문장을 "냉장고에 재료 N가지가 있어요. 곧 끝나는 건 … 외 N개예요."로 바꿨다 (`FridgeReplyWriter`) |
| "밀키트 추천해줘"에 냉장고 밀키트가 앞에 안 나옴 | 냉장고의 밀키트는 부추 콩가루 찜 밀키트(상품 27) 한 줄뿐이고 소비기한이 6일 지나, 정해 둔 대로 냉장고에 없는 것으로 봤다 | 바꾸지 않음 (먹을 수 없는 것을 앞에 두지 않고 새로 사게 둔다, 사용자 확인) |

- 같이 본 데이터: "서울우유"·"국산 흙대파"는 재료 사전에 매칭되지 않은 직접 입력 줄이라 등록 이름으로 나오고, "우유"·"대파"와 따로 센다
- 검증: 백엔드 chat 테스트, 프론트 lint·typecheck·build·test 통과. 화면에서 카드가 남는지는 브라우저로 확인하지 않았다 (ChatRoomSection 컴포넌트 테스트 없음)

## 할인 질문 (2026-11-08 추가)
- 문제: "지금 세일하는 재료 있어?"(dev 세션 76, 메시지 617)가 일반 답으로 가서, 할인 데이터를 받지 않는 일반 답 AI가 "세일 중인 재료에 대해서는 설명할 수 없습니다"라고 답했다
- 정한 것(사용자): 사기(SHOP)에 할인 조건을 더한다. 밀키트·재료 단어로 나누고, 3장까지 보여 주고 더 있으면 "재료/밀키트" 메뉴로 안내, 수량이 다 된 번개 드롭은 빼기
- 구현
  - 할인 말 `app.chat.fridge-shop-words.sale`(세일·할인·특가·번개): 서버 단어 안전망에서 SHOP으로 바꾸고, `ItemLookup.saleOnly`·`productType`(밀키트 단어 → MEALKIT, "재료" → INGREDIENT)을 정한다. "재료·밀키트·상품"은 찾을 이름에서 뺀다
  - `ChatFridgeShopFacade.saleProducts`: 메인 "지금 뜨는 할인"(`getPopularSales`)은 종류를 섞어 최대 6개만 가져서 쓰지 않고, 진행 중 할인 전체(`SaleEventReader.findAllActiveSales`) → 상품 조회 → 종류·살 수 있는지(`SellingPrice.soldOut`: 상품 품절·번개 드롭 수량 0)로 거름 → 할인율 높은 순 3장
  - 이름이 있으면 "○○는 지금 할인 중이에요 / 할인 중이 아니에요"
  - 프롬프트: SHOP에 할인 예 1줄, "재료·밀키트·상품은 이름이 아니다" 문장 추가 → `chat-intent.txt` 61줄 4,812자 (처음 3,653자보다 +1,159자, 약 32%)
- 서울우유 묶기(기타 줄을 등록 이름이 같은 사전 줄로)는 하지 않기로 했다 (사용자). 지금처럼 사전 재료는 재료 이름, 기타는 등록 이름으로 센다
- 검증: `./gradlew clean test` 2,228개, 실패 0, 건너뜀 1. 실제 AI·화면은 미확인

## 기본 양념 보기 (2026-11-08 추가)
- 문제: "내 냉장고에 기본 양념 뭐있어?"에 일반 재료가 나왔다. 전체 보기(ALL)는 기본 양념을 빼도록 정했는데, 기본 양념만 볼 범위가 없었다
- 처리: `FridgeScope.SEASONING`(기본 양념 줄만). 양념 말 `app.chat.fridge-shop-words.seasoning`(양념·조미료)이 있으면 서버도 이 범위로 정하고, "기본 양념"은 찾을 재료 이름에서 뺀다. 프롬프트 fridgeScope 설명에 SEASONING과 예 1개를 더했다
- 문장: 임박·밀키트 보기처럼 이름을 나열한다 ("냉장고에 고추장, 간장이 있어요."). 없으면 "냉장고에 등록된 기본 양념이 없어요."
- 알아 둘 것: "양념"이 들어간 이름(예: "양념치킨 밀키트 남았어?")도 기본 양념 보기로 간다
- 검증: `./gradlew clean test` 통과. 실제 AI·화면은 미확인

## 연결과 남은 작업
- 실제 커밋:
  - `feat(be): 상담 냉장고 보기 의도와 냉장고 카드`
  - `feat(be): 상담 상품 사기 의도와 상품 카드`
  - `feat(be): 밀키트 추천에서 냉장고 밀키트 먼저 보여 주기`
  - `feat(fe): 상담 냉장고·상품 카드와 밀키트 냉장고 태그`
  - `docs: API 명세·작업 기록`
- PR: 없음 (push하지 않음)
- 미검증·확인할 것:
  - 실제 AI가 FRIDGE·SHOP·fridgeScope를 뽑는지 (dev 로그 "상담 의도 -", "상담 냉장고 보기 말이 있어 의도를 바꿈")
  - 상품 검색 보충으로 밀키트 상품이 걸리면 답에서는 상품 카드로 보이지만, 다시 불러오면 상품 종류(MEALKIT)라 밀키트 카드로 보인다
  - 판매 중이 아닌 밀키트는 밀키트 추천 후보에 없어서, 냉장고에 있어도 밀키트 추천에는 나오지 않는다 (냉장고 보기에는 나온다)
  - 냉장고 보기 범위가 하나라 "유통기한 지난 밀키트"는 MEALKIT(밀키트 단어가 먼저)로 본다
