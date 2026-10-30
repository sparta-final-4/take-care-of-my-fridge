# WORK-124: 번개 드롭 등록·시작 알림 요청 발행

상태: 검증 대기 · 작성일: 2026-10-30 · 담당: 준혁

## 목표와 완료 기준

- 번개 드롭을 등록할 때와 시작할 때, 정지되지 않은 일반 회원마다 알림 요청을 `notification-requested` 토픽으로 보낸다.
- 특가는 알리지 않는다.
- 행사 저장이 커밋된 뒤에만 보내고, 보내기에 실패해도 행사는 되돌리지 않는다.

## 근거와 범위

- 시점은 등록(예고)과 시작 두 번이다.
  - 인앱 알림은 사이트를 연 사람만 바로 보므로, 시작 5분 전 알림은 시작 알림과 같은 사람에게 같은 행동을 요구한다.
  - 등록 직후 시작하는 번개 드롭도 예고를 생략하지 않는다.
- 받는 사람은 역할이 `USER`이고 상태가 `ACTIVE`인 회원 전체다. 관리자와 정지 회원은 받지 않는다.
- 메시지는 회원 한 명당 하나이고, 칸이 알림 테이블과 같다(회원, 종류, 문구, 이동 경로, 중복 방지 키).
  - 이 토픽에 처음 보내는 곳이다. 받는 쪽은 어느 도메인이 보냈는지 몰라도 그대로 저장하면 된다.
- 받는 사람과 문구는 커밋 뒤에 정한다. 시작 알림은 시작할 때의 회원 목록으로 보낸다.

## 구현 결과

- `FlashDropNotificationEvent`: 서버 안에서만 쓰는 이벤트다. 시점(등록·시작)과 행사 ID 목록만 싣는다.
- `SaleEventCreateService.createFlashDrop`: 저장한 번개 드롭 ID로 등록 이벤트를 낸다. 특가 등록은 이벤트를 내지 않는다.
- `SaleEventStatusService.openAndCloseDue`: 열 번개 드롭 ID를 먼저 읽고 연 뒤 시작 이벤트를 낸다.
  - 일괄 UPDATE는 어떤 행사를 열었는지 돌려주지 않아서 먼저 읽는다.
  - ID를 읽는 `SaleEventRepository.findIdsDueToOpen`은 `openDue`와 조건이 같고 종류만 더 본다.
- `FlashDropNotificationListener`: 커밋 뒤(`AFTER_COMMIT`) 메시지를 만들어 회원 ID를 키로 보낸다. 실패하면 시점과 행사 ID, 중복 방지 키를 로그로 남긴다.
- `FlashDropNotificationReader`: 행사 상품, 상품 이름, 회원 ID를 읽어 메시지를 만든다.
  - 행사 트랜잭션이 끝난 뒤 불리므로 새 읽기 트랜잭션(`REQUIRES_NEW`)에서 읽는다.
  - 회원은 `MemberRepository.findIdsByRoleAndStatus`로 ID만 읽는다.
- `NotificationRequestedMessage`: 토픽에 싣는 메시지. 받는 쪽도 같은 타입을 쓰도록 알림 도메인에 두었다.
- API·DB 변경 없음.

<details><summary>메시지 내용</summary>

| 칸 | 등록 | 시작 |
|---|---|---|
| 종류 | `FLASH_DROP` | `FLASH_DROP` |
| 문구 | `두부 50% 번개 드롭 · 10월 31일 18:00 시작 · 10개 한정` | `두부 50% 번개 드롭 시작 · 10개 한정` |
| 이동 경로 | `/products/{상품 ID}` | `/products/{상품 ID}` |
| 중복 방지 키 | `FLASH_DROP_CREATED:{행사 ID}:{상품 ID}` | `FLASH_DROP_STARTED:{행사 ID}:{상품 ID}` |

서버 두 대가 같은 번개 드롭을 동시에 열어 시작 알림이 두 번 나가도, 받는 쪽의 회원·중복 방지 키 유니크 제약으로 알림은 하나만 남는다.

</details>

## 검증

| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `sh ./gradlew compileJava compileTestJava` | 로컬(맥), 최신 dev 병합 뒤 | 통과 | 오류 없음 |
| `sh ./gradlew test --tests '*FlashDropNotificationTransactionTest'` | 로컬(맥), MySQL 컨테이너 | 통과 | 아래 조건 모두. 낸 이벤트를 기록하고, 커밋된 행사·회원을 다시 읽어 만든 메시지를 확인 |
| `sh ./gradlew test --tests '*SaleEventCreateServiceTransactionTest' --tests '*SaleEventLifecycleTransactionTest' --tests '*SaleEventSchedulerTest'` | 로컬(맥), MySQL 컨테이너 | 통과 | 기존 등록·상태 정리 동작이 그대로임 |
| Kafka에 실제로 들어가는지 | 로컬 서버, 로컬 Kafka | 미실행 | |

<details><summary>테스트 조건</summary>

- 번개 드롭을 등록하면 등록 이벤트를 내고, `ACTIVE`인 `USER` 회원마다 예고 메시지가 만들어진다. 관리자와 정지 회원은 빠진다.
- 특가를 등록하면 이벤트를 내지 않는다.
- 상태 정리는 시작 시각이 된 번개 드롭만 시작 이벤트에 넣는다. 특가와 아직 시작 전인 번개 드롭은 빠진다.

</details>

## 연결과 남은 작업

- 알아 둘 것: Kafka에 닿지 않으면 전송이 기다리는 동안(프로듀서 기본값 최대 60초) 번개 드롭 등록 응답이 늦어진다. 행사는 이미 저장돼 있다. 같은 공용 프로듀서를 쓰는 다른 발행도 마찬가지다.
- 다음 작업: 알림 요청 받아 저장.
