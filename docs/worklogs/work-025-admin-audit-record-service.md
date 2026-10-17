# WORK-025: 관리자 작업 이력 기록 서비스

상태: 검증 대기 · 작성일: 2026-10-17 · 담당: 관리자

## 목표와 완료 기준

- 회원 ID로 작업자를 찾고 동작·대상·결과를 이력 저장소에 기록한다.
- USER의 권한 거부도 기록할 수 있고, 대상 ID가 없으면 null로 저장할 수 있다.
- 작업자를 찾을 수 없으면 이력을 만들지 않는다.

## 근거와 범위

- 사용자 요청 13번과 docs/database/data-dictionary.md의 admin_audit_logs 계약.
- 성공·실패·권한 거부 발생 지점의 기록 호출은 각 기능 PR에서 연결한다.
- 변경 전후 값과 요청 본문은 서비스 인자로 받지 않는다.

## 구현 결과

- AdminAuditLogService.record를 추가했다. 저장은 호출자의 트랜잭션에 참여한다.
- 실패한 업무 트랜잭션이 롤백되면 같은 트랜잭션의 이력도 롤백된다. 이후 기능 PR에서 실패 기록은 롤백 완료 후 별도 트랜잭션으로 호출해야 한다.
- 신규 HTTP API와 오류 코드는 없다. 작업자 없음에는 기존 MEMBER_NOT_FOUND를 사용한다.

## 검증

- AdminAuditLogServiceTest: 통과.
- Gradle clean compileJava compileTestJava: 통과.
- 전체 테스트 86개 중 85개 통과. contextLoads()는 로컬 MySQL fridge_user 인증 거부로 실패했다.
- 실제 MySQL INSERT와 트랜잭션 경계는 검증하지 못했다.

## 연결과 남은 작업

- 개별 관리자 쓰기 기능에서 성공·실패·권한 거부의 기록 시점을 연결한다.
- 관리자 작업 이력 조회 API는 별도 기능으로 구현한다.
