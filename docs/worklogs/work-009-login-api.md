# WORK-009: 로그인 HTTP API

상태: 검증 대기 · 작성일: 2026-10-16 · 담당: 회원·인증

## 목표와 범위

- 익명 사용자가 이메일·비밀번호로 `POST /api/auth/login`을 호출할 수 있다.
- 성공하면 회원 정보와 HttpOnly JWT 쿠키 2개를 받는다.

## 구현 결과

- 로그인 요청 검증·컨트롤러·쿠키 설정을 추가했다.
- 로그인 경로만 익명 및 CSRF 예외로 등록했다.
- 다른 보호 API에서 액세스 쿠키를 읽는 처리는 후속 변경이다.

## 검증

- `./gradlew.bat clean compileJava compileTestJava test --tests '*AuthControllerTest'`: 통과.
- 테스트용 `JWT_SECRET`을 설정해 `./gradlew.bat test`: 27개 중 26개 통과. 기존 `contextLoads`는 로컬 MySQL `fridge_user` 인증 거부로 실패.
