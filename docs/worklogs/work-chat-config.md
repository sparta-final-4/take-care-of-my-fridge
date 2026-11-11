# 상담 설정 정리: Properties 15개를 5개로, 기본값은 yml 한 곳, 비면 기동 실패

상태: 완료 · 작성일: 2026-11-11 · 담당: zcookiez

브랜치 `refactor/chat-config`(`refactor/chat-packages` 위)에서 한 작업이다. 커밋은 로컬에만 있고 push·PR은 하지 않았다.
앞선 기록: [상담 도메인 패키지 정리](work-chat-packages.md).

## 목표와 완료 기준
- 사용자 문제
  - `domain/chat/config`에 Properties가 15개였다. yml은 이미 chat-words.yml·chat-safety.yml·chat-candidate.yml로 나뉘어 있는데 Properties는 묶음마다 따로였다.
  - 기본값이 `@DefaultValue`와 yml 두 곳에 있었고, 기본값이 없는 목록은 yml이 빠지면 조용히 빈 목록이 됐다.
- 완료 조건
  - 합치기 커밋은 동작 그대로. 커밋마다 chat 테스트와 재생 테스트(`ChatIntentReplayTest`) 통과.
  - 꼭 있어야 하는 목록이 비면 서버가 뜨지 않는다. 제외 목록은 비어도 된다.
  - 마지막에 `./gradlew clean compileJava compileTestJava test` 통과, local 프로필 bootRun 기동 확인.

## 근거와 범위
- 조사 결과
  - `@DefaultValue`와 yml 값은 11개 클래스 모두 같았다. 실제로 쓰이는 값은 yml이라 빼도 동작이 바뀌지 않는다.
  - 테스트도 main의 application.yml을 읽고, 그 `spring.config.import`가 chat yml 셋을 불러온다. 테스트 전용 yml은 없다.
  - 주석의 `{@link}`는 끊긴 것이 없었다. config·dto 주석의 클래스 언급은 경로 없는 글자라 깨지지 않는다.
- 다른 도메인 영향: search의 `RecipeDocumentReader`는 `ChatCandidateProperties`를 그대로 쓴다. `RecipeDocumentReaderTest`가 빈으로 등록한 고정값만 고쳤다(아래).
- 결정: yml 키는 `app.chat.words.*`, `app.chat.safety.*`로 한 단계 묶는다. `@NotEmpty`는 제외 목록만 빼고 전부, 중첩 record에는 `@Valid`.

## 계획
| 커밋 | 변경 | 검증 |
|---|---|---|
| 027a22c7 refactor: 단어 설정을 ChatWordProperties로 합치기 | 8개 → `ChatWordProperties`(Taste·TasteOpposites·Ask·Category·Food·Mealkit·FridgeShop·Temperature), chat-words.yml을 `app.chat.words` 아래로 | chat 794개, 재생 통과 |
| 5cc751bb refactor: 안전 설정을 ChatSafetyProperties로 합치기 | 4개 → `ChatSafetyProperties`(Words·Health·Allergy·AllergyGroup 목록), chat-safety.yml을 `app.chat.safety` 아래로 | chat 794개, 재생 통과 |
| ecd8fb59 refactor: 상담 설정 기본값은 yml 한 곳에서만 | 5개 클래스의 `@DefaultValue` 삭제 | chat 794개, 재생 통과 |
| 2ffff578 fix: 상담 단어 목록이 비면 서버가 뜨지 않게 | `@Validated`·`@Valid`·`@NotNull`·`@NotEmpty`·`@NotBlank`, 바인딩 테스트 하나로 합침, 빈 목록 실패 테스트 | chat 796개, 재생 통과, 전체 통과 |

## 합친 뒤 구조
```
config/ChatProperties            app.chat              (그대로)
config/ChatCorrectionProperties  app.chat.corrections  (그대로)
config/ChatCandidateProperties   app.chat.candidate    (그대로, search도 씀. @Validated 추가)
config/ChatWordProperties        app.chat.words        chat-words.yml
config/ChatSafetyProperties      app.chat.safety       chat-safety.yml
```
- yml 안쪽 키 이름 변경: `taste-words` → `taste`, `ask-words` → `ask`, `category-words` → `category`, `food-words` → `food`, `mealkit-words` → `mealkit`, `fridge-shop-words` → `fridge-shop`, `temperature-words` → `temperature`, `safety-words` → `words`, `health-words` → `health`, `allergy-words` → `allergy`. `allergy-groups`는 `groups` 한 겹을 빼고 바로 목록이다.
- 주입: ChatIntentFacade 7→3, IntentCorrector 7→2, ChatRecommendationFacade 5→2, ChatStreamService 2→1, AllergyExclusionFinder 2→1.
- 단어 확인 클래스의 정적 메서드는 중첩 record(`ChatWordProperties.Taste` 등)를 받는다.
- 비어도 되는 목록: `safety.words.exclusions`, `safety.health.exclusions`, `words.temperature.hotDishExclusions`, `safety.allergy.notSameKind`, `candidate.spicyExclusions`.

## AI 활용과 개발자 판단
- AI에 맡긴 작업: 조사(Properties별 키·쓰는 곳·기본값 위치, 테스트 생성 지점), 합치기, 검증 추가, 테스트 정리, 기동 확인.
- 조사 단계에서 "주석 `{@link}`가 끊긴다"는 앞선 판단이 틀렸음을 확인해 주석 정리 커밋은 만들지 않았다. config javadoc에는 비어도 되는 목록만 적었다.
- 계획과 달라진 점
  - 빈으로 등록한 Properties도 같은 검증을 거친다. search의 `RecipeDocumentReaderTest`가 `@Bean`으로 만든 `ChatCandidateProperties`에 빈 목록이 있어 전체 테스트에서 컨텍스트가 뜨지 않았다. 테스트 레시피에 없는 이름("양념", "고추냉이")으로 채워 4번 커밋에 넣었다(push 전이라 amend).
  - 빈 목록 실패 테스트는 `SpringApplicationBuilder.properties()`가 yml보다 우선순위가 낮아 실패를 재현하지 못했다. 명령줄 인자(`--app.chat.words.temperature.warm=`)로 덮어쓰게 바꿨다.
- 테스트 고정값: 묶음별 값은 각 WordCheckerTest에 두고, 전체는 `ChatIntentFacadeTest.WORDS`, `SafetyWordCheckerTest.ALL`로 묶었다. `SafetyWordCheckerTest`를 public으로 열었다.

## 검증
| 명령·시나리오 | 실행 환경·조건 | 결과 | 근거 |
|---|---|---|---|
| 커밋마다 `compileJava compileTestJava` + `test --tests "…domain.chat.*"` | 로컬, Docker(Testcontainers) | 통과 | 1~3번 794개, 4번 796개, 실패 0 |
| 커밋마다 `ChatIntentReplayTest` | chat 테스트에 포함 | 통과 | 1개, 실패 0 |
| `./gradlew clean compileJava compileTestJava test` | 로컬 | 통과 | 2,318개, 실패 0, 건너뜀 2 (첫 실행은 search 테스트 5개 실패 → 위 수정 후 재실행) |
| `./gradlew bootRun --args='--spring.profiles.active=local'` | dev MySQL(validate), 로컬 Docker Redis·Kafka·ES | 기동 | "Started TakeCareOfMyFridgeBeApplication in 8.5 seconds", ERROR 로그 0. 확인 후 종료 |
| 빈 목록이면 기동 실패 | `ChatConfigYamlBindingTest` | 통과 | `BindValidationException`, 제외 목록은 비어도 기동 |

## 연결과 남은 작업
- 실제 커밋: 로컬 `refactor/chat-config` 4개 + 이 기록(push 전).
- 배포 전 확인: 운영 Parameter Store에서 옛 키(`app.chat.taste-words.*` 등)를 덮어쓰는 값이 있으면 이제 무시된다. 코드·문서에는 없었다.
- `@NotEmpty` 때문에 yml이 깨지면 기동이 실패한다. 의도한 동작이며 dev 배포 때 한 번 확인한다.
- 앞 PR(`refactor/chat-packages`)이 squash 머지되면 이 브랜치를 dev 위로 rebase해야 한다.
