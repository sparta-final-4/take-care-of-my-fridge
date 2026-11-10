# 알림 API 명세

상태: 구현 완료 · 담당: 장준혁 · 공통 규칙: [API 계약](api-spec.md#공통-규칙) · 자세한 동작: [알림 설계](../architecture/domains/notification.md)

공통 응답·오류 코드·페이지·날짜 형식은 [공통 규칙](api-spec.md#공통-규칙)을 따릅니다. 이 문서의 경로는 `/api`를 뺀 값이고, 에러 표에는 공통 오류(401 `A003`, 400 `C001`)를 빼고 이 API만의 오류를 적었습니다. 회원 번호는 로그인 정보에서만 읽고 요청에는 받지 않습니다.

## 목차

- [내 알림 목록](#내-알림-목록)
- [안 읽은 알림 수](#안-읽은-알림-수)
- [실시간 알림 스트림](#실시간-알림-스트림)
- [전부 읽음](#전부-읽음)
- [알림 지우기](#알림-지우기)
- [모두 지우기](#모두-지우기)
- [알림 응답 DTO](#알림-응답-dto)

## 내 알림 목록
`GET /notifications` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| beforeId (query) | long | N | 이 ID보다 오래된 알림부터 받음. 1 이상. 비우면 가장 최신부터 |

응답: `NotificationListResponse`

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | beforeId가 1 미만이거나 숫자가 아님 |

주의 사항
- **목록 크기**: 한 번에 5개입니다. 크기는 요청으로 받지 않습니다.
- **더 보기**: 화면의 마지막 알림 ID를 beforeId로 보냅니다. 목록을 연 사이 새 알림이 와도 다음 알림이 밀리거나 겹치지 않습니다. `hasNext`가 false면 더 받을 알림이 없습니다.
- **본인 소유 검증**: 다른 회원의 알림 ID를 보내도 내 알림만 조회되고 오류는 없습니다.

## 안 읽은 알림 수
`GET /notifications/unread-count` · 로그인

요청: 없음

응답: `UnreadNotificationCountResponse`

주의 사항
- **배지**: 전부 읽음 뒤에는 안 읽은 알림 수를 다시 불러옵니다. 화면 이동·탭 복귀·새로고침 때, 새 알림 신호가 올 때도 다시 불러옵니다.

## 실시간 알림 스트림
`GET /notifications/stream` · 로그인

요청: 없음

응답: SSE(`text/event-stream`)로 이벤트를 보냅니다. 연결을 오래 쥐는 응답이라 공통 성공 응답으로 감싸지 않습니다.

| 이벤트 | 내용 |
|---|---|
| `notification` | 내용 없는 새 알림 신호(`refresh`). 화면이 목록·안 읽은 수 API로 직접 받아감 |
| `chat` | 상담 바뀜 신호. data는 JSON `{"sessionId":15,"status":"COMPLETED"}`(status: `ACTIVE`·`COMPLETED`·`EXPIRED`). 메시지 내용은 싣지 않음 |

주의 사항
- 새 알림이 저장되면 `notification` 이벤트로 `refresh` 신호를 보냅니다. 알림 내용은 싣지 않으므로 화면이 목록·안 읽은 수 API로 직접 받아갑니다.
- 연결 유지 시간은 30분, 조용한 연결이 끊기지 않도록 30초마다 하트비트(주석)를 보내며, 한 회원이 동시에 열 수 있는 연결은 5개입니다. 한도를 넘기면 가장 오래된 연결이 닫힙니다. 값은 `app.sse.*` 설정입니다. 비로그인은 401입니다.
- 끊기면 브라우저가 다시 연결하고, 다시 연결될 때 화면이 한 번 다시 조회해 놓친 알림을 메웁니다. 그 밖에 놓친 알림은 다음 조회 때 보입니다.
- **상담 바뀜 신호(2026-11-02 추가)**: 같은 스트림으로 `chat` 이벤트도 옵니다. 여러 창(브라우저·기기·서버)에서 AI 상담을 맞추는 신호입니다. 회원 번호와 메시지 내용은 싣지 않으므로 화면이 상담·메시지 조회 API로 직접 받아갑니다. 커밋된 뒤에만 보내고, 실패해 되돌려진 변경은 보내지 않습니다. 보내는 때는 다음과 같습니다.
  - 새 상담을 만들었을 때 `ACTIVE`(상담 시작 API가 기존 진행 중 상담을 그대로 돌려줄 때는 보내지 않음)
  - 답(질문·답 한 쌍)을 저장했을 때 `ACTIVE`
  - 상담 종료 API, 요리 완료로 상담이 끝났을 때 `COMPLETED`
  - 30분 동안 대화가 없어 서버가 실제로 만료시켰을 때(다음 상담 시작·메시지 보내기 때) `EXPIRED`. 시간이 지나 화면에서만 만료로 보이는 상담은 신호가 없습니다.
  - 상담 시작 때 오래된 상담을 만료시키고 새로 만들면 `EXPIRED`, `ACTIVE` 순으로 두 번 옵니다.
  - 서버 간 전달은 알림(`notification:push`)과 따로인 Redis 채널 `chat:push`로 합니다. 놓친 신호는 다음 조회 때 메워집니다.

## 전부 읽음
`PATCH /notifications/read-all` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| lastNotificationId | long | Y | 화면이 받은 가장 최신 알림 ID. 1 이상 |

응답: 없음(`ApiResponse.ok()`)

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | lastNotificationId가 없거나 1 미만 |

주의 사항
- 종 아이콘을 열 때 부릅니다. lastNotificationId 이하의 안 읽은 알림을 한 번에 읽음으로 바꾸고, 그 뒤 도착한 알림은 안 읽음으로 남습니다.
- 받은 알림이 없으면 부르지 않습니다. 같은 요청을 두 번 보내도 결과가 같습니다.

## 알림 지우기
`DELETE /notifications/{notificationId}` · 로그인

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| notificationId (path) | long | Y | 지울 알림 ID |

응답: 없음(`ApiResponse.ok()`)

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | notificationId가 숫자가 아님 |

주의 사항
- 지운 알림은 목록과 안 읽은 알림 수에서 빠집니다.
- 없는 알림, 다른 회원의 알림, 이미 지운 알림이면 아무것도 바꾸지 않고 200입니다.

## 모두 지우기
`DELETE /notifications` · 로그인

요청: 없음

응답: 없음(`ApiResponse.ok()`)

주의 사항
- 내 알림을 전부 지웁니다. 화면이 받지 않은 알림, 목록을 받은 뒤 도착한 알림도 함께 지워집니다.

## 알림 응답 DTO

`NotificationListResponse` (내 알림 목록)

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| notifications | `NotificationResponse[]` | N | 최신순 |
| hasNext | boolean | N | 더 받을 알림이 있는지 |

`NotificationResponse`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| notificationId | long | N | 알림 ID |
| notificationType | `EXPIRATION`·`FLASH_DROP`·`COMMERCE_STOCKED` | N | 알림 종류 |
| content | string | N | 문구. 번개 드롭 시작 알림은 줄바꿈 문자로 세 부분(번개 드롭 시작 / 할인율·한정 수량 / 상품 이름)을 나눔 |
| linkUrl | string | N | 누르면 이동할 화면 경로 |
| read | boolean | N | 읽음 여부 |
| createdAt | datetime | N | 만든 시각 |

`UnreadNotificationCountResponse` (안 읽은 알림 수)

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| unreadCount | long | N | 안 읽은 알림 수 |
