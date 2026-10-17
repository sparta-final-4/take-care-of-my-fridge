# WORK-029: 관리자 승격 이력 값

상태: 검증 대기 · 작성일: 2026-10-17 · 담당: 관리자

## 목표와 완료 기준

- 작업 이력에 역할 변경 전후 값을 저장할 수 있다.
- 기존 작업 기록 호출은 그대로 작동한다.

## 근거와 범위

- 관리자 승격 시 ROLE_CHANGE와 role 변경 전후 값을 기록하는 API 명세.

## 구현 결과

- 감사 이력의 선택 JSON 컬럼에 beforeValue·afterValue를 연결했다.
- 역할 값만 저장하는 생성 경로를 추가했다. 기존 생성 경로는 유지한다.

## 검증

- AdminAuditLogTest와 AdminAuditLogServiceTest 통과.

## 연결과 남은 작업

- 다음 승격 서비스에서 USER에서 ADMIN으로 변경한 값을 기록한다.
