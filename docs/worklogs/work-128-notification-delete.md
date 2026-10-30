# WORK-128: 알림 지우기

상태: 검증 완료 · 작성일: 2026-10-30 · 담당: 준혁

## 목표와 완료 기준

- 종 아이콘 알림마다 X로 알림 하나를 지울 수 있다.
- "모두 지우기"로 내 알림을 한 번에 지울 수 있다.
- 지운 알림은 목록과 배지에서 빠지고, 같은 알림 요청이 다시 와도 되살아나지 않는다.
- 다른 회원의 알림 ID로 지우려 해도 그 알림은 바뀌지 않는다.

## 근거와 범위

- 선행 작업: WORK-123 내 알림 목록·안 읽은 수·전부 읽음 API, WORK-125 헤더 종 아이콘 알림.
- 알림을 눌러 상품 화면으로 가는 것으로는 지우지 않는다. 지우기는 X와 "모두 지우기"로만 한다.
- "모두 지우기"는 확인 창 없이 내 알림 전부를 지운다. 목록을 받은 뒤 도착한 알림도 같이 지워진다.

## 구현 결과

<details><summary>백엔드</summary>

- 알림에 `deleted_at`을 더했다. 지우면 행을 남기고 지운 시각만 채운다.
  - 행을 지우면 (회원, 중복 방지 키) 유니크 제약이 비어, 같은 Kafka 메시지가 다시 올 때 지운 알림이 새로 저장된다. 행을 남겨 제약 위반으로 건너뛰게 했다.
- 목록 조회와 안 읽은 알림 수에서 지운 알림을 뺀다.
- `DELETE /api/notifications/{notificationId}`
  - 내 알림 중 지우지 않은 것을 읽어 엔티티의 `delete()`로 지운다.
  - 없는 알림, 다른 회원의 알림, 이미 지운 알림이면 아무것도 바꾸지 않고 200이다. 읽음 API와 같은 방식이라 X를 두 번 눌러도 오류가 없다.
- `DELETE /api/notifications`
  - 내 지우지 않은 알림 전부를 일괄 UPDATE 하나로 지운다.
- 두 API 모두 성공 응답은 공통 성공 응답(데이터 없음)이다. 화면은 지운 뒤 안 읽은 수를 다시 불러온다.
- 파일: `Notification`, `NotificationRepository`, `NotificationService`, `NotificationController`.

</details>

<details><summary>프론트</summary>

- 알림마다 오른쪽 끝에 X를 둔다. 알림 링크 밖에 있어 눌러도 이동하지 않는다.
  - 성공하면 받아 둔 목록에서 그 알림만 빼고 배지를 다시 불러온다. "더 보기"로 펼친 목록이 유지된다.
  - 보이던 마지막 알림을 지우면 목록을 다시 받는다. 빼기만 하면 다음 알림이 있어도 "알림이 없습니다"가 보이기 때문이다.
- 팝오버 제목 오른쪽에 "모두 지우기"를 둔다. 받은 알림이 있을 때만 보이고, 지운 뒤 목록과 배지를 다시 불러온다.
- 함께 고친 목록 모양
  - 문구를 첫 ` · `에서 나눠 두 줄로 보여 준다. 번개 드롭 예고 알림은 시작 시각부터 둘째 줄이다.
  - 띄어쓰기 자리에서만 줄을 바꿔 "20개 한정"이 갈라지지 않게 했다.
  - 읽은 알림에도 회색 점을 둔다. 안 읽은 알림은 파란 점 그대로다.
  - md 이상 팝오버 너비를 360px에서 320px로 줄였다.
  - md 미만은 목록 최대 높이를 화면의 40%로 낮췄다. 알림이 여러 개면 팝오버가 화면을 거의 다 덮었다. md 이상은 60% 그대로다.
- 생성 클라이언트에 두 API를 추가했다(`deleteNotification`, `deleteAll`).
- 파일: `domains/notification/api.ts`, `components/NotificationList.tsx`, `NotificationList.module.scss`, 새 `components/NotificationTitle.tsx`, `NotificationBell.tsx`, `styles/_variables.scss`.

</details>

- DB: `notifications.deleted_at DATETIME(6) NULL` 추가. 유니크 제약은 그대로다.
- 문서: API 명세, 알림 설계, 데이터 사전, ERD, `notification.sql`, 테스트 전략(NOTI-01·05 보강, NOTI-06 추가), 화면 명세의 헤더 종 아이콘.

## 검증

| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `NotificationServiceTransactionTest`, `NotificationRequestedConsumerTest`, `NotificationControllerTest` | 로컬(맥), Testcontainers MySQL·Kafka | 통과 | 지운 알림의 `deleted_at`을 DB에서 다시 읽어 확인했다. 다른 회원 알림 ID로 지우면 그 알림의 `deleted_at`은 비어 있다. 모두 지우기는 내 알림만 바꾼다. 지운 알림과 같은 키가 다시 와도 행이 늘지 않고 지운 상태로 남는다 |
| `pnpm lint`, `typecheck`, prettier | 로컬(맥) | 통과 | 오류 없음 |
| X로 지우기, 알림 눌러 이동 | 로컬 프론트, 로컬 백엔드, docker MySQL·Kafka | 통과 | X를 누른 알림만 사라지고 이동하지 않았다. 알림을 누르면 상품 상세로 가고 알림은 남았다 |
| 모두 지우기 | 같은 환경 | 통과 | "알림이 없습니다"가 되고 버튼이 사라졌다 |
| 좁은 화면, 넓은 화면 | 같은 환경 | 통과 | 좁은 화면에서 알림이 여러 개여도 팝오버 아래 화면이 보이고 목록 안에서 스크롤된다 |

## 다음 작업

알림 실시간 전달(SSE).
