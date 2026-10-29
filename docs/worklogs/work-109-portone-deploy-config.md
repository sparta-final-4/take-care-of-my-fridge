# WORK-109: dev 배포에 카카오페이 결제 설정 연결

상태: 완료 · 작성일: 2026-10-28 · 담당: 결제·환불

## 목표와 완료 기준

- 결제 채널을 KG이니시스에서 카카오페이(테스트 결제)로 바꾼다.
- PortOne 키를 코드에 두지 않고 로컬·CI·배포 환경에서 각각 주입한다.
- dev 배포 서버가 정상 기동하고 결제할 수 있다.

## 근거와 범위

- PR: #323(10/28).
- 백엔드는 `application.yml`에서 `${PORTONE_*}` 환경변수를 읽는다. 배포(prod 프로필)는 `application-prod.yml`이 가져오는 AWS Parameter Store `/imhungry/<APP_ENV>/`에서 읽는다. 배포 워크플로(`be-deploy.yml`)는 고치지 않는다.
- 필요한 값은 4개다: `PORTONE_API_SECRET`, `PORTONE_STORE_ID`, `PORTONE_CHANNEL_KEY`, `PORTONE_WEBHOOK_SECRET`. API 주소(`base-url`)는 `application.yml`에 고정한다.
- 프론트 상점 ID·채널 키는 공개 값이라 `.env.production`에 둔다.

## 구현 결과

- `application.yml`·`application-prod.yml`: PortOne 값 4개를 기본값 없는 환경변수로 바꿨다.
- `.github/workflows/be-ci.yml`: 테스트 전에 `PORTONE_WEBHOOK_SECRET="whsec_$(openssl rand -base64 32)"`를 넣는다.
  - 서버가 뜰 때 `PortOneConfig`가 웹훅 시크릿을 base64로 풀어 `WebhookVerifier`를 만든다. 값이 없으면 `${PORTONE_WEBHOOK_SECRET}` 글자가 그대로 들어가 `Illegal base64 character 24`로 기동이 실패한다.
  - 다른 세 값은 기동 때 쓰지 않고, 테스트는 PortOne을 실제로 부르지 않아 형식만 맞춘 임의 값이면 된다.
- 프론트: `.env.production`·`.env.example`에 카카오페이 상점 ID·채널 키. `portone.ts`의 결제 수단을 `EASY_PAY`로 바꾸고 KG이니시스 주석을 정리했다.

## 검증

| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| 백엔드 전체 테스트 | 로컬(Windows), CI와 같은 조건(PortOne 값 없이 임의 JWT·웹훅 시크릿) | 통과 | 844건, 실패 0 |
| dev 배포 헬스 체크 | dev, #323 머지 직후 | 실패 | 서버가 뜨지 않아 Caddy 502. 직전 배포(#322)는 정상이었고 #323부터 Parameter Store 값이 필요해짐 |
| Parameter Store 값 수정 후 dev 기동 | dev | 통과 | 인프라 담당이 값을 고친 뒤 `GET /api/auth/csrf` 200 |

<details><summary>배포 502 원인과 해결</summary>

- 증상: 프론트는 뜨고 로그인·주문도 됐지만 결제 시점부터 API가 502를 돌려줬다. 백엔드 컨테이너가 기동에 실패해 리버스 프록시(Caddy)가 연결할 곳이 없었다.
- 원인: #323부터 prod 프로필이 PortOne 값을 Parameter Store에서 읽는다. 값이 없거나 형식이 맞지 않으면 웹훅 시크릿을 base64로 풀다가 기동이 실패한다. 전달한 웹훅 시크릿 끝에 `\`가 섞여 있어(`\=`) 그대로 저장됐다면 `Illegal base64 character 5c`가 난다. 서버 로그는 직접 보지 못해 어느 값이 문제였는지는 확인하지 못했다.
- 해결: 인프라 담당에게 4개 키와 저장 형식(따옴표·이스케이프 없이 원문, 웹훅 시크릿은 `whsec_`로 시작)을 전달해 값을 고친 뒤 정상 기동을 확인했다.

</details>

## 연결과 남은 작업

- 웹훅 URL: `https://api-dev.imhungry.click/api/webhooks/portone`.
- main 배포 전에 `/imhungry/main/`에도 같은 4개 값을 넣어야 한다.
