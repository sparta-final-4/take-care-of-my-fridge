# 상담 답 프롬프트와 실제 동작이 다른 곳 맞추기

상태: 완료 · 작성일: 2026-11-11 · 담당: zcookiez

브랜치 `fix/chat-prompt-mismatch`(dev e0590e64 위)에서 한 작업이다. 커밋은 로컬에만 있고 push·PR은 하지 않았다.
상담 지도 조사에서 나온 항목 중 P3·P7·P8·P12를 고쳤고, D12는 저장 칸이 필요해 이번에는 하지 않았다.

## 목표와 완료 기준
- 사용자 문제: 답 프롬프트가 서버가 실제로 하는 일과 다르게 말하는 곳이 있었다.
  - P3: 추천 프롬프트가 "임박·부족 재료는 서버가 [이미 안내한 말]로 했다"고 했지만, 서버는 냉장고에 없는 선택 재료만 안내한다(맞는 레시피 없음·요리 못 찾음 안내는 따로 있다).
  - P8: 공통 프롬프트가 건강 문제에 전문가 상담을 권하라고 해서, 서버가 붙이는 건강 안내와 두 번 나왔다.
  - P12: 일반 답의 추천 기준 설명에 "맞는 레시피가 없으면 냉장고 재료로 3장 + 안내"와 온도 말 거르기가 빠져 있었다.
  - P7: 공통 "500자 안팎" 규칙이 레시피 자세히 답(최대 600토큰, 단계 10줄)과 부딪혔다.
- 완료 조건: 프롬프트 문구 테스트 갱신, `./gradlew clean test` 통과. 의도 프롬프트(chat-intent*.txt)는 건드리지 않는다.

## 근거와 범위
- 서버 안내 문장: `RecommendationPromptBuilder.notices`(맞는 레시피 없음·요리 못 찾음·냉장고에 없는 선택 재료), `ChatRecommendationFacade.NO_MEALKIT_NOTICE`. 임박 재료 안내는 없다.
- 건강 안내: `ChatStreamService.HEALTH_NOTICE`. 사용자 말에 건강 단어가 있을 때만 붙는다. AI가 먼저 건강 이야기를 꺼낼 때 상담 권유가 안 나오는 것은 받아들이기로 했다.
- 고르기: `RecommendationPicker`·`PickRule`(빈자리는 온도와 반대인 레시피로 채우지 않음), `ChatRecommendationFacade.recommendRecipes`(맞는 후보가 없으면 조건 없이 고르고 안내), `TemperatureWordChecker.arrange`(따뜻하면 찬 요리 빼기, 차가우면 뜨거운 국물 뒤로).
- 변경 영역: prompts/chat-system.txt·chat-general.txt·chat-recommend.txt, 프롬프트 문구 테스트 3개. 코드 변경 없음.

## 구현 결과
| 항목 | 파일 | 바꾼 것 |
|---|---|---|
| P3 | chat-recommend.txt | 임박 재료 줄: "필요하면 서버가 [이미 안내한 말]로 이미 했다" → "임박 재료는 카드에 이미 있다". 냉장고에 없는 재료 줄: "장볼 재료는 카드에 있고, 냉장고에 없는 선택 재료는 서버가 [이미 안내한 말]로 이미 안내했다" |
| P8 | chat-system.txt | "전문가 상담을 권한다" 삭제, "먹어도 되는지·몸에 맞는지 같은 의료 판단은 하지 않는다"만 남김 |
| P7 | chat-system.txt | "500자 안팎" 뒤에 "레시피 단계를 쓰는 답만 단계 수만큼 길어져도 된다" |
| P12 | chat-general.txt | 1번에 "맛이나 온도와 반대가 아닌", "맞는 레시피가 하나도 없으면 냉장고 재료로 만들 수 있는 요리 세 개를 고르고, 맞는 레시피가 없었다고 먼저 알려 드려요" 추가. 온도 말 항목(2번)을 새로 넣고 뒤 번호를 하나씩 밀었다 |

글자 수 (공백·줄바꿈 포함)

| 파일 | 전 | 후 | 차이 |
|---|---|---|---|
| chat-system.txt | 359 | 410 | +51 |
| chat-general.txt | 734 | 879 | +145 |
| chat-recommend.txt | 2,884 | 2,901 | +17 |

chat-system.txt는 일반·추천·레시피 자세히 답 모두에 붙는다.

API·DB·프론트 변경 없음.

## 검증
| 명령·시나리오 | 실행 환경·조건 | 결과 | 근거 |
|---|---|---|---|
| 프롬프트 문구 테스트 (`Chat*PromptTest`, `OpenAiChatRecommendationReplyTest`) | 로컬 | 통과 | 옛 문장이 없고 새 문장이 있는지, 일반 답 프롬프트 줄 수 7→8 |
| `./gradlew clean test` | 로컬, Docker(Testcontainers) | 실패 1 | 2,350개 중 `cooking/PopularRecipeServiceCacheIntegrationTest` 1개만 실패 (전체 실행 3번 모두). 단독 실행은 통과, 같은 시점 dev(e0590e64) 전체 실행은 통과. 이 작업은 프롬프트 글과 프롬프트 테스트만 바꿨다 |
| 실제 AI 답 비교 | OpenAI 호출 | 미실행 | 답 프롬프트는 의도 평가 대상이 아니라 평가 세트가 없다 |

## 나중에 할 것
- `cooking/PopularRecipeServiceCacheIntegrationTest`가 전체 실행에서 순위 집계를 두 번 한다 (캐시를 못 씀). 캐시 오류는 `LoggingCacheErrorHandler`가 삼켜 원인이 로그에 남지 않는다. 지난주 다른 브랜치에서도 한 번 실패했다.
- D12: 다시 불러온 카드 이유(`ChatRecommendationFacade.rebuildCards`)가 처음과 다르다.
  - 처음 이유는 요청 조건(요리 이름 keywordText·hasRequest·원하는 맛·분류·선택 재료), 그때 키워드 검색 결과(KeywordMatch), 다른 제안 표시, 남긴 피할 맛으로 만든다.
  - 저장되는 것은 레시피 ID·순서·AI 설명뿐이고 조건은 Redis에 30분, 마지막 추천만 있다. 그래서 다시 불러오면 임박·좋아하는 맛·냉장고 재료 수 이유만 나온다.
  - 고치려면 `chat_recommendations`에 `reason`(TEXT) 칸을 추가해 처음 이유를 저장하는 방법을 추천한다.
  - 그 경우 지난 기록의 이유는 그때 냉장고 기준으로 고정된다 ("내일까지인 고추"가 지금 냉장고를 따라가지 않는다).
