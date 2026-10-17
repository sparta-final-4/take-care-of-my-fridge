# WORK-014: 토큰 갱신 HTTP API

상태: 검증 대기 · 작성일: 2026-10-17 · 담당: 회원·인증

## 목표와 범위

- 갱신 쿠키와 CSRF 헤더로 `POST /api/auth/refresh`를 호출하면 두 JWT 쿠키를 교체한다.
- 누락·만료·재사용한 갱신 토큰은 401, CSRF 누락은 403을 반환한다.

## 구현 결과

- 갱신 경로를 액세스 JWT 없이 호출할 수 있게 했다. CSRF 보호는 유지한다.
- 새 갱신 쿠키의 Max-Age는 최초 로그인 후 7일 중 남은 기간으로 설정한다.

## 검증

- Gradle 캐시 쓰기·워커 연결 제한으로 표준 Gradle 테스트 작업은 미실행이다.
- Gradle 의존성 경로로 `javac` 소스·테스트 컴파일 성공.
- 최신 회원 브랜치 위에서 JUnit Launcher로 `RefreshServiceTest` 2개, `CsrfControllerTest` 1개, `AuthControllerTest` 6개를 포함한 DB 비의존 테스트 56개 통과.
- DB 행 잠금의 실제 동시성 검증은 로컬 MySQL `fridge_user` 인증 거부로 대기한다.
