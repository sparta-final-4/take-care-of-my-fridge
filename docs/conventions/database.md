# 데이터베이스 설계 규칙

> **스키마 기준**: 실제 테이블은 JPA 엔티티가 만듭니다(`ddl-auto`). [docs/database/schema.sql](../database/schema.sql)은 dev DB에 맞춰 옮겨 적은 DDL입니다.  
> 프로젝트의 모든 RDBMS(MySQL 8.4) 테이블 및 컬럼 설계 시 반드시 준수해야 하는 공통 도메인 규칙입니다.

---

### 1. 네이밍(Naming) 규칙

- **테이블명:** 소문자 `snake_case`를 사용하며, **복수형** 명사를 사용합니다. (예: `refunds`, `payments`)
- **컬럼명:** 소문자 `snake_case`를 사용합니다. (예: `cancel_reason`)

---

### 2. 식별자(PK - Primary Key) 규칙

- **컬럼명:** `id` 형태로 짓습니다. (예: `refunds` 테이블의 PK는 `id`)
- **데이터 타입:** `BIGINT`를 사용합니다.
- **생성 규칙:** `AUTO_INCREMENT`를 적용하여 DB에서 순차적으로 생성되게 합니다.

---

### 3. 연관관계(FK - Foreign Key) 규칙

- **컬럼명:** 참조하는 대상 테이블의 PK 이름을 그대로 가져와 `[단수형 참조테이블명]_id`로 짓습니다. (예: `payments`를 참조하므로 `payment_id`)
- **데이터 타입:** 참조하는 PK와 완벽히 동일한 타입(`BIGINT`)을 사용하고 `NOT NULL`을 적용합니다. *(선택적 연관관계나 위변조 추적용 웹훅 등 비즈니스 예외인 경우에만 한정적으로 NULL 허용)*
- **제약조건명(Constraint Name):** `fk_[현재 테이블명]_[참조하는 테이블 단수명]` 패턴을 사용합니다. (예: `fk_refunds_payment`)
- **물리적 외래키:** ERD 상의 논리적 관계뿐만 아니라 `CONSTRAINT ... FOREIGN KEY` 구문을 사용해 물리적 외래키를 명시합니다. *(단, 도메인 경계가 명확하여 결합도를 낮춰야 하는 타 도메인 참조는 ID만 저장)*

---

### 4. 데이터 타입 및 제약조건 규칙

- **숫자/금액:** `INT` (또는 필요시 `BIGINT`)를 사용하며, 금액 등은 `NOT NULL DEFAULT 0`으로 설정해 `NULL` 계산 오류를 방지합니다.
- **상태값(Enum):** RDB의 `ENUM` 타입을 사용하여 허용된 문자열 값만 들어가도록 강제합니다. (예: `ENUM('COMPLETED', 'FAILED')`)

---

### 5. 공통 컬럼 규칙 (`BaseTimeEntity`)

- **컬럼 구성:** 모든 테이블에 `created_at`(생성일시)과 `updated_at`(수정일시)을 필수로 포함합니다.
- **데이터 타입:** 마이크로초 단위까지 정확하게 기록하기 위해 `DATETIME(6)`을 사용합니다.
- **자동 생성:**
    - 생성 시: `DEFAULT CURRENT_TIMESTAMP(6)`
    - 수정 시: `ON UPDATE CURRENT_TIMESTAMP(6)` (애플리케이션 레이어뿐만 아니라 DB 레벨에서도 수정 시간을 자동 갱신)

---

### 6. 코멘트(COMMENT) 규칙

- **테이블 및 컬럼 코멘트:** 테이블 자체와 **모든 컬럼**에 반드시 한글로 `COMMENT`를 달아주어 ERD와 DDL을 보는 누구나 명확한 의미와 단위를 알 수 있게 합니다.

---

## 📌 관련 산출물 문서
- **전체 DDL 스크립트**: [docs/database/schema.sql](../database/schema.sql)
- **물리 ERD 다이어그램**: [docs/database/erd.md](../database/erd.md)
- **컬럼 상세 데이터 사전**: [docs/database/data-dictionary.md](../database/data-dictionary.md)
