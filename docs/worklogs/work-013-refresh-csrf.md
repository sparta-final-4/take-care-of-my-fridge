# WORK-013: 토큰 갱신용 CSRF 토큰 제공

상태: 검증 대기 · 작성일: 2026-10-17 · 담당: 회원·인증

## 목표와 범위

- 쿠키 기반 인증의 보호 대상 쓰기 요청에 사용할 CSRF 토큰을 프론트에 제공한다.
- 로그인과 회원가입 외 POST의 CSRF 검사를 유지한다.

## 구현 결과

- Spring Security SPA CSRF 설정을 적용하고 공개 `GET /api/auth/csrf`를 추가했다.
- 프론트는 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더에 보낸다.

## 검증

- 기본 Gradle 캐시 잠금 파일 쓰기와 워커 연결이 제한돼 표준 Gradle 테스트 작업은 미실행이다.
- Gradle이 계산한 의존성 경로로 `javac` 소스·테스트 컴파일 성공. JUnit Launcher로 `CsrfControllerTest` 1개 통과, 전체 DB 비의존 테스트 38개 통과.
- Spring Security 7.1.1의 `CsrfConfigurer.spa()` 메서드는 로컬 라이브러리에서도 확인했다.
