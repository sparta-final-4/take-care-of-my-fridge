# 상담 공백 지우기 함수 하나로 모으기

상태: 완료 · 작성일: 2026-11-12 · 담당: 이현정

## 목표와 완료 기준
- 사용자 문제: 상담 코드의 `private static String compact(String text)`가 17곳에 복사돼 있었다. 그중 5곳은 `replaceAll("\s+", "")`라서 Java 15+에서 `"\s"`가 스페이스 한 칸 이스케이프로 읽혀, 줄바꿈·탭을 지우지 않았다. null 처리도 복사본마다 달랐다.
- 확인 가능한 완료 조건
  - 공용 함수 하나(`ChatTexts.removeWhitespace`): null이면 빈 글, 스페이스·줄바꿈·탭을 모두 지운다.
  - 17곳의 복사본을 지우고 공용 함수를 쓴다. `MealkitPicker.compact`(public)를 빌려 쓰던 곳도 바꾼다.
  - 5곳마다 줄바꿈이 낀 입력 테스트가 고치기 전에는 실패하고 고친 뒤 통과한다.
  - 상담 테스트 전체와 `ChatIntentReplayTest`가 통과한다. 재생 기준값은 고치지 않는다.

## 근거와 범위
- 변경할 영역: `domain/chat`만. 새 하위 폴더 `chat/text` (be/CLAUDE.md 3장 "기능 단위 하위 폴더").
- 다른 도메인 영향: 없음. search의 `RecipeDocumentReader`는 `SpicyRecipeDetector`를 통째로 부르므로, 매운 재료 이름에 줄바꿈이 있을 때만 결과가 달라진다(잡게 됨).
- 그대로 둔 비슷한 코드(동작이 다름): `IngredientAliasDictionary.key`(공백 제거 + 소문자), `QueryEmbeddingService.normalize`(공백을 한 칸으로), `IngredientMatcher`(정해진 패턴 제거).
- 위치 결정: chat 안(`domain/chat/text/ChatTexts`)에 둔다. 다른 도메인에서 필요해지면 그때 global로 옮긴다. be/CLAUDE.md는 고치지 않는다.

## 계획
| 단계 | 변경 | 검증 |
|---|---|---|
| 1 | `ChatTexts.removeWhitespace` + 같은 동작(`"\\s+"`) 12곳 교체 | `ChatTextsTest`, 상담 테스트 전체, 재생 테스트 |
| 2 | `"\s+"` 5곳 교체, `MealkitPicker.compact` 제거 | 줄바꿈 테스트 5개 (고치기 전 실패 확인), 상담 테스트 전체, `./gradlew clean test` |

## AI 활용과 개발자 판단
- AI에 맡긴 작업: 복사본 조사, 공용 함수 위치 제안, 교체, 테스트 작성.
- 채택: 정규식 `\s+`를 그대로 쓴다. Spring `StringUtils.trimAllWhitespace`는 전각 공백(U+3000)까지 지워 12곳의 결과가 달라질 수 있어 쓰지 않았다.
- 수정한 제안: 위치는 `global/util`(be/CLAUDE.md 구조표 수정 필요) 대신 chat 안으로 정했다(개발자 결정).
- 개발자가 확인한 근거: 원본 바이트에서 5곳이 `"\s+"`(백슬래시 하나)임을 확인했다.

## 구현 결과
- 새 파일: `domain/chat/text/ChatTexts.java`, `ChatTextsTest.java`.
- 커밋 1(동작 그대로): `ChatSafetyProperties`, `RecipeQuery`, `ChatFridgeShopFacade`, `ChatIntentFacade`, `ChatRecommendationFacade`, `FridgeShopWordChecker`, `IntentCorrector`, `TasteWordChecker`, `TemperatureWordChecker`, `RecommendationTextSplitter`, `AllergyExclusionFinder`, `HealthWordChecker`. 이 중 null을 받지 않던 5곳은 null이면 NPE 대신 빈 글이 된다.
- 커밋 2(줄바꿈·탭): `AskWordChecker`, `MealkitWordChecker`, `KeywordMatchClassifier`, `SpicyRecipeDetector`, `MealkitPicker`. `RecipeCandidateFacade`와 `MealkitPickerTest`가 빌려 쓰던 `MealkitPicker.compact`를 공용 함수로 바꾸고 public 메서드를 없앴다. 쓰지 않게 된 `MealkitPicker` import를 지웠다.
- 계획과 달라진 점: `MealkitWordChecker`의 첫 테스트(`"밀키트\n말고 레시피"`)는 고치기 전에도 통과했다. 부정어는 `TasteWordChecker.negatedAfter`가 따로 공백을 지워 보기 때문이다. 이 클래스 자신의 공백 지우기가 쓰이는 단어 찾기를 시험하도록 `"요리하기\n싫어"`(단어 "요리하기싫")로 바꿨다.
- API·DB·문서 변경: 없음.

## 검증
| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| 줄바꿈 테스트 5개, 고치기 전 | 커밋 1 위에서 실행 | 5개 모두 실패 | 예상대로 |
| `./gradlew test --tests 'org.example.takecareofmyfridgebe.domain.chat.*'` (커밋 1) | 로컬, Docker | 통과 | 804개, 수동 평가 1개 건너뜀 |
| 같은 명령 (커밋 2) | 로컬, Docker | 통과 | 809개(줄바꿈 5개 추가), 수동 평가 1개 건너뜀 |
| `ChatIntentReplayTest` | 위 실행에 포함 | 통과 | 기준값(expected.csv) 그대로, 바뀐 줄 없음 |
| `./gradlew clean test` (커밋 2) | 로컬, Docker | 통과 | 2,359개, 실패 0. 건너뜀 2개는 수동 테스트(`ChatIntentEvalManualTest`, `IngredientNameSearchServiceManualTest`) |

## 연결과 남은 작업
- 실제 커밋·PR: 브랜치 `fix/chat-compact-whitespace`. push·PR은 하지 않았다.
- 공용 함수는 chat 안에 둔다. 다른 도메인에서 필요해지면 그때 global로 옮긴다.
- 미검증: 실제 AI를 부르는 `ChatIntentEvalManualTest`는 수동 테스트라 실행하지 않았다.
