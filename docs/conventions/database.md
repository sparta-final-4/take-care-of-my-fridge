# 데이터베이스 설계 규칙

상태: 공유 규칙 정리·일부 결정 대기 · 근거: S2 도메인 생성 규칙 이미지

## 공유된 기준

| 대상 | 기준 |
|---|---|
| 테이블 | 소문자 snake_case, 복수 명사. 예: refunds |
| 컬럼 | 소문자 snake_case. 예: cancel_reason |
| PK | id, BIGINT, AUTO_INCREMENT |
| FK 컬럼 | 참조 테이블 단수명_id. 예: payment_id |
| FK 타입 | 참조 PK와 동일 |
| FK 이름 | fk_<현재 테이블>_<참조 테이블 단수명> |
| 관계 제약 | 물리 FOREIGN KEY 명시 |
| 숫자·금액 | INT 또는 필요 시 BIGINT. 금액의 NOT NULL DEFAULT 0 방침 |
| 상태 | DB ENUM 사용 방침 |
| 감사 컬럼 | created_at, updated_at 필수, DATETIME(6) |
| 설명 | 테이블·모든 컬럼에 한국어 COMMENT |

이는 MySQL 계열 문법에 기반한 원본 규칙입니다. DB 제품·버전 확정 전 DDL을 실행하지 않습니다.

## 결정이 필요한 적용 범위

- 원본 FK의 일괄 NOT NULL은 선택 관계와 충돌할 수 있습니다. 관계 필수 여부를 먼저 정합니다.
- 금액 기본값 0과 상품 가격 1원 이상은 별개입니다. 업무상 필수 가격의 누락을 0으로 처리할지 확정합니다.
- DB ENUM과 Java enum 매핑·상태 추가 시 마이그레이션 방식을 정합니다.
- 원본은 DB의 DEFAULT CURRENT_TIMESTAMP(6), ON UPDATE CURRENT_TIMESTAMP(6)을 지정합니다. JPA Auditing과의 책임 분담은 Q-06입니다.
- 판매 중지/삭제 등의 저장값과 표시 문구를 확정합니다. 보관 방법은 상온·냉장·냉동으로 결정됐습니다(Q-14).
- 다른 도메인 테이블을 가리키는 ID(예: product_id)에 물리 FK를 걸지 여부는 같은 DB를 쓰는지에 따라 정합니다. 현재 개인 담당 초안은 FK 없이 ID만 저장합니다([데이터 사전](../database/data-dictionary.md)).
- 금액은 원 단위 정수 기준으로 검토하되 최대 범위·환불 계산·반올림 정책을 정의합니다.
- FK 삭제·갱신 동작, 유니크 제약, 인덱스, 논리 삭제 후 중복 허용 정책은 테이블별로 정합니다.

## 산출물

관계는 [ERD](../database/erd.md), 컬럼·제약은 [데이터 사전](../database/data-dictionary.md)에 기록합니다. 마이그레이션 도구와 환경별 실행 방식은 미정입니다.
