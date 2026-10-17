# WORK-018: 맛 선호 변경 HTTP API

상태: 검증 대기 · 작성일: 2026-10-17 · 담당: 회원

## 목표와 완료 기준

- PUT /api/members/me/tastes가 인증 회원의 맛 선호 목록 전체를 교체한다.
- tastes는 필수 목록이다. 빈 목록과 중복 값은 허용하고, null 항목과 정의되지 않은 맛은 400이다.

## 근거와 범위

- docs/api/api-spec.md의 맛 선호 변경 계약과 2026-10-17 사용자 중복 허용 결정.
- 기존의 미추적 상세 API 문서에는 중복 값 400이 남아 있으므로, 사용자 결정에 맞춰 추적 중인 API 명세를 갱신한다.
- 인증·CSRF 보호는 기존 보안 설정을 사용한다.

## 구현 결과

- 요청 DTO와 컨트롤러가 인증 주체의 회원 ID와 맛 목록을 서비스에 전달한다.
- 읽을 수 없는 JSON 요청 본문을 공통 400 오류로 처리해 정의되지 않은 enum 값의 500 응답을 바로잡는다.
- 성공 응답은 기존 ApiResponse.ok() 형식이다. 신규 오류 코드는 없다.

## 검증

- Gradle MemberServiceTest 및 MemberControllerTest: 통과.
- Gradle clean compileJava compileTestJava: 통과.
- 전체 테스트 67개 중 66개 통과. contextLoads()는 로컬 MySQL fridge_user 인증 거부로 실패했다.

## 연결과 남은 작업

- 맛 선호 저장 작업 다음 PR로 올린다.
- 프론트엔드는 요청에 CSRF 헤더를 포함하고, 변경 후 내 정보 응답을 다시 조회한다.
