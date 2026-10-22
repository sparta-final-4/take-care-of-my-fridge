# WORK-070: 레시피 실패 통계 API 구현

상태: 완료 · 작성일: 2026-10-21 · 담당: AI 보조

## 목표와 완료 기준
- 사용자 문제: 레시피 상세 화면에서 해당 레시피의 요리 성공/실패 횟수 및 총 요리 횟수를 조회할 수 있어야 함.
- 확인 가능한 완료 조건:
  - `GET /api/recipes/{recipeId}/cooking-stats` 엔드포인트 제공 (인증 없이 호출 가능)
  - 요리 기록이 없는 레시피는 404가 아닌 0으로 반환 (`totalCount`: 0, `successCount`: 0, `failedCount`: 0)
  - 개인정보(메모, 회원 정보) 미포함
  - 실패율은 응답에 포함하지 않음 (FE에서 계산)
  - 단일 QueryDSL 집계 쿼리로 실시간 집계 처리

## 근거와 범위
- 요구사항: 요리 기록 기반 레시피 요리 실패 통계 API
- 변경할 영역: `take-care-of-my-fridge-be` (cooking 도메인)
- 다른 도메인 영향·기존 승인 범위: recipe 도메인 코드는 수정하지 않음 (`SecurityConfig` 내 `/api/recipes/**` permitAll 설정 확인만 진행)
- 미정 정책·가정: 레시피 존재 여부 검증은 추후 `RecipeService`에 조회 메서드가 추가되면 연동하도록 TODO 주석으로 처리

## 계획
| 단계 | 변경 | 검증 |
|---|---|---|
| 1. DTO 설계 | `CookingStatsCount`, `CookingStatsResponse` 작성 | DTO 단위/조합 검증 |
| 2. Repository 집계 구현 | QueryDSL 단일 쿼리로 count 및 sum(case...) 집계 | 레포지토리 단위/통합 테스트 |
| 3. Service 연동 | `CookingService.getCookingStats(recipeId)` 추가 | 서비스 단위 테스트 |
| 4. Controller 엔드포인트 구현 | `CookingStatsController` 추가 | WebMvcTest 슬라이스 테스트 |

## AI 활용과 개발자 판단
- AI에 맡긴 작업·핵심 지시:
  - QueryDSL SELECT 절에서 파라미터 `recipeId`를 직접 프로젝션(`Expressions.asNumber`)하지 않고 자바에서 조합하는 구조 적용 (Hibernate 6 SQM 파라미터 타입 이슈 방지)
- 제안 중 채택한 내용과 이유:
  - `CookingStatsCount`로 집계 숫자 3개만 조회한 후 `CookingStatsResponse.of(recipeId, count)`로 조립하여 타입 안정성 및 Hibernate 6 호환성 확보
- 개발자가 확인한 근거:
  - Spring Security 설정상 `/api/recipes/**`가 `permitAll`에 포함되어 있어 미인증 상태에서도 접근 가능 확인

## 구현 결과
- 생성 파일:
  - `CookingStatsCount.java`: 쿼리 집계 결과 매핑 record
  - `CookingStatsResponse.java`: 최종 응답 DTO record
  - `CookingStatsController.java`: `GET /api/recipes/{recipeId}/cooking-stats` 컨트롤러
  - `CookingStatsControllerTest.java`: 컨트롤러 슬라이스 테스트
- 변경 파일:
  - `CookingLogRepositoryCustom.java`, `CookingLogRepositoryImpl.java`: `getCookingStats` QueryDSL 단일 집계 메서드 추가
  - `CookingService.java`: `getCookingStats` 위임 메서드 추가
  - `CookingLogRepositoryTest.java`: DB 집계 쿼리 통합 테스트 추가
  - `CookingLogRepositoryImplTest.java`: 단일 쿼리 조립 단위 테스트 추가
  - `CookingServiceTest.java`: 서비스 호출 위임 단위 테스트 추가

## 검증
| 명령·시나리오 | 실행 환경·조건 | 통과/실패/미실행 | 근거 |
|---|---|---|---|
| `./gradlew test --tests "*CookingStats*" --tests "*CookingLog*" --tests "*CookingService*"` | 로컬 JDK 21 | 통과 | 49개 테스트 전체 통과 (컨트롤러, 서비스, 리포지토리 단위/통합) |
| `npm --prefix take-care-of-my-fridge-fe run build` | 로컬 Node.js | 통과 | 프론트엔드 빌드 정상 완료 |

## 연결과 남은 작업
- 미검증·결정 대기 항목: 향후 recipe 도메인에 Recipe 조회 메서드가 구현되면 TODO 주석 위치에 레시피 존재 여부 검증 추가
