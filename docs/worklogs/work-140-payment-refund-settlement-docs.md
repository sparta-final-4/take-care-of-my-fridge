# WORK-140: 결제·환불·정산 문서를 현재 코드에 맞춤

상태: 검증 대기 · 작성일: 2026-11-03 · 담당: 결제·환불

## 목표와 완료 기준
- 사용자 문제: 결제 문서가 카카오페이 하나만 설명하고, 환불 문서에 환불 완료 뒤 주문 상태 전환이 없고, 정산 설계 문서가 없어 코드와 문서가 달랐다.
- 확인 가능한 완료 조건: 결제·환불·정산 설계, API 명세, ERD·데이터 사전, 테스트 전략, 화면 명세의 해당 절이 현재 코드(origin/dev `ec159981`)의 심볼·오류 코드·정책과 맞고, 바꾼 문서의 상대 링크가 모두 열린다.

## 근거와 범위
- 요구사항·화면 ID·설계 문서: 화면 9 주문 확인, 10 환불 신청·상세, 11 매출 정산. [결제 설계](../architecture/domains/payment.md), [환불 설계](../architecture/domains/refund.md), [정산 설계](../architecture/domains/settlement.md).
- 변경할 영역: `docs/`의 문서만. 코드·설정 파일은 바꾸지 않는다.
- 다른 도메인 영향·기존 승인 범위: 주문 도메인 문서는 고치지 않았다. 주문 상태 전이 규칙은 주문 문서를 따르고, 환불 문서에는 환불 쪽에서 부르는 계약만 적었다.
- 미정 정책·가정: 정산 결정은 2026-10-29 사용자 결정을 코드와 대조해 맞는 것만 옮겼다. daily_sales DDL은 저장소에 없어 엔티티 기준으로 적었다.

## 계획
| 단계 | 변경 | 검증 |
|---|---|---|
| 1 | 결제 설계에 결제대행사 5개, 결제창 값, 채널 설정, 실제 결제대행사 기록, 결제 완료 이벤트 반영 | `PgProvider`, `PortOneClient.getCheckout`, `PortOneProperties`, `PaymentCommandService.approve` 대조 |
| 2 | 환불 설계에 환불 완료 뒤 주문 상태 전환·폴백·복구 스케줄러·잠금 순서 반영 | `RefundCommandService`, `RefundableOrderPolicy`, `RefundFacade`, `RefundReconcileScheduler`, `RefundRepository` 대조 |
| 3 | 정산 설계 새로 작성 | `domain/settlement` 전체, FE `domains/settlement`, `AdminSettlementPage` 대조 |
| 4 | API·ERD·데이터 사전·테스트 전략·화면·기획·시스템 설계 해당 절 갱신 | 컨트롤러·DTO·엔티티·테스트 이름 대조, 링크 검사 |

## AI 활용과 개발자 판단
- AI에 맡긴 작업·핵심 지시: 코드와 기존 문서를 대조해 달라진 사실만 고치고 정산 설계를 새로 쓰기. 코드 수정·커밋 금지, 로컬 설정 파일은 열지 않기.
- 제안 중 채택한 내용과 이유: 정산의 "조회 기간에서 잘린 주·월", "기간에 오늘 포함" 처리는 결정 기록에 미정으로 남아 있었으나 코드가 이미 정해 두어(기간 안의 날만 더함, 오늘은 지금까지의 매출을 붙임) 코드대로 적었다.
- 수정·보류한 제안과 이유: `payment.sql`·`refund.sql` DDL 초안은 고치지 않았다. 데이터 사전에 초안과 다른 점을 적었다.
- 개발자가 확인한 근거: 검토 전이다.

## 구현 결과
- `docs/architecture/domains/payment.md`: 결제 수단 5개와 간편결제·카드 결제창, `portone.channels` 필수, 결제 ID 40자 이유, 결제대행사 누락 `C001`, 실제 결제대행사 기록, `PaymentCompletedEvent`→장바구니, `PAYMENT_009`, 정산·프론트 연동 계약, 흐름도 갱신.
- `docs/architecture/domains/refund.md`: 환불 뒤 주문 상태 판단(`REFUNDED`·`CONFIRMED`·그대로), `completeWithoutOrderStatus` 폴백, `syncOrderStatuses` 복구, 환불 행 → 주문 행 잠금과 REPEATABLE READ 잠금 조회 이유, 주문 상태 전이 표, 프론트 변화(#551).
- `docs/architecture/domains/settlement.md`: 새 문서. 집계 소스·기준 시각·음수·스케줄러·빈 날 채우기·주·월 묶기·기간 규칙·건별 조회·화면.
- `docs/api/api-spec.md`: 결제 준비 요청 `pgProvider`와 응답 필드, 결제 확정 응답 `pgProvider`, 환불 뒤 주문 상태, 정산 API 절 추가.
- `docs/database/erd.md`, `docs/database/data-dictionary.md`: `payments.pg_provider`, `idx_payment_histories_created_at`, 주문 전액 환불 전이 설명, `refunded_at`의 정산 기준 표현 정정, daily_sales 추가.
- `docs/testing/test-strategy.md`: PAY-15, REFUND-13~14, SETTLE-01~08.
- `docs/requirements/screens.md`: 9 주문 확인의 결제 수단·결제하기, 11 매출 정산 요약과 상세 절.
- `docs/requirements/project-plan.md`: Q-15 정산 부분을 결정으로 표시.
- `docs/architecture/system-design.md`: 책임과 연결에 정산 한 줄.

## 검증
| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| 바꾼 문서의 상대 링크·앵커 검사 | 로컬 스크립트 | 통과 | 새로 넣은 링크는 모두 열림. 기존 `search.sql`, `../../frontend/AGENTS.md`·`../../backend/AGENTS.md` 링크는 이번 변경 전부터 깨져 있음 |
| 애플리케이션 테스트 | | 미실행 | 문서만 바꿈 |

## 연결과 남은 작업
- 실제 커밋·PR: 아직 없음. 근거 코드는 #528·#536(결제대행사 선택), #545·#551(환불 뒤 주문 상태), #367·#385·#397·#415·#424·#438·#449(정산).
- 테스트·화면 증거: 문서에 적은 대응 테스트 이름은 저장소의 테스트 `@DisplayName`과 대조했다. 실행하지 않았다.
- 관련 ADR·문제 기록: 없음.
- 미검증·결정 대기 항목: daily_sales DDL(`net_amount` 식·타입)과 `payments.pg_provider`의 실제 DB 타입은 저장소에서 확인하지 못했다.
