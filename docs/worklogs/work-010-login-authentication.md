# WORK-010: 로그인 세션 유효성 검사

상태: 검증 대기 · 작성일: 2026-10-16 · 담당: 회원·인증

## 목표와 범위

- 액세스 JWT의 회원·세션 식별자를 서버 세션과 대조한다.
- 30분 미사용, 7일 절대 만료, 폐기·정지 회원을 거부하고 유효 요청의 마지막 사용 시각을 갱신한다.

## 구현 결과

- `AuthSessionService`가 세션 소유자·상태·만료를 확인하고 마지막 사용 시각을 갱신한다.
- 쿠키 필터와 보안 설정은 후속 변경에서 연결한다.

## 검증

- `./gradlew.bat clean compileJava compileTestJava test --tests '*AuthSessionServiceTest'`: 통과.
- 테스트용 `JWT_SECRET`을 설정해 `./gradlew.bat test`: 33개 중 32개 통과. 기존 `contextLoads`는 로컬 MySQL `fridge_user` 인증 거부로 실패.
