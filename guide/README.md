# 프로젝트 문서 작성 가이드

이 폴더는 프로젝트의 각 Markdown 문서가 **무슨 역할을 하고, 무엇을 적어야 하는지** 설명합니다. 현재 저장소에 이미 적힌 내용과 앞으로 채울 내용도 함께 안내합니다.

## 먼저 볼 문서

[문서별 역할·작성 방법·현재 작성 현황](document-guide.md)을 읽으세요. README의 문서 안내와 같은 순서로 다음 다섯 가지를 설명합니다.

| 구분 | 설명 |
|---|---|
| **역할** | 이 문서가 답해야 하는 질문. |
| **작성할 내용** | 문서에 넣어야 할 구체적인 항목. |
| **현재 내용** | 실제 파일에서 확인한 본문과 작성 수준. |
| **보완할 내용** | 미정이거나 아직 작성되지 않은 부분. |
| **작성 시점** | 언제 만들고 무엇이 바뀌면 갱신하는지. |

예를 들어 공통 작업 지침은 작업 전 참조·변경 범위·검증·보고 절차를, 시스템 설계는 구성 요소·기술 역할·연결·배포 구조를 적습니다. 실제 문서의 해당 절에서 이미 있는 내용도 확인할 수 있습니다.

---

## 참고용 예시

작성 형태를 추가로 보고 싶을 때 [상품 삭제 기능의 문서 연결](walkthrough.md)을 참고하세요. 아래 예시만 학습용 가상 사례이며, 위의 문서별 현황 가이드는 실제 문서를 대조해 작성했습니다.

> 예시의 API 경로·DB 정책·기술 선택은 실제 팀 결정이 아닙니다. 실제 코드·테스트 성과도 아닙니다.

### 예시에 적용한 가정

| 항목 | 예시에서만 정한 값 |
|---|---|
| 요구사항 ID | REQ-PRODUCT-DELETE-01~05 |
| 화면 | 기존 자료의 A-01·A-02 |
| API | DELETE /api/admin/products/{productId} |
| 삭제 정책 | 행 유지, status=DELETED, deleted_at 설정, 복구 기능 제외 |
| 재요청 | 이미 삭제된 상품이면 200, 삭제 시각 유지, 성공 감사 중복 생성 없음 |
| 동시 변경 | version 값으로 충돌 감지, 409 후 새로 조회 |
| 주문 | 주문 당시 이름·가격 스냅샷으로 과거 주문 표시 |
| 상품 조회 | 사용자 목록에서 삭제 제외, 관리자 목록에서는 조회 가능 |
| DB·시간 | MySQL 8 계열 문법 예시, UTC 저장·한국 시간 표시, JPA 감사 시간 관리 가정 |

DB 제품·시간 관리·충돌 처리·API 경로는 실제 프로젝트에서 아직 확정된 결정이 아닙니다. 기존 Q-06·Q-07 등을 해결한 것으로 취급하지 않습니다. 진행 중인 행사 상품 삭제는 별도 협의가 필요하여 이 예시는 **행사에 연결되지 않은 일반 상품**만 다룹니다.

### 예시 목록

| 용도 | 예시 |
|---|---|
| 포트폴리오 소개 | [프로젝트 README 예시](project-readme.sample.md) |
| AI 작업 지시·문서 탐색 | [작업 지침 예시](agent-instructions.sample.md) |
| 문서 규칙 | [작성 기준 예시](docs/conventions/documentation-standard.sample.md) |
| 프론트·백엔드·DB·Git 규칙 | [규칙을 적용하는 예시](docs/conventions/conventions.sample.md) |
| 기획·범위 | [기획서](docs/requirements/project-plan.sample.md) |
| 화면 동작 | [화면 명세](docs/requirements/screens.sample.md) |
| 전체 구조 | [시스템 설계](docs/architecture/system-design.sample.md) |
| 도메인 책임·정책 | [상품 도메인](docs/architecture/domains/product.sample.md) |
| 요청·응답 계약 | [API](docs/api/api-spec.sample.md) |
| 관계·컬럼 | [ERD](docs/database/erd.sample.md) · [데이터 사전](docs/database/data-dictionary.sample.md) |
| 선택 근거 | [논리 삭제 ADR](docs/decisions/ADR-001-logical-delete.sample.md) |
| 검증 계획 | [테스트 전략](docs/testing/test-strategy.sample.md) |
| 문제 조사 | [삭제 상품 노출 문제](docs/troubleshooting/deleted-product-visible.sample.md) |
| 작업·AI 활용 | [상품 삭제 작업 기록](docs/worklogs/WORK-001-product-delete.sample.md) |

### 예시 활용 방법

1. 예시에서 필요한 **구조와 설명 수준**을 가져옵니다.
2. 예시 정책·경로·타입은 팀 합의와 실제 코드에 맞춰 바꿉니다.
3. 기획·설계는 관련 실제 문서의 해당 절을 갱신합니다. 예시 파일 전체를 덮어쓰지 않습니다.
4. ADR·작업 기록·문제 기록은 실제 사건이 있을 때 새 파일로 만듭니다.
5. 실제 확인한 결과와 링크만 기록하고 미실행 항목은 유지합니다.

이 폴더의 문서는 프로젝트 구현 지시보다 우선하지 않습니다. 실제 기준은 [루트 지침](../AGENTS.md)과 [실제 문서 목록](../README.md)입니다.
