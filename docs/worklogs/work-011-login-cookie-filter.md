# WORK-011: 액세스 쿠키 인증 연결

상태: 검증 대기 · 작성일: 2026-10-16 · 담당: 회원·인증

## 목표와 범위

- 보호 API가 액세스 JWT 쿠키에서 로그인 회원과 역할을 확인한다.
- 유효하지 않은 쿠키는 익명 요청으로 처리하고 JSON 401을 반환한다.

## 구현 결과

- `JwtCookieFilter`를 Security 필터 체인에 연결했다.
- 인증 실패와 권한 거부에 표준 JSON 응답을 적용했다.
- HTTP 세션을 인증 상태 저장에 사용하지 않는다.
- 15분 액세스 토큰 만료 후 재발급은 후속 변경이다.

## 검증

- `./gradlew.bat clean compileJava compileTestJava test --tests '*AuthSessionServiceTest' --tests '*AuthControllerTest' --tests '*MemberControllerTest'`: 통과.
- `./gradlew.bat test --tests '*JwtCookieFilterTest'`: 통과.
- 테스트용 `JWT_SECRET`을 설정해 `./gradlew.bat test`: 33개 중 32개 통과. 기존 `contextLoads`는 로컬 MySQL `fridge_user` 인증 거부로 실패.
