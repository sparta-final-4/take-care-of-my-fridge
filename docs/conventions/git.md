# Git 협업 및 브랜치 규칙

> **기준 브랜치**: `main` (운영/배포), `dev` (개발 통합, 원격 기본 브랜치)  
> **PR 템플릿**: [`.github/pull_request_template.md`](../../.github/pull_request_template.md)

---

### 🍵 1. 브랜치 전략

- **`main`**: 실제 배포 및 프로덕션 운영을 위한 브랜치입니다. (직접 push / commit 절대 금지, PR 병합으로만 반영)
- **`dev` (또는 `develop`)**: 기능 개발 완료 후 1차로 모여 테스트하고 검증하는 **개발 통합 브랜치**입니다.
- **`작업 브랜치`**: 개별 기능 개발 및 버그 수정을 위한 브랜치입니다.
- **브랜치 명명 규칙**: **`작업 타입/기능명`** 형태의 소문자 kebab-case를 사용합니다.
  - 예시: `feat/cart-api`, `fix/login-error`, `refactor/chat-panel`

#### 🔄 작업 및 협업 순서
1. 로컬에서 최신 `dev` 브랜치를 기준으로 새 작업 브랜치를 생성합니다.
   ```bash
   git checkout dev
   git pull origin dev
   git checkout -b feat/cart-api
   ```
2. 작업 단위별로 코드를 작성하고 커밋(Commit) 규칙에 맞추어 커밋합니다.
3. 원격 저장소(`origin`)로 작업 브랜치를 푸시(Push)합니다.
   ```bash
   git push origin feat/cart-api
   ```
4. GitHub에서 **`dev` 브랜치를 대상(Base)**으로 Pull Request(PR)를 생성하고 리뷰어를 지정합니다.
5. 리뷰 및 CI 검증 통과 후(최소 1명 이상 승인), **`Squash and merge`** 방식으로 `dev` 브랜치에 병합합니다.
6. 병합이 완료된 작업 브랜치는 로컬 및 원격에서 삭제합니다.
7. *(배포 주기 또는 릴리즈 시)* `dev` 브랜치의 내용을 `main`으로 최종 병합합니다.

---

### 🚷 2. 깃헙 커밋 규칙 (Commit Convention)

커밋 메시지는 변경 목적을 명확히 파악할 수 있도록 접두사를 사용합니다.

| 작업 타입 | 이모지 | 설명 및 적용 기준 |
|---|:---:|---|
| **`feat`** | ✨ | 새로운 기능 추가 |
| **`fix`** | 🐛 | 버그 수정 |
| **`docs`** | 📄 | 문서 작성 및 수정 (README, API 명세, 설계 문서 등) |
| **`style`** | ♻️ | 코드 포맷팅, 세미콜론 누락 등 코드 동작에 영향이 없는 변경 |
| **`refactor`** | 🩹 | 코드 리팩터링 (동작 변경 없는 구조 개선) |
| **`test`** | 🚚 | 테스트 코드 추가 또는 기존 테스트 코드 리팩터링 |
| **`chore`** | 🔥 | 빌드 업무, 패키지 매니저 설정, 의존성 변경 등 기타 잡무 |

- **커밋 메시지 형식**: `타입: 내용 요약` (필요시 도메인 명시 `feat(cart): 장바구니 수량 변경 구현`)

---

### 📈 3. PR (Pull Request) 규칙

- **필수 설정**
  - **Base Branch**: 무조건 **`dev`**로 지정 (`main` 직접 지정 금지)
  - **Reviewers**: 팀원 지정 (최소 1명 이상의 Approve 필수)
  - **Assignees**: 작업자 본인 지정
  - **병합 방식**: 커밋 이력을 깔끔하게 유지하기 위해 **`Squash and merge`** 사용

- **PR 템플릿 ([`.github/pull_request_template.md`](../../.github/pull_request_template.md))**

```markdown
## 📌 작업 개요
- 작업 목적 및 배경을 간단히 작성해주세요.

## 🛠️ 작업 내용
- [ ] 주요 구현 및 변경 사항 1
- [ ] 주요 구현 및 변경 사항 2

## 📸 스크린샷 / 테스트 결과 (선택)
- Postman 결과나 실행 화면 캡처 첨부
```
