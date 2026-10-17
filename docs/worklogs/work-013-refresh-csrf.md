# WORK-013: 토큰 갱신용 CSRF 토큰 제공

상태: 검증 대기 · 작성일: 2026-10-17 · 담당: 회원·인증

## 목표와 범위

- 쿠키 기반 인증의 보호 대상 쓰기 요청에 사용할 CSRF 토큰을 프론트에 제공한다.
- 로그인과 회원가입 외 POST의 CSRF 검사를 유지한다.

## 구현 결과

- Spring Security SPA CSRF 설정을 적용하고 공개 `GET /api/auth/csrf`를 추가했다.
- 프론트는 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더에 보낸다.

## 검증

- `./gradlew.bat clean compileJava compileTestJava` 실행 시 기본 Gradle 캐시 잠금 파일 접근이 거부됐다.
- 작업 공간 캐시로도 Gradle 데몬 연결이 실패해 테스트는 미실행이다.
- Spring Security 7.1.1의 `CsrfConfigurer.spa()` 메서드는 로컬 라이브러리에서 확인했다.
