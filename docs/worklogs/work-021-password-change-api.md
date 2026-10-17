# WORK-021: 비밀번호 변경 HTTP API

상태: 검증 대기 · 작성일: 2026-10-17 · 담당: 회원

## 목표와 완료 기준

- PATCH /api/members/me/password가 로그인 회원의 현재 비밀번호를 확인해 새 비밀번호로 바꾼다.
- 새 비밀번호는 가입과 동일한 규칙으로 검증한다.
- 성공 후 현재 기기 세션은 유지하고 다른 기기 세션을 폐기한다.

## 근거와 범위

- docs/api/api-spec.md의 비밀번호 변경 계약과 2026-10-17 사용자 세션 정책 결정.
- 서비스 변경은 WORK-019, 다른 기기 세션 폐기는 WORK-020에 분리했다.

## 구현 결과

- currentPassword, newPassword 요청 DTO와 PATCH API를 추가했다.
- 인증 회원 및 현재 세션 ID를 서비스에 전달한다. 기존 인증과 CSRF 보호를 적용한다.
- 성공 응답은 ApiResponse.ok()이다. 현재 비밀번호 불일치는 401 M006, 새 비밀번호 입력 오류는 400이다.
- API 명세, 회원 도메인 정책, 테스트 전략을 확정된 정책에 맞춰 수정했다.

## 검증

- Gradle 관련 회원·인증 테스트: 통과.
- Gradle clean compileJava compileTestJava: 통과.
- 전체 테스트 75개 중 74개 통과. contextLoads()는 로컬 MySQL의 fridge_user 인증 거부로 실패했다.
- 실제 MySQL에서의 세션 폐기와 트랜잭션 동작은 검증하지 못했다.

## 연결과 남은 작업

- 프론트엔드는 요청 본문에 currentPassword와 newPassword를 보내고 CSRF 헤더를 포함한다.
- 프론트엔드는 401 M006을 현재 비밀번호 오류로 표시한다.
