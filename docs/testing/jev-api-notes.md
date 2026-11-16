# Jev(TypeSafe) API 조사 기록

상태: **실제 호출로 확인 완료** · 조사일: 2026-11-16 · 브랜치: `feat/chat-jev-shadow`

상담 의도 판단에 Jev(TypeSafe AI의 분류 전용 "System One" 모델)를 그림자 모드로 붙이기 전에 API 사용법을 정리한다.
설정 값은 `app.chat.jev`(`ChatJevProperties`)에 있다. 이 문서에는 API 키를 쓰지 않는다. curl에서는 `"$TYPESAFE_API_KEY"`로만 참조한다.

> 키 없는 호출 2건, 사용자가 직접 한 키 있는 호출 2건(모델 목록, Choice 하나), 이 작업에서 한 키 있는 호출 7건(a~g)을 [실제 호출](#실제-호출-2026-11-16)에 적었다.
> 각 경우는 **한 번씩만** 호출했다. 지연·확률은 한 번 측정한 값이라 흔들릴 수 있다.

> **한글 본문은 UTF-8로 보내야 한다.** Windows에서 `curl -d '{"state":"김피탕…"}'`처럼 한글을 명령줄로 넘기면 인코딩이 깨져
> `There was an error parsing the body`가 난다. 본문을 UTF-8(BOM 없음) 파일로 만들어 `--data-binary @파일`로 보내고
> `Content-Type: application/json; charset=utf-8`을 붙인다. 자바 구현도 요청 본문을 UTF-8 바이트로 보내야 한다
> (문자열을 기본 문자셋으로 바꾸지 않는다. `RestClient`·Jackson 기본 JSON 변환은 UTF-8이지만 직접 `getBytes()`를 쓰면 `StandardCharsets.UTF_8`을 넘긴다).

## 공식 문서

| 주소 | 내용 |
|---|---|
| https://docs.typesafe.ai/ | 소개 |
| https://docs.typesafe.ai/llms.txt | 문서 목록 |
| https://docs.typesafe.ai/api/system-one.md | `POST /v1/systemone` (OpenAPI 스키마·예시) |
| https://docs.typesafe.ai/api/list-models.md | `GET /v1/models` |
| https://docs.typesafe.ai/api/errors.md | 오류 코드 |
| https://docs.typesafe.ai/primitives/choice.md · score.md · noul.md | 질문 종류 |
| https://docs.typesafe.ai/models.md | 모델 이름·가격·문맥 한도 |
| https://api.typesafe.ai/openapi.json | 전체 OpenAPI (이번에 읽지 않음) |
| https://console.typesafe.ai/rate-limits | 조직별 속도 제한 (콘솔, 읽지 않음) |

## 요청

`POST https://api.typesafe.ai/v1/systemone`, 헤더 `Authorization: Bearer <키>`, `Content-Type: application/json` (UTF-8, BOM 없음).

| 칸 | 필수 | 뜻 |
|---|---|---|
| `model` | 예 | `jev-latest` 등 |
| `state` | 예 | 모든 질문이 가리키는 내용. 문자열·객체·배열 |
| `questions` | 예 | **객체(이름 → 질문)**. 목록이 아니다. 1개 이상. 응답 `answers`도 같은 이름으로 온다 |
| `assets` | 아니오 | 이미지 (Box 질문용) |

질문 종류(`type`):

| type | 칸 | 뜻 |
|---|---|---|
| `choice` | `instructions`(질문), `criteria`(필수, **선택지 이름 → 설명** 객체, 1~255개) | 선택지 중 하나. 설명은 `criteria`의 값으로 넣는다 (따로 `description` 칸은 없다) |
| `score` | `instructions`, `criteria`(필수, **순서 있는 단계 설명 배열**) | 단계 위 점수. 단계 번호는 0부터. OpenAPI는 1~10개, 설명 페이지는 "2개 이상, 10개까지" |
| `noul` | `instructions`(예/아니오 질문 문장), `criteria`(선택, `"true"`·`"false"` 설명) | **예/아니오 확률**. "확인 필요"라는 뜻이 아니다 |
| `box` | `instructions` | 이미지 위치. `TypeSafe-Danger-Zone-Images: true` 헤더가 필요 |

긴 문맥은 `TypeSafe-Danger-Zone-Long-Context: true` 헤더로 늘린다.

문서 예시(요약):
```json
{"model":"jev-latest","state":"I was charged twice for my subscription this month. Please refund the extra charge.",
 "questions":{"tone":{"type":"choice","instructions":"What is the tone of this message?",
   "criteria":{"angry":"An upset or hostile message","calm":"A neutral or polite message","excited":"An enthusiastic or eager message"}}}}
```

## 응답

| 칸 | 뜻 |
|---|---|
| `model` | 답한 모델 이름. **별칭과 다를 수 있다** (예: `jev-1.13.0`). 버전 확인에 쓸 수 있다 |
| `answers` | 질문 이름 → 답 |
| `usage.input_tokens` | 과금 입력 토큰 |
| `usage.output_tokens` | 출력 토큰 (지금 무료) |

답 모양:

| type | 칸 |
|---|---|
| choice | `choice`(확률이 가장 큰 선택지), `confidence`(0~1), `probabilities`(선택지 → 0~1, 설명 페이지는 합 1, OpenAPI는 "대략 1") |
| score | `score`(단계 번호의 확률 가중 평균), `confidence`, `legend`(번호 → 설명), `probabilities`("0","1",… → 0~1) |
| noul | `noul`(예일 확률 0~1). **`confidence` 없음** |

문서 예시:
```json
{"model":"jev-latest","answers":{"tone":{"type":"choice","choice":"calm","confidence":0.7,
  "probabilities":{"angry":0.15,"calm":0.8,"excited":0.05}}},"usage":{"input_tokens":120,"output_tokens":12}}
{"model":"jev-latest","answers":{"billing":{"type":"noul","noul":0.98}},"usage":{"input_tokens":120,"output_tokens":12}}
```

요청 id는 본문에 없고, 실제 호출에서 응답 헤더 `x-typesafe-request-id`로 왔다(아래).

실제 응답에서 확인한 것:
- `model`은 `jev-latest`로 보내도 `jev-1.14.0`으로 온다. 버전 고정 이름(`jev-1.14.0`)으로 보내도 같다.
- `probabilities`는 소수 둘째 자리로 반올림되어 오고, 이번 응답들은 모두 합이 1.00이었다.
- **`confidence`는 가장 큰 확률과 같지 않다.** 확률이 퍼질수록 더 낮다
  (매운맛 `언급 없음` 0.61 → confidence 0.42, 분류 `국` 0.72 → 0.71, 감칠맛 `언급 없음` 0.93 → 0.89). 기준값은 둘 중 하나로 정해 같은 칸만 쓴다.
- Score의 `score`는 단계 번호의 확률 가중 평균이 맞다 (0×0.02 + 1×0.16 + 2×0.46 + 3×0.36 = 2.16).
- `usage.output_tokens`는 질문·선택지 수에 따라 늘었다 (Noul 33개 598, 최대 크기 770). 출력은 무료라 비용에 영향이 없다.

## 한도

- 본문 32 MiB 이하(넘으면 413). state·instructions·criteria 각각 중첩 64단 이하.
- 문맥: 요청 전체(state + 모든 질문) 64k 토큰, 질문 하나(state + 질문) 32k 토큰. 긴 문맥 헤더로 262k.
- **한 요청의 질문 수 한도는 따로 없다** (문맥 한도만).
- Choice 선택지 255개, Score 단계 10개.

## 모델

| 이름 | 가리키는 모델 |
|---|---|
| `jev-latest` | `jev-1.14.0` (최신 정식) |
| `jev-preview` | 지금은 `jev-1.14.0` (latest와 같음) |
| `jev-1.14.0`, `jev-1.13.0` | 버전 고정 이름. `GET /v1/models` 목록에는 별칭만 나오지만 `model`에 넣을 수 있다 |

문서는 신뢰도 기준을 맞춰 둔 버전이 있으면 버전 고정 이름을 쓰라고 권한다.
`GET /v1/models` 응답(문서): `{"models":[{"name":"jev-latest","description":"General-purpose system one model.","release_date":"2026-09-15"}]}`

실제 확인: `model: "jev-1.14.0"`으로 보낸 호출(g)이 200으로 답했고 응답 `model`도 `jev-1.14.0`이었다.
그래서 `app.chat.jev.model`을 `jev-1.14.0`으로 고정했다. 버전을 올릴 때는 평가를 다시 돌린 뒤 바꾼다.

## 오류

문서(`errors.md`): 본문은 `{"detail": 문자열 | 객체}`.

| 코드 | 뜻 |
|---|---|
| 400 | 요청 규칙 위반 (UTF-8, 중첩, 무한대 숫자 등) |
| 401 | 키 없음·틀림 |
| 413 | 본문 32 MiB 초과 |
| 415 | 지원하지 않는 Content-Encoding |
| 422 | 검증 실패. `{"detail":[{"loc":[…],"msg":"…","type":"…","input":…,"ctx":{…}}]}` |
| 429 | 속도 제한. 지수 백오프로 다시 |
| 529 | 일시 과부하 |

- 실제: 키가 없으면 **403**(문서는 401), 틀리면 401. 인증 오류 `detail`은 `{"error_type","message"}` 객체다.
- 실제: 빈 `questions`는 422, `detail`은 문서 모양 그대로 배열이다(아래 f).
- 크레딧 부족(402 등) 응답은 문서에 없고, 이번에도 겪지 않아 확인하지 못했다.
- 속도 제한 헤더는 `retry-after`만 언급된다(SDK가 있으면 따른다). 숫자 한도는 콘솔에만 있다.
  실제 응답(200·401·403·422 모두)에는 `x-ratelimit-*`·`retry-after` 헤더가 없었다. 429를 받았을 때 `retry-after`가 오는지는 확인하지 못했다.

## 실제 호출 (2026-11-16)

지연은 `curl -w "%{time_total}"`(서울 → Cloudflare `ICN`, 연결 맺기 포함), 한 번씩만 쟀다. 모든 응답에 `x-typesafe-request-id` 헤더가 왔고 속도 제한 헤더는 없었다.

| 호출 | 내용 | HTTP | 응답 `model` | 입력 토큰 | 출력 토큰 | 지연(초) |
|---|---|---|---|---|---|---|
| 키 없이 | 모델 목록 | 403 | - | - | - | 0.168 |
| 틀린 키 | systemone | 401 | - | - | - | 0.184 |
| 사용자 | 모델 목록 | 200 | - | - | - | 0.20 |
| 사용자 | Choice 하나 (의도 5개) | 200 | jev-1.14.0 | 414 | 66 | 0.21 |
| a | 의도 Choice + Noul + 매운맛 Choice (질문 3개) | 200 | jev-1.14.0 | 516 | 125 | 0.220 |
| b | 분류 Choice 하나 (선택지 34개) | 200 | jev-1.14.0 | 964 | 294 | 0.219 |
| c | 분류마다 Noul (질문 33개) | 200 | jev-1.14.0 | 1100 | 598 | 0.200 |
| d | 최대 크기 (질문 13개, 아래) | 200 | jev-1.14.0 | 1893 | 770 | 0.296 |
| e | Score 하나 (단계 4개) | 200 | jev-1.14.0 | 331 | 19 | 0.231 |
| f | 빈 `questions` | 422 | - | - | - | 0.166 |
| g | 의도 Choice, `model: jev-1.14.0` | 200 | jev-1.14.0 | 417 | 66 | 0.233 |

(사용자 호출과 g는 같은 질문이지만 선택지 설명 글이 조금 달라 입력 토큰이 3 차이 난다.)

### 키 없이 모델 목록 → 403 (문서는 401이라고 함)
```bash
curl -sS -i -w "time=%{time_total}\n" https://api.typesafe.ai/v1/models
```
```
HTTP/1.1 403 Forbidden
Content-Type: application/json
x-typesafe-request-id: req_01a1e37052797108a31a16e1a05abcc1
Server: cloudflare

{"detail":{"error_type":"authentication_error","message":"Must supply an API key! Check your request and try again."}}
time=0.168
```

### 틀린 키 → 401
```bash
curl -sS -i -w "time=%{time_total}\n" -X POST https://api.typesafe.ai/v1/systemone \
  -H "Authorization: Bearer invalid-key-for-test" -H "Content-Type: application/json; charset=utf-8" \
  --data-binary '{"state":"test","model":"jev-latest","questions":[]}'
```
```
HTTP/1.1 401 Unauthorized
Content-Type: application/json
x-typesafe-request-id: req_01a1e37053a979918be0daaf64cd714e
Server: cloudflare

{"detail":{"error_type":"authentication_error","message":"Cannot authenticate with the server. Please check your API key and try again."}}
time=0.184
```

### 공통 명령 (a~g)

본문은 Node로 UTF-8(BOM 없음) 파일을 만들어 보냈다.
```bash
curl -sS -i -w "\ntime=%{time_total}\n" -X POST https://api.typesafe.ai/v1/systemone \
  -H "Authorization: Bearer $TYPESAFE_API_KEY" -H "Content-Type: application/json; charset=utf-8" \
  --data-binary @a.json
```

정상 응답 헤더(d, 쿠키 값 생략):
```
HTTP/1.1 200 OK
Content-Type: application/json
Transfer-Encoding: chunked
x-typesafe-request-id: req_01a1e3988ea5774cbccef3fc850ce1f1
Server: cloudflare
cf-cache-status: DYNAMIC
set-cookie: __cf_bm=…
CF-RAY: …-ICN
```

### a. 한 요청에 질문 3개

```json
{"model":"jev-latest","state":"김피탕 같은 요리 추천해줘","questions":{
 "intent":{"type":"choice","instructions":"이 말은 무엇을 원하나?","criteria":{"추천":"요리나 레시피를 추천해 달라는 말","일반 질문":"요리·재료에 대한 질문","냉장고 보기":"냉장고 재료를 보여 달라는 말","장보기":"재료나 상품을 사고 싶다는 말","상관없는 말":"요리와 상관없는 말"}},
 "dishNamed":{"type":"noul","instructions":"요리 이름을 말했나?"},
 "spicy":{"type":"choice","instructions":"매운맛에 대해","criteria":{"원함":"매운맛을 원한다","피함":"매운맛을 피하고 싶다","언급 없음":"매운맛을 말하지 않았다"}}}}
```
```json
{"model":"jev-1.14.0","answers":{
 "intent":{"type":"choice","choice":"추천","confidence":1.0,"probabilities":{"추천":1.0,"일반 질문":0.0,"냉장고 보기":0.0,"장보기":0.0,"상관없는 말":0.0}},
 "dishNamed":{"type":"noul","noul":0.92},
 "spicy":{"type":"choice","choice":"언급 없음","confidence":0.42,"probabilities":{"원함":0.39,"피함":0.0,"언급 없음":0.61}}},
 "usage":{"input_tokens":516,"output_tokens":125}}
```
- 질문 3개의 답이 같은 이름으로 한 번에 온다. 요리 이름(김피탕)은 0.92로 잡았다.
- 매운맛을 말하지 않았는데 `원함` 0.39가 나왔다. `confidence` 0.42로 낮아서, confidence 기준으로 자르면 "확실하지 않음"으로 걸러진다.

### b. 분류 Choice 하나 (선택지 34개)

state `따뜻한 국물 요리 추천해줘`, 질문 `원하는 요리는 어느 분류인가?`, 선택지는 분류 33개(설명 `"{분류} 분류의 요리"`) + `없음`(설명 `분류를 말하지 않았거나 맞는 분류가 없음`).

```json
{"model":"jev-1.14.0","answers":{"category":{"type":"choice","choice":"국","confidence":0.71,"probabilities":{
 "국":0.72,"없음":0.22,"수프":0.04,"찌개":0.01,"기타":0.01, "(나머지 29개)":0.0}}},
 "usage":{"input_tokens":964,"output_tokens":294}}
```
(응답은 34개를 모두 주지만 0.0인 29개는 줄였다.)
- 확률이 거의 `국` 하나에 몰리고, 둘째가 `없음` 0.22다. 국물 요리에 맞는 `찌개`·`전골`·`스튜`는 0.01 이하다.

### c. 분류마다 Noul (질문 33개)

같은 state, 질문 `원하는 요리가 '{분류}' 분류에 해당하나?`를 `cat00`~`cat32`로 33개.

| 분류 | noul | | 분류 | noul |
|---|---|---|---|---|
| 국 | 0.83 | | 기타 | 0.27 |
| 수프 | 0.70 | | 밥 | 0.25 |
| 찌개 | 0.46 | | 죽 | 0.24 |
| 스튜 | 0.42 | | 떡·조림·찜 | 0.17 |
| 전골 | 0.41 | | 양념 | 0.13 |
| **말이** | **0.40** | | 만두 | 0.12 |
| 면류 | 0.31 | | 김치 | 0.11 |

나머지 16개는 0.02~0.09. 입력 1100 토큰, 0.200초.

### b·c 비교

| | b. Choice 하나 | c. 분류마다 Noul |
|---|---|---|
| 입력 토큰 | 964 | 1100 (+14%) |
| 지연 | 0.219초 | 0.200초 (차이 없음) |
| 1등 | 국 0.72 | 국 0.83 |
| 국물 분류(국·수프·찌개·스튜·전골) | 국만 높고 나머지 0.05 이하 | 다섯 개가 상위 1~5위 (0.83~0.41) |
| 틀린 분류 | 없음 (둘째가 `없음` 0.22) | `말이` 0.40이 `전골` 0.41 바로 아래 |
| 0.5로 자르면 | 국 | 국, 수프 |

### d. 실제로 쓸 최대 크기 (질문 13개)

state `따뜻한 국물 요리 추천해줘`. 의도 Choice(5개), 맛 Choice 5개(매운·단·짠·감칠·순한, 각 원함/피함/언급 없음), 묻는 말 Choice(메뉴 추천/만드는 법/조리 시간/재료/팁/그 밖),
냉장고 범위 Choice(전체/밀키트/임박/양념/해당 없음), Noul 4개(밀키트, 요리 이름, 요리 특징, 재료 이름), 분류 Choice(b와 같은 34개).

```json
{"model":"jev-1.14.0","answers":{
 "intent":{"choice":"추천","confidence":1.0},
 "spicy":{"choice":"언급 없음","confidence":0.98}, "sweet":{"choice":"언급 없음","confidence":1.0},
 "salty":{"choice":"언급 없음","confidence":1.0}, "savory":{"choice":"언급 없음","confidence":0.89},
 "mild":{"choice":"언급 없음","confidence":0.97},
 "ask":{"choice":"메뉴 추천","confidence":1.0},
 "fridgeScope":{"choice":"해당 없음","confidence":0.9},
 "mealkit":{"noul":0.08}, "dishNamed":{"noul":0.09}, "featureNamed":{"noul":0.74}, "ingredientNamed":{"noul":0.07},
 "category":{"choice":"국","confidence":0.74,"probabilities":{"국":0.75,"없음":0.18,"수프":0.05,"찌개":0.01,"기타":0.01}}},
 "usage":{"input_tokens":1893,"output_tokens":770}}
```
(`type`과 0.0인 확률은 줄였다.) 13개 답 모두 문장에 맞았다. 분류 답은 b 단독(국 0.72)과 거의 같아, 다른 질문과 함께 물어도 흔들리지 않았다. 0.296초.

### e. Score 하나

```json
{"model":"jev-latest","state":"김피탕 같은 요리 추천해줘","questions":{"spiciness":{"type":"score",
 "instructions":"원하는 요리는 얼마나 매워야 하나?","criteria":["맵지 않음","조금 매움","보통 매움","아주 매움"]}}}
```
```json
{"model":"jev-1.14.0","answers":{"spiciness":{"type":"score","score":2.16,"confidence":0.44,
 "legend":{"0":"맵지 않음","1":"조금 매움","2":"보통 매움","3":"아주 매움"},
 "probabilities":{"0":0.02,"1":0.16,"2":0.46,"3":0.36}}},"usage":{"input_tokens":331,"output_tokens":19}}
```
- 맵기를 말하지 않은 문장에도 "보통~아주 매움"으로 답한다. Score에는 "언급 없음" 단계가 없으므로, 말했는지는 Choice(원함/피함/언급 없음)로 묻는 게 맞다.

### f. 잘못된 본문 → 422

```json
{"model":"jev-latest","state":"김피탕 같은 요리 추천해줘","questions":{}}
```
```
HTTP/1.1 422 Unprocessable Entity
Content-Type: application/json
x-typesafe-request-id: req_01a1e39891627987a69e8d113a489348

{"detail":[{"type":"too_short","loc":["body","questions"],"msg":"Dictionary should have at least 1 item after validation, not 0","input":"{}","ctx":{"field_type":"Dictionary","min_length":1,"actual_length":0}}]}
```

### g. 버전 고정 이름

a의 `intent` 질문 하나만 `"model":"jev-1.14.0"`으로 보냈다.
```json
{"model":"jev-1.14.0","answers":{"intent":{"type":"choice","choice":"추천","confidence":1.0,"probabilities":{"추천":1.0,"일반 질문":0.0,"냉장고 보기":0.0,"장보기":0.0,"상관없는 말":0.0}}},"usage":{"input_tokens":417,"output_tokens":66}}
```
200으로 답했다. 별칭(`jev-latest`)과 결과·지연이 같다.

## 다시 확인하는 법

키는 환경 변수로만 넣는다. 한글 본문은 UTF-8 파일로 만들어 `--data-binary @파일`로 보낸다
(명령줄에 한글을 직접 쓰면 Windows에서 깨져 `There was an error parsing the body`가 난다. PowerShell 5.1 curl도 UTF-8로 보내지 않음, [기준 기록](chat-recipe-search-baseline.md#es-분석기-확인) 참고).

```bash
# 모델 목록
curl -sS -i -w "\ntime=%{time_total}\n" -H "Authorization: Bearer $TYPESAFE_API_KEY" https://api.typesafe.ai/v1/models

# 본문 파일을 UTF-8로 만든 뒤
curl -sS -i -w "\ntime=%{time_total}\n" -X POST https://api.typesafe.ai/v1/systemone \
  -H "Authorization: Bearer $TYPESAFE_API_KEY" -H "Content-Type: application/json; charset=utf-8" \
  --data-binary @body.json
```

## 의도 판단에 쓸 때 고려할 점

### 질문 수에 따른 지연·토큰

| 경우 | 질문 수 | 입력 토큰 | 지연(초) | 입력 비용 ($0.042/1M) |
|---|---|---|---|---|
| 의도 Choice 하나 | 1 | 414 | 0.21 | $0.000017 |
| a | 3 | 516 | 0.22 | $0.000022 |
| b. 분류 Choice | 1 (선택지 34) | 964 | 0.22 | $0.000040 |
| c. 분류 Noul | 33 | 1100 | 0.20 | $0.000046 |
| d. 최대 크기 | 13 | 1893 | 0.30 | $0.000080 |

- 입력 토큰은 질문·선택지 글자 수만큼 늘지만, 지연은 질문 33개까지 0.2초대로 거의 같고 최대 크기(d)에서 0.3초다. 질문 수보다 연결·왕복 시간이 크다.
- 한 번씩만 잰 값이라 0.05초 안팎 차이는 흔들림일 수 있다. 설정 timeout 3초는 넉넉하다.

### 분류 여러 개 고르기: Choice 상위 여러 개 vs 분류마다 Noul

- **Choice 상위 여러 개는 쓰기 어렵다.** 확률을 나눠 갖는 구조라 1등에 몰리고, 맞는 둘째·셋째 분류(찌개·전골·스튜)가 0.01 이하로 눌린다. 둘째가 `없음`(0.22)이라 "상위 3개"로 자르면 틀린 답이 섞인다.
- **분류마다 Noul은 순서가 더 맞다.** 국물 분류 다섯 개가 상위 1~5위였다. 다만 서로 독립이라 값이 높게 퍼지고, 틀린 `말이` 0.40이 `전골` 0.41 바로 아래에 있어 깔끔한 기준값이 없다(0.5면 국·수프만).
- 비용·지연 차이는 작다(+136 토큰, 지연 차이 없음).
- **제안**: 대표 분류 하나만 필요하면 Choice(1등 + confidence 기준). 여러 분류가 필요하면 분류마다 Noul을 쓰고 0.5부터 시작해 평가 문장으로 기준값을 맞춘다. 문장 하나로 본 결과라 결정 전에 평가 묶음으로 다시 비교한다.

### 한 요청에 다 묻기 vs 의도 먼저 묻고 추천이면 다시 묻기

- 한 번에(d): 모든 메시지 **0.30초**, 입력 1893 토큰($0.00008).
- 두 번에: 의도만 0.21초, 추천이면 나머지를 한 번 더(적어도 0.2초대) → 추천 메시지는 **0.4초 이상**. 추천이 아닌 메시지는 0.21초·414 토큰.
- 상담 메시지는 대부분 추천이라 두 번에 묻으면 평균 지연이 더 길어진다. 아끼는 비용은 추천이 아닌 메시지당 약 1500 토큰($0.00006)뿐이다.
- **제안: 한 요청에 다 묻는다.** 호출·실패 지점이 하나라 그림자 모드 기록도 단순하다. d에서 질문을 함께 물어도 분류 답이 흔들리지 않았다(국 0.72 → 0.75).

### 그 밖

- **버전 고정**: `jev-1.14.0`으로 고정했다(`app.chat.jev.model`). `jev-latest`는 바뀔 수 있으므로, 버전을 올릴 때는 평가를 다시 돌린 뒤 바꾼다. 응답 `model`을 로그에 남겨 실제 답한 버전을 확인한다.
- **UTF-8 본문**: 요청 본문은 UTF-8 바이트로 보낸다(`Content-Type: application/json; charset=utf-8`). 기본 문자셋으로 바꾸면 한글이 깨져 `There was an error parsing the body`가 난다.
- **확인 질문**: Noul은 "확인 필요"가 아니라 예/아니오 확률이다. "확실하지 않음"은 Choice·Score의 `confidence`로 판단한다(Noul에는 `confidence`가 없다). `confidence`는 1등 확률보다 낮게 나오므로 기준값은 `confidence`로 정한다(a의 매운맛: 1등 0.61, confidence 0.42).
- **맛 같은 "말했나"**: Score는 언급 없음을 나타낼 수 없어(e) Choice에 `언급 없음`을 넣는다.
- **인증 오류 코드**: 키가 없으면 403, 틀리면 401이다. 둘 다 같은 오류로 다룬다. 422 `detail`은 배열, 인증 오류 `detail`은 객체라 모양이 다르다.
- **속도 제한**: 정상 응답에도 속도 제한 헤더가 없다. 429는 지수 백오프로 다루고, 그림자 모드라 다시 시도하지 않고 기록만 해도 된다.
- **비용**: 입력 토큰만 과금(문서: $0.042 / 1M 토큰), 출력 무료. 최대 크기(d)도 메시지당 $0.00008이다. 크레딧 부족 응답은 확인하지 못했다.
