# 문서 전체 점검 결과 정리

상태: 완료 · 작성일: 2026-11-11 · 담당: 이현정

브랜치 `docs/member-api-cleanup`에서 회원 API 정리 커밋 4개에 이어 한 작업이다. 커밋은 로컬에만 있고 push·PR은 하지 않았다.
앞선 기록: [회원 API 문서 정리](work-member-api-cleanup.md).

## 목표와 완료 기준
- 사용자 문제: docs 전체를 코드와 대조한 결과 깨진 링크 13개, DB 문서 3개와 시스템 설계의 큰 차이, README·CLAUDE.md의 틀린 사실, 옛 설정 키·화면 경로·결정 상태, 낡은 머리말이 있었다.
- 완료 조건
  - 사실만 고치고 규칙 문장은 바꾸지 않는다. 규칙과 코드가 다른 점은 아래 "팀에 물을 것"에만 적는다.
  - DB 문서는 실제 DB(dev) 기준. dev DB는 `information_schema`를 SELECT로만 읽고, bootRun은 하지 않는다(ddl-auto가 dev 테이블을 바꿀 수 있어서).
  - 테스트 코드는 바꾸지 않는다.
  - 저장소 전체 md 링크·앵커 깨짐 0.

## 커밋
| 커밋 | 내용 | 다른 담당 문서 |
|---|---|---|
| 1c023a61 | #660 이후 깨진 링크 13개 | AGENTS.md(강준모), cart·order·sale-event·notification(장준혁), payment-card·settlement·work-141(최용범) |
| 3a60264e | DB 문서를 엔티티(dev DB) 기준으로 | data-dictionary.md(최용범), erd.md(강준모) |
| 43dc8fa5 | 시스템 설계를 지금 구성으로 | system-design.md(최용범) |
| f1f66a1d | README·CLAUDE.md의 틀린 사실 | 없음 (AGENTS.md 앵커 한 줄 포함) |
| e699043e | 설정 키·화면 경로·결정 상태 | screens.md(장준혁), project-plan.md(최용범) |
| af4f0a47 | 머리말 상태·담당 표기 | test-strategy·db-test-guide(장준혁), initial-admin·documentation-standard(안상윤), 상품·레시피·검색 문서(강준모), 결제·환불 문서(최용범) |
| 7ab25f4f | 할인 후보 협의안을 docs/archive로 | 협의안(안상윤) |
| (이 기록) | 작업 기록 | |

## 깃 아이디와 실명
커밋 이메일과 README 팀원 표로 확인했다.

| 실명 | 깃 이름 | GitHub |
|---|---|---|
| 이현정 | cookiez, zcookiez | zcookiez |
| 장준혁 | JunHyeok Jang, trexs1004, trex1004 | trex1004 |
| 최용범 | BBUMM, choiyongbum | Yong-B |
| 강준모 | momo | prjkmo112 |
| 안상윤 | OdinAhn, Ahnsangyun | OdinAhn |

## 담당 표기 근거
- 상품·레시피·통합검색 문서 6개 → 강준모
  - 도메인 코드 커밋: product 25개 중 12, recipe 24개 중 17, search 40개 중 33이 강준모.
  - 문서 커밋: 6개 문서 모두 강준모가 썼고, product-api.md만 이현정의 링크 수정 1개가 섞임.
  - README 팀원 표에 강준모가 통합 검색 담당으로 있다.
- 결제·환불·정산·등록 카드 문서 7개 → 최용범
  - 도메인 코드 커밋: payment 27개 중 25, refund 13개 중 12, settlement 7개 중 7이 최용범.
  - 문서 커밋: 대부분 최용범. 섞인 커밋은 장준혁 1개(payment.md·refund.md)와 이현정의 링크·README 정리(#660)뿐이다.
- 회원·판매 예측·할인 후보 문서 → 이현정 (안상윤에게서 넘겨받음). 할인 후보 협의안은 작성자 안상윤.

## DB 문서 대조 (dev DB 기준)
- dev `fridge_db`에 테이블 43개. schema.sql·data-dictionary.md·erd.md의 칸 이름을 모두 dev와 같게 맞췄다(공통 칸 `id`·`created_at`·`updated_at`은 사전·ERD가 생략하는 규칙이라 제외).
- `refresh_sessions.device_info`: dev에 `VARCHAR(255) NULL`로 있다. 처음 만든 마이그레이션(`history/member-auth-admin.sql:42`)에서 생겼고 엔티티는 한 번도 매핑하지 않았다. 문서에 남기고 "엔티티 매핑 없음, 항상 NULL"로 표시했다.
- `billing_keys.billing_key`: dev에 남은 암호화 전 평문 칸(`VARCHAR(200) NULL`). 엔티티 매핑 없음으로 추가했다.
- schema.sql은 테스트·CI·docker-compose 어디서도 읽지 않는다(`grep` 결과 코드 주석 한 곳뿐). 그래서 `./gradlew test` 대상이 아니다.

### 칸 타입이 다른 것 (고치지 않음, 정할 것)
진짜 차이 4개:

| 칸 | dev | 문서(schema.sql·사전) |
|---|---|---|
| admin_audit_logs.action | `DEAL_ANALYSIS` 포함 7개 | `DEAL_ANALYSIS` 없음 |
| orders.status | `REFUNDED` 포함 5개 | `REFUNDED` 없음 |
| deal_candidates.product_name | `varchar(100)` | `VARCHAR(200)` |
| sales_forecasts.product_name | `varchar(100)` | `VARCHAR(200)` |

값 순서만 다른 ENUM 13개(같은 값, dev는 엔티티가 만들어 알파벳순): admin_audit_logs.result, member_tastes.taste, members.role, payment_histories.event_type·changed_by, payments.pg_provider·status·fail_reason, recipe_tastes.taste, refunds.reason_type·status, search_zero_result_histories.target, webhook_events.process_status.

## 실제 기능 빠짐 (팀 공유)
- 상품 작업 이력: `PRODUCT_CREATE`·`PRODUCT_DELETE`·`PRICE_UPDATE` 값은 있지만 상품 쪽에서 기록하는 코드가 없다. [회원 설계 남은 결정](../architecture/domains/member.md#남은-결정)에 있다.

## 팀에 물을 것 (규칙과 지금 코드가 다른 점)
규칙 문장은 바꾸지 않았다. 규칙을 넓힐지 코드를 고칠지 정해야 한다.

- **테스트 검증은 AssertJ만**(be/CLAUDE.md 6장): JUnit `Assertions`를 쓰는 테스트 파일 22개. auth 9, admin 5, global 4, recipe 2, member 1, ingredient 1.
- **@DisplayName**: 빠진 테스트 메서드가 deal·forecast·admin·auth에 몰려 있다. 규칙 정리(11-09)보다 먼저 쓴 테스트다.
- **테스트 메서드 이름 `{동사}{결과}_when{조건}`**: `_throw`·`_return`·`_success`·`_keep`·`_fail` 같은 접미사가 많다. `_when` 외 접미사를 허용할지.
- **서비스 클래스 `@Transactional(readOnly = true)`**(4.2): 리포지토리를 쓰는데 클래스에 없는 서비스 15개. ForecastActualService, ForecastPredictionService, ForecastPredictionSaveService, ForecastBatchRunRecorder, DealAnalysisExecutionService, DealReviewService, DealEventService, PaymentCommandService, LoginService, LogoutService, RefreshService, AuthSessionService, MemberPromotionService, IngredientMatcher, SearchZeroResultService. 대부분 쓰기 위주라 메서드에만 붙였다. "쓰기 위주 서비스는 메서드 단위" 예외를 둘지.
- **커밋 접두사**(git.md): 표에 없는 `design`·`ci`를 쓴다. dev 최근 200개 중 48개가 접두사 없음(squash 제목이 PR 제목이라 "Update README.md", "Refactor/chat config" 등). PR 제목도 같은 형식으로 쓰자고 적을지. 원격에 규칙 밖 브랜치 `Yong-B-patch-1`.
- **DB 규칙**(conventions/database.md): 테이블 이름은 복수형인데 `fridge`. "모든 테이블에 created_at·updated_at"인데 ingredient_products, mealkit_products, recipe_steps에는 없다.

## 검증
| 명령·시나리오 | 결과 | 근거 |
|---|---|---|
| 저장소 전체 md 링크·앵커 검사 (스크립트) | 깨짐 0 | 마지막 실행 579개 확인 |
| dev DB 칸 대조 (information_schema SELECT) | 칸 이름 차이 0 | 칸 타입 차이는 위 목록 |
| 애플리케이션 테스트 | 미실행 | 문서만 바꿈. schema.sql을 읽는 테스트 없음 |

## 연결과 남은 작업
- 칸 타입 차이 4개를 문서에 반영할지(위 표).
- "팀에 물을 것" 6가지.
- screens.md A-06의 "보류 버튼 없음"은 화면을 확인하지 않아 그대로 두었다(검토 상태 `HELD`는 서버에 있음).
