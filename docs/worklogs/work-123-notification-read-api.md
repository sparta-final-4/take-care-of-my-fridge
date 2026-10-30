# WORK-123: 내 알림 목록·안 읽은 수·전부 읽음 API

상태: 검증 대기 · 작성일: 2026-10-30 · 담당: 준혁

## 목표와 완료 기준

- 로그인한 회원이 종 아이콘에서 자기 알림을 최신순으로 5개씩 보고, "더 보기"로 그 이전 알림을 이어 볼 수 있다.
- 모든 화면 헤더의 종 아이콘 배지에 안 읽은 알림 수를 보여 줄 수 있다.
- 종 아이콘을 열면 그때 받은 알림까지 전부 읽음이 되고, 그 뒤 도착한 알림은 안 읽음으로 남는다.

## 근거와 범위

- 목록 크기
  - 종 아이콘 드롭다운에서만 보므로 한 번에 5개를 돌려준다. 크기는 서버가 정하고 요청으로 받지 않는다.
  - "더 보기"는 페이지 번호가 아니라 화면의 마지막 알림 ID(`beforeId`)로 이어 받는다. 드롭다운을 연 사이 새 알림이 와도 다음 5개가 밀리거나 겹치지 않는다.
  - 다음이 있는지는 6개를 읽어 판단한다. 전체 개수 COUNT 쿼리는 없다.
- 안 읽은 수는 별도 API로 둔다. 배지는 모든 화면에 보이지만 목록은 종을 열 때만 필요하다.
- 전부 읽음
  - 화면이 받은 가장 최신 알림 ID(`lastNotificationId`)를 보내고, 그 ID 이하의 내 안 읽은 알림만 UPDATE 한 번으로 바꾼다.
  - 조건 없이 전부 바꾸면, 목록을 받은 뒤 도착한 알림이 보이지 않은 채 읽음이 된다. 번개 드롭 알림은 전체 회원에게 한꺼번에 간다.
  - 알림마다 `markRead()`를 부르는 방식은 안 읽은 수만큼 UPDATE가 나가서 쓰지 않았다.
- 새 알림은 화면 이동·새로고침 때 안 읽은 수를 다시 불러 반영한다. 주기 조회나 SSE를 쓰지 않으므로 이 API에 추가로 필요한 것은 없다.
- 다른 회원의 알림 ID를 `beforeId`·`lastNotificationId`로 보내도 조건에 회원 ID가 함께 걸려 내 알림만 조회·변경된다. 그래서 오류로 막지 않는다.

## 구현 결과

- `GET /api/notifications?beforeId=`: 알림 목록(`notifications`)과 `hasNext`. 알림 하나는 `notificationId`, `notificationType`, `content`, `linkUrl`, `read`, `createdAt`이다.
- `GET /api/notifications/unread-count`: `unreadCount`.
- `PATCH /api/notifications/read-all`: 본문 `{ "lastNotificationId": 숫자 }`, 응답 데이터 없음.
- 세 API 모두 로그인이 필요하다. 보안 설정은 바꾸지 않았다(기본이 인증 필요).
- `NotificationController` → `NotificationService` → `NotificationRepository`. 엔티티와 오류 코드는 바꾸지 않았다.
- 저장소
  - 목록: `beforeId`가 없으면 가장 최신부터, 있으면 그보다 작은 ID부터 ID 내림차순으로 읽는 JPQL 하나.
  - 안 읽은 수: `countByMemberIdAndReadFalse`.
  - 전부 읽음: `@Modifying` 일괄 UPDATE. JPQL UPDATE라 `updatedAt`은 바뀌지 않는다.

## 검증

| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `sh ./gradlew clean compileJava compileTestJava` | 로컬(맥) | 통과 | 컴파일 성공 |
| `sh ./gradlew test --tests 'NotificationServiceTransactionTest' --tests 'NotificationControllerTest'` | 로컬(맥), MySQL 컨테이너 | 통과 | 아래 조건 모두 |
| 화면·Postman 확인 | 로컬 서버 | 미실행 | 종 아이콘 화면 작업 때 확인 |

<details><summary>테스트 조건</summary>

- 서비스(`NotificationServiceTransactionTest`, MySQL 컨테이너, 테스트 트랜잭션 없이 서비스 경계로 확인)
  - 내 알림이 6개면 최신 5개가 ID 내림차순으로 오고 `hasNext`가 true다. 다른 회원의 더 최신 알림은 섞이지 않는다.
  - 내 알림이 딱 5개면 `hasNext`가 false다.
  - 첫 페이지의 마지막 ID를 `beforeId`로 보내면 그보다 오래된 알림만 겹치지 않게 온다.
  - 안 읽은 수는 읽은 알림과 다른 회원 알림을 빼고 센다.
  - 전부 읽음 뒤 DB를 다시 읽어, `lastNotificationId` 이하의 내 알림만 읽음이고 그 뒤 도착한 내 알림과 다른 회원 알림은 안 읽음인지 확인한다.
- 컨트롤러(`NotificationControllerTest`)
  - `beforeId`가 0이거나 숫자가 아니면 400이다.
  - 전부 읽음에 `lastNotificationId`가 없거나 0이면 400이다.
  - 로그인하지 않으면 세 API 모두 401이다.

</details>

## 연결과 남은 작업

- 설계 문서: 알림 설계(`docs/architecture/domains/notification.md`)를 새로 만들고 API 명세, 데이터 사전, ERD, DDL 초안(`notification.sql`), 테스트 전략(NOTI-01~05)에 알림을 추가했다. 할인 행사 설계와 테스트 전략(SALE-14)에는 번개 드롭 알림 발행을 반영했다.
- 실제 커밋·PR: 작성 전
- 다음 작업: 생성 클라이언트, 종 아이콘 화면. 화면은 전부 읽음 뒤 배지를 0으로 고정하지 않고 안 읽은 수를 다시 불러온다.
- 측정 전: 회원별 조회는 `(member_id, dedupe_key)` 유니크 인덱스를 쓰고 ID 순서는 따로 정렬한다. 회원 한 명의 알림이 많아질 때의 비용은 TBD(측정 필요).
