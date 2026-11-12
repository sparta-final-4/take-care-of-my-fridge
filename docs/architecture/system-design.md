# 시스템 설계

상태: 구현 완료 · 전체 구성도: [README 시스템 구성도](../../README.md#4-시스템-구성도)

프론트엔드(React SPA) 하나와 백엔드(Spring Boot API) 하나로 이루어진 모놀리식 구조입니다. 백엔드 안은 도메인별 패키지(`domain/{도메인}`)로 나눕니다. 서버·저장소·외부 연동의 전체 그림은 [README 시스템 구성도](../../README.md#4-시스템-구성도)를 기준으로 합니다.

## 기술 역할

| 기술 | 역할 | 쓰는 곳 |
|---|---|---|
| TanStack Query | 서버 데이터 조회·무효화 | 프론트 전 도메인 |
| Zustand | 공용 클라이언트 상태 | 프론트 인증·상담 창·냉장고 선택 |
| JPA·QueryDSL | 저장·조회·동적 검색. 스키마는 엔티티가 만든다(`ddl-auto`) | 백엔드 전 도메인 |
| MySQL 8.4 | 주 저장소 | 전 도메인. 표는 [ERD](../database/erd.md)·[데이터 사전](../database/data-dictionary.md) |
| Redis 7 | 캐시(인기 레시피·레시피 분류 등), 짧은 잠금(`SETNX`: 상담 답 하나씩), 하루 횟수·문맥 보관, SSE 다중 서버 전달(Pub/Sub), 인기 검색어·클릭 거르기, ShedLock | 상담·알림·검색·요리 기록·주문 번호·결제 비밀번호 재설정 제한 |
| DB 비관적 락 | 같은 행을 동시에 바꾸는 요청 직렬화(`SELECT ... FOR UPDATE`) | 냉장고·장바구니·주문·결제·환불·할인 행사·상품·인증 세션 등 |
| Kafka 3.8 | 도메인 이벤트 전달. 토픽 `purchase-confirmed`(구매 확정 → 냉장고 입고), `notification-requested`(알림 요청 → 알림 저장·전송) | 주문 → 냉장고, 각 도메인 → 알림 |
| Elasticsearch 9.2 | 통합검색(Nori 형태소, 레시피 kNN), 상담 레시피 후보 검색 | 검색·상담. 끄면 MySQL 조회로 대신 |
| OpenAI (Spring AI) | 상담 의도 판단·답 생성, 후기 요약, 레시피 임베딩 | 상담·후기·검색 |
| 예측 모델 | 주간 판매 전망. Java 배치가 실제 판매 이력을 모아 내부 Python 작업의 PyTorch 모델에 전달. 운영 데이터 성능 평가는 아직 없음 | [판매 예측 설계](domains/sales-forecast.md) |
| PortOne | 결제창·빌링키 결제·웹훅 | [결제 설계](domains/payment.md) |
| AWS S3 | 상품 이미지(Presigned PUT), 프론트 정적 파일 | 상품·배포 |

## 책임과 연결

- 회원은 계정·권한·세션·닉네임·맛 선호의 기준입니다([회원 설계](domains/member.md)).
- 상품 도메인은 정상가·상품 설명·보관 방법을 관리하고, 할인 행사는 행사 가격과 기간을 관리합니다. 관리자 상품 화면은 상품 도메인 API를 호출합니다.
- 주문은 주문 시 상품·가격 스냅샷을 보존합니다. 현재 상품 수정이 과거 주문을 바꾸지 않습니다.
- 환불은 주문 상태·환불 누계에 따라 가능 수량과 금액을 판단합니다.
- 정산은 결제 이력을 읽어 일별 매출을 집계합니다. 결제·환불 데이터를 바꾸지 않습니다([정산 설계](domains/settlement.md)).
- 요리 완료는 요리 기록 저장과 냉장고 차감을 한 트랜잭션으로 하고, 상담 종료는 커밋 뒤 이벤트로 합니다([요리 기록 설계](domains/cooking.md)).
- 분석은 판매·환불·재고·검색 지표를 입력받고 dataCutoff와 모델 버전을 보관합니다.
- 할인 후보 검토는 실제 가격에 영향이 없습니다. 선택한 후보는 기존 행사 생성 서비스로 등록합니다([할인 후보 설계](domains/deal-analysis.md)).

## 인증·트랜잭션

- 인증은 HttpOnly 쿠키(JWT)와 서버 세션으로 서버에서 합니다. 쿠키·CSRF 계약은 [회원 API 명세](../api/member-auth-admin-api.md#인증-쿠키), 토큰·세션 동작은 [회원 설계](domains/member.md#인증-쿠키토큰세션)에 있습니다. 프론트 라우트 보호는 사용자 흐름만 맡습니다.
- 동시성은 위 표처럼 DB 비관적 락과 Redis `SETNX`로 막습니다. 분산 락 라이브러리(Redisson)는 쓰지 않습니다.
- 외부 I/O와 비동기 작업은 커밋 뒤(`@TransactionalEventListener(AFTER_COMMIT)`) 이벤트로 분리합니다. 재시도·중복 처리·유실 복구는 도메인마다 설계 문서에 적습니다.

## 환경·운영

- 운영 Java 이미지의 JVM 시간대는 `Asia/Seoul`입니다.
- 배포는 GitHub Actions로 백엔드 Docker 이미지를 AWS ECR에 올려 EC2에서 실행하고, 프론트는 S3·CloudFront에 올립니다. 비밀 값은 AWS Parameter Store에서 읽습니다. 자세한 흐름은 [README CI/CD](../../README.md#8-cicd)를 봅니다.
- 스키마 마이그레이션 도구, 로그 보관, 백업·복구는 아직 정하지 않았습니다.
