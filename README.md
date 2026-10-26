# 냉장고를 부탁해 · take-care-of-my-fridge

냉장고에 남은 재료를 관리하고, 보유 재료에 맞는 레시피와 부족 재료·밀키트를 연결하는 팀 포트폴리오 프로젝트입니다.

> 상태: 기획 및 개발 지침 정리 단계 · 문서 초안 작성일: 2026-10-14  
> 이 저장소의 문서는 개발 예정 사항을 포함합니다. 실행 가능한 애플리케이션, 배포, 테스트 통과, 성능 개선은 아직 확인되지 않았습니다.

## 서비스 흐름

재료 등록 → 냉장고 관리 → 레시피 탐색·AI 상담 → 요리 기록·후기 또는 부족 재료 구매 → 주문·환불 → 관리자 운영·판매 분석.

## 개발 범위와 기여

전체 서비스와 개인 담당 범위를 구분합니다. 담당자 이름·기간·최종 역할 분담은 미정입니다.

- 개인 담당 범위: 회원·인증, 관리자 상품 화면, 판매 TOP 10 예측 및 실제 판매량 비교, 요일별 할인 후보, 관리자 작업 이력, 회원 권한 관리.
- 관리자 상품 화면(A-01~03)은 화면만 담당합니다. 상품 데이터와 API는 상품 도메인 소유입니다.
- 다른 도메인과 연동: 상품, 냉장고, 레시피·후기, 검색·추천, 재고·주문, 결제·환불·정산, 프로모션·알림.
- 할인 행사 운영 화면(A-11)의 구현 담당은 협의가 필요합니다.
- 실제 구현 기여는 작업 기록과 커밋·PR로 증명하며, 계획을 구현 실적으로 표현하지 않습니다.

## 계획된 기술

| 영역 | 공유된 규칙상의 기술 |
|---|---|
| 프론트엔드 | React, TypeScript, Vite, pnpm, Ant Design, SCSS Modules, React Router, Zustand, TanStack Query |
| 백엔드 | Java 17, Spring Boot 3.x, Gradle, JPA, QueryDSL |
| 인프라 후보 | Redis, Redisson, Kafka |
| 검증 | ESLint, Prettier, Vitest, JUnit 5, Mockito |

정확한 버전, DB 제품·버전, 배포 환경은 미정입니다. 인프라 후보는 필요한 기능과 도입 근거를 확인한 뒤 확정합니다.

## 실행 방법

현재 애플리케이션 설정 파일과 소스가 없어 실행 명령을 제공하지 않습니다. 초기 구성 후 필요한 런타임 버전, 환경 변수 이름, 의존 서비스, 실행·검증 절차를 이 절에 추가합니다. 비밀 값은 커밋하지 않습니다.

### 로컬 의존 서비스

루트의 `docker-compose.yaml`로 MySQL, Redis, Elasticsearch(nori 포함), Kibana, Kafka를 띄웁니다.

```bash
docker compose up -d
```

- Elasticsearch는 컨테이너가 처음 시작될 때 한국어 형태소 분석기(nori)를 설치합니다. 인터넷 연결이 필요하고, 첫 기동이 조금 느립니다.
- 컨테이너를 새로 만들면(`docker compose down` 후 `up` 등) 플러그인을 다시 설치하고, 인덱스도 다시 색인해야 합니다.

## 문서 안내

각 문서의 **역할·적어야 할 내용·현재 작성된 내용·앞으로 채울 내용**은 [문서별 가이드](guide/document-guide.md)에 정리했습니다. 아래 현황은 2026-10-14 문서 확인 기준이며 구현 완료 여부와는 다릅니다.

| 문서 | 역할 | 현재 작성 수준 | 작성 안내 |
|---|---|---|---|
| [공통 작업 지침](AGENTS.md) | 작업 전 참조·변경 범위·완료 절차 | 공통 절차 작성됨 | [가이드](guide/document-guide.md#공통-작업-지침) |
| [기획·범위·미결정 사항](docs/requirements/project-plan.md) | 목적·기능 범위·담당·미결정 정책 | 전체·개인 범위 초안 있음 | [가이드](guide/document-guide.md#기획범위미결정-사항) |
| [화면 명세](docs/requirements/screens.md) | 화면 진입·입력·동작·실패 흐름 | 화면 목록·동작 요약 있음 | [가이드](guide/document-guide.md#화면-명세) |
| [시스템 설계](docs/architecture/system-design.md) | 전체 구조·기술 역할·데이터 연결 | 개념도·책임 초안 있음 | [가이드](guide/document-guide.md#시스템-설계) |
| [회원 도메인 설계](docs/architecture/domains/member.md) | 회원·인증·권한의 정책·상태·연동 | 정책·상태 전이 초안 있음 | [가이드](guide/document-guide.md#회원-도메인-설계) |
| [API 계약](docs/api/api-spec.md) | API 요청·응답·권한·오류 약속 | 공통 규격·기능 목록·회원 API 제안 있음 | [가이드](guide/document-guide.md#api-계약) |
| [ERD 초안](docs/database/erd.md) | 데이터 개체·키·관계 | 개인 담당 8개 테이블 관계도 있음 | [가이드](guide/document-guide.md#erd-초안) |
| [데이터 사전](docs/database/data-dictionary.md) | 컬럼 타입·의미·제약 | 개인 담당 8개 테이블 컬럼 초안 있음 | [가이드](guide/document-guide.md#데이터-사전) |
| [테스트 전략](docs/testing/test-strategy.md) | 검증할 조건·기대 결과·방법 | 검증 계획 있음·실행 결과 없음 | [가이드](guide/document-guide.md#테스트-전략) |
| [문서 규칙](docs/conventions/documentation-standard.md) | 문서 상태·출처·이름·갱신 기준 | 작성·갱신 규칙 초안 있음 | [가이드](guide/document-guide.md#문서-규칙) |
| [Git 협업 초안](docs/conventions/git.md) | 브랜치·커밋·리뷰·병합·배포 | 협의용 초안·PR 양식 있음 | [가이드](guide/document-guide.md#git-협업-초안) |
| [프론트 규칙](docs/conventions/frontend.md) | 프론트 구현·상태·UI·검증 기준 | 상세 원문·적용 메모 있음 | [가이드](guide/document-guide.md#프론트-규칙) |
| [백엔드 규칙](docs/conventions/backend.md) | 백엔드 책임·트랜잭션·오류 기준 | 상세 원문·충돌 메모 있음 | [가이드](guide/document-guide.md#백엔드-규칙) |
| [DB 규칙](docs/conventions/database.md) | 테이블 공통 명명·타입·시간 기준 | 공통 기준·적용 쟁점 있음 | [가이드](guide/document-guide.md#db-규칙) |
| [기술 결정 템플릿](docs/decisions/ADR-000-template.md) | 선택한 방법과 대안·이유 기록 | 빈 양식만 있음 | [가이드](guide/document-guide.md#기술-결정-템플릿) |
| [문제 해결 템플릿](docs/troubleshooting/_template.md) | 실제 문제의 재현·원인·해결 기록 | 빈 양식만 있음 | [가이드](guide/document-guide.md#문제-해결-템플릿) |
| [AI 활용·작업 기록 템플릿](docs/worklogs/_template.md) | 작업 목표·AI 활용·판단·검증 기록 | 빈 양식만 있음 | [가이드](guide/document-guide.md#ai-활용작업-기록-템플릿) |

## 포트폴리오 기록 원칙

요구사항 → 설계·선택 이유 → 구현 커밋·PR → 검증 결과를 연결합니다. 가상 데이터와 실제 운영 데이터를 구분하고, 예측 성능·부하 처리량은 조건과 측정 근거가 있을 때만 기재합니다.

## 문서 작성이 처음이라면

[문서별 작성 가이드](guide/document-guide.md)에서 각 파일의 역할과 작성 항목을 먼저 확인하세요. 현재 적혀 있는 내용과 보완할 내용도 실제 문서 기준으로 안내합니다. [가이드 목차](guide/README.md)의 가상 예시는 작성 형태가 궁금할 때 참고하세요.
