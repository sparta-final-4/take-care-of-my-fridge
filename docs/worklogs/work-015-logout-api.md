# WORK-015: 로그아웃 API

상태: 검증 대기 · 작성일: 2026-10-17 · 담당: 회원·인증

## 목표와 완료 기준

- 현재 로그인 세션을 폐기하고 인증 쿠키를 삭제한다.
- 액세스 쿠키가 만료됐거나 이미 로그아웃한 경우도 200을 반환한다. CSRF 누락은 403이다.

## 근거와 범위

- 근거: `docs/api/api-spec.md` 인증 API, 기존 로그인·갱신 세션 구현.
- 범위: `POST /api/auth/logout`, 현재 세션의 `revoked_at`, 액세스·갱신 쿠키 삭제.
- 다른 기기의 세션은 변경하지 않는다. 로그인·갱신 정책은 유지한다.

## 구현 결과

- 유효한 액세스 JWT의 세션 ID로 현재 세션을 잠가 폐기한다. 액세스 JWT가 만료된 경우 갱신 쿠키 해시로 세션을 찾아 폐기한다.
- 갱신 요청과 같은 행 잠금을 사용한다. 두 인증 쿠키를 발급 때와 같은 경로에서 `Max-Age=0`으로 삭제한다.
- 응답은 기존 `ApiResponse.ok()` 형식을 따른다. 신규 요청 DTO나 오류 코드는 없다.
- `api-spec.md`의 로그아웃 401 요약을 상세 명세의 만료 세션 200 정책에 맞췄다.

## 검증

| 명령·시나리오 | 결과 | 근거 |
|---|---|---|
| `./gradlew.bat test --tests '*LogoutServiceTest' --tests '*AuthControllerTest'` | 통과 | 로그아웃·인증 컨트롤러 테스트 |
| `./gradlew.bat test` | 62개 중 61개 통과, 1개 실패 | `contextLoads()`에서 로컬 MySQL `fridge_user` 인증 거부 |
| `./gradlew.bat clean compileJava compileTestJava` | 통과 | 전체 소스·테스트 소스 재컴파일 |

## 연결과 남은 작업

- 작업 브랜치: `member-logout-api`.
- 실제 DB 세션 행 잠금과 갱신 요청 경합 검증은 로컬 MySQL 접속이 가능할 때 확인한다.
- 프론트엔드는 로그아웃 요청에 `X-XSRF-TOKEN` 헤더를 보내고, 200 응답 후 로그인 화면으로 전환한다.
