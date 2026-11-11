# WORK-141: 등록 카드 빌링키를 암호화해 저장

상태: 검증 대기 · 작성일: 2026-11-11 · 담당: 결제·환불

## 목표와 완료 기준
- 사용자 문제: `billing_keys.billing_key`에 PortOne 빌링키가 평문으로 저장돼, DB나 백업이 새면 그 값으로 등록 카드 결제를 요청할 수 있었다.
- 확인 가능한 완료 조건: DB에는 빌링키 암호문과 지문만 남고, 결제·PortOne 삭제 직전에만 풀며, 기존 평문 행은 배포 후 기동만으로 옮겨진다. 배포 DB(`ddl-auto: update`)에서 손으로 DDL을 돌리지 않아도 카드 등록·결제가 된다.

## 근거와 범위
- 요구사항·화면 ID·설계 문서: [등록 카드 결제 설계](../architecture/domains/payment-card.md#빌링키-암호화), [데이터 사전](../database/data-dictionary.md#billing_keys--등록-카드), [payment.sql](../database/payment.sql).
- 변경할 영역: BE `domain/payment`(엔티티·서비스·파사드), `global/config/BillingKeyCryptoConfig`, `ErrorCode`(`BILLING_006`), 결제 문서.
- 다른 도메인 영향·기존 승인 범위: 정산은 `payments.billing_key_id`가 있는지만 보고 빌링키 값은 읽지 않아 영향이 없다. 카드 행 ID는 바뀌지 않는다.
- 미정 정책·가정: 옛 `billing_key` 컬럼 삭제는 이후 별도 배포에서 한다. 웹훅 원문(`webhook_events.raw_payload`)과 PortOne 클라이언트 예외 메시지의 빌링키 가리기는 이번 범위가 아니다.

## 계획
| 단계 | 변경 | 검증 |
|---|---|---|
| 1 | `BillingKeyCrypto`: AES-256-GCM 암호문(회원 ID를 추가 인증 데이터로), HMAC-SHA256 지문, 키 ID로 키 교체 | `BillingKeyCryptoTest` |
| 2 | 엔티티를 암호문·지문 컬럼으로 바꾸고 등록 중복은 지문으로 찾기 | `BillingKeyServiceTest`, `BillingKeyRepositoryTest`, `BillingKeyServiceConcurrencyTest` |
| 3 | 결제·PortOne 삭제 직전에만 풀기, 결제 복호화 실패는 `BILLING_006` | `PaymentFacadeBillingKeyTest`, `BillingKeyFacadeTest` |
| 4 | 키 설정: 배포 서버는 Parameter Store 필수, 로컬은 키 파일 자동 생성 | `BillingKeyCryptoConfigTest` |
| 5 | 기존 평문 옮기기와 배포 DB 구조 정리(옛 컬럼 NULL 허용, 옛 UK 삭제, 지문 UK 생성) | `LegacyBillingKeyEncryptorTest` |

## AI 활용과 개발자 판단
- AI에 맡긴 작업·핵심 지시: 평문 저장을 없애되 결제는 계속 되게, 다시 고칠 필요가 없는 방식으로 구현. 리뷰 지적은 코드와 대조해 맞는 것만 반영.
- 제안 중 채택한 내용과 이유: 해시만 저장하는 방식은 결제 때 원래 빌링키를 PortOne에 보내야 해서 쓸 수 없어, 되돌릴 수 있는 암호화와 조회용 지문을 함께 두었다. PR 리뷰의 "`ddl-auto: update`가 옛 NOT NULL·UK 컬럼을 남긴다"는 지적을 받아, 손 DDL 대신 기동 시 구조를 정리하게 했다.
- 수정·보류한 제안과 이유: 마이그레이션 실패 시 기동 차단은 카드 한 건 문제로 사이트 전체가 내려가므로 보류하고 남은 건수를 ERROR 로그로 남겼다. 키 필수 조건을 prod 프로필에만 두는 것은 dev·main 서버가 모두 prod 프로필로 떠 그대로 두었다.
- 개발자가 확인한 근거: 로컬 DB 6행을 직접 풀어 PortOne 빌링키 목록과 끝 4자리·상태가 맞는지, 다른 회원 ID로는 풀리지 않는지 확인했다.

## 구현 결과
- 새 파일: `BillingKeyCrypto`, `BillingKeyCryptoException`, `EncryptedBillingKey`, `LegacyBillingKeyEncryptor`, `BillingKeyCryptoConfig`.
- 수정: `BillingKey`(컬럼 `billing_key_ciphertext`·`billing_key_hash`, UK `uk_billing_keys_billing_key_hash`), `BillingKeyRepository.findByBillingKeyHash`, `BillingKeyService.register`, `BillingKeyFacade.deleteAtPg`, `PaymentFacade.payWithBillingKey`, `ErrorCode.BILLING_KEY_UNAVAILABLE`(`BILLING_006`, 500).
- DB: `billing_keys.billing_key` 대신 `billing_key_ciphertext VARCHAR(512)`, `billing_key_hash CHAR(64)` UK. 기존 DB의 옛 컬럼은 NULL 허용으로 비운 채 남는다.
- 설정: 배포 서버는 Parameter Store `/imhungry/{dev, main}/BILLING_KEY_ENCRYPTION_KEY`, `BILLING_KEY_HMAC_KEY`가 있어야 기동한다.
- 문서: `payment.sql`, `data-dictionary.md`, `erd.md`, `payment-card.md`(빌링키 암호화 절), `payment-api.md`(`BILLING_006`).

## 검증
| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `./gradlew clean compileJava compileTestJava`, `./gradlew test` | 로컬, Docker(Testcontainers MySQL·Redis·Kafka) | 통과 | 2305건 실패 0 |
| 배포 DB 재현: 옛 컬럼 NOT NULL·UK, 새 컬럼 `''`, 지문 UK 없음에서 기동 마이그레이션 | `LegacyBillingKeyEncryptorTest` | 통과 | 구조 정리·옮기기·지문 UK 생성·새 카드 저장 |
| 로컬 DB 기존 평문 4건 옮기기, 6행 복호화와 PortOne 대조 | 로컬 MySQL 8.4, PortOne 테스트 상점 | 통과 | 6행 모두 복호화·지문 일치, 활성 카드 2장 PortOne `ISSUED` |
| dev 서버 배포 후 기동·카드 등록·결제 | dev | 미실행 | Parameter Store 키 등록 후 배포 필요 |

## 연결과 남은 작업
- 실제 커밋·PR: `05a726f9`(암호화), `39b7f460`(배포 DB 구조 정리·리뷰 반영), 브랜치 `feat/billing-key-encryption`.
- 테스트·화면 증거: 위 검증 표.
- 관련 ADR·문제 기록: 없음.
- 미검증·결정 대기 항목: dev·main Parameter Store 키 등록과 배포 확인, 옛 `billing_key` 컬럼 삭제 시점, 웹훅 원문·PortOne 클라이언트 예외의 빌링키 가리기, 남은 평문 건수 로그의 모니터링 알림 연결.
