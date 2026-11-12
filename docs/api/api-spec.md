# API 명세서

## 공통 규칙

- 모든 API 경로는 `/api`로 시작합니다. 프론트 baseURL은 서버 주소(origin)만 갖고, `/api`는 생성된 API 경로에 들어 있습니다.
- 프론트는 API와 같은 출처로 부릅니다(로컬 Vite proxy, 운영 CloudFront `/api/*`). 그래서 백엔드에 CORS 설정은 없습니다.
- 인증은 HttpOnly 쿠키이고 프론트는 `withCredentials: true`로 보냅니다. 로그인 쿠키와 CSRF 계약은 [회원·인증·관리자 API의 인증 쿠키](member-auth-admin-api.md#인증-쿠키)를 따릅니다.
- 성공·실패 응답은 모두 `ApiResponse<T>`입니다. 업무 오류는 `ErrorCode`를 담은 `BusinessException`을 공통 처리기가 바꿉니다. 인증 필터·권한 거부 응답도 같은 모양입니다.
- 공통 오류 코드:

| HTTP | 코드 | 조건 |
|---|---|---|
| 400 | C001 | 요청 검증 실패, 본문·파라미터 형식 오류, 필수 파라미터 누락 |
| 401 | A003 | 로그인이 필요함(쿠키 없음·세션 만료) |
| 403 | A004 | 로그인했지만 권한이 없음(USER가 `/api/admin/**`) |
| 403 | AUTH_002 | CSRF 실패, 메서드 단위 권한 거부 |
| 404 | COMMON_003 | 없는 경로 |
| 500 | COMMON_002 | 처리하지 못한 서버 오류 |

- 날짜·시간 형식:
  - 기본은 시간대 없는 ISO-8601입니다(`2026-11-09T17:04:30`, 날짜만이면 `2026-11-09`).
  - 상담·메시지·후기 응답처럼 `@JsonFormat`을 붙인 칸은 `yyyy-MM-dd HH:mm:ss`입니다. 각 API 명세의 응답 표에 적습니다.
  - 할인 `endsAt`은 `+09:00`이 붙은 시각입니다.
- 경로 변수 인코딩, 조회 요청 취소 신호(`signal`)는 [프론트 규칙](../../take-care-of-my-fridge-fe/CLAUDE.md#81-api-호출)을 따릅니다. 요청·응답 타입은 `/v3/api-docs`로 생성한 클라이언트를 씁니다.

## 도메인별 API

| 도메인 | 명세 |
|---|---|
| 회원·인증·관리자 | [member-auth-admin-api.md](member-auth-admin-api.md) |
| 냉장고 | [fridge-api.md](fridge-api.md) |
| 요리 기록 | [cooking-api.md](cooking-api.md) |
| 후기 | [review-api.md](review-api.md) |
| AI 상담 | [chat-api.md](chat-api.md) |
| 레시피 | [recipe-api.md](recipe-api.md) |
| 상품 | [product-api.md](product-api.md) |
| 통합검색 | [search-api.md](search-api.md) |
| 장바구니 | [cart-api.md](cart-api.md) |
| 주문 | [order-api.md](order-api.md) |
| 환불 | [refund-api.md](refund-api.md) |
| 클릭 수 기반 할인 상품 | [sale-event-api.md](sale-event-api.md#클릭-수-기반-할인-상품) |
| 할인 행사 | [sale-event-api.md](sale-event-api.md) |
| 판매 예측 | [forecast-api.md](forecast-api.md) |
| 할인 후보 | [deal-analysis-api.md](deal-analysis-api.md) |
| 결제 | [payment-api.md](payment-api.md) |
| 정산 | [settlement-api.md](settlement-api.md) |
| 알림 | [notification-api.md](notification-api.md) |

## 공유된 응답 형태

아래 값은 예시이며 실제 API 응답이 아닙니다.

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "C001",
    "message": "적절하지 않은 요청 값입니다."
  }
}
```

검증 실패는 400 `C001`이고, 첫 번째 필드 오류의 메시지 하나만 `error.message`에 담깁니다. 필드 이름은 오지 않으므로 `form.setFields` 매핑에 쓸 수 없습니다.

## 페이지 응답

공유 구조: content, pageNumber, pageSize, totalElements, totalPages, isLast. 요청 page와 응답 pageNumber는 1부터 시작하며, 서비스 내부의 Spring Page 번호는 0부터 시작합니다.

## 엔드포인트 작성 템플릿

도메인 API 명세(`○○-api.md`)는 아래 모양으로 씁니다. 정책·설계·흐름·이유는 `architecture/domains/○○.md`에 둡니다.

```markdown
## <기능명>
`METHOD /경로` · 권한(공개·로그인·ADMIN)

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| <필드> (path·query면 표시) | <타입> | Y·N | <범위·기본값> |

응답: `<응답 DTO>`

| 이름 | 타입 | null | 설명 |
|---|---|---|---|
| <필드> | <타입> | Y·N | <뜻> |

| HTTP | 코드 | 조건 |
|---|---|---|
| <상태> | <ErrorCode> | <이 API만의 오류. 공통 오류는 빼기> |

주의 사항
- 3줄 이내. 호출하는 쪽이 꼭 알아야 할 것만
```
