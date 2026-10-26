# WORK-100: 실제 이력 기반 주간 예측 배치

상태: 검증 대기 · 작성일: 2026-10-26 · 담당: 관리자·분석

## 목표와 완료 기준
- 일요일 18:00 기준 판매 이력으로 PyTorch 예측을 만들고 22:00에 다음 주 결과를 저장한다.
- 실제 학습 이력이 없으면 가상 결과를 저장하지 않는다.

## 근거와 범위
- 사용자 요청 18번과 별도 Python 작업 사용 결정.
- 기존 `forecast_model` 학습·추론 코드를 재사용한다. Java 배포 이미지에 Python을 추가하지 않는다.

## 구현 결과
- Java 배치가 상품별 28일 이력과 과거 8주 학습 표본을 생성해 내부 Python 작업에 보낸다.
- Python 작업은 모델을 학습·추론하고 대상 주별 모델 파일을 보관한다. Java는 모델 버전·입력 마감 시각·예측값·기준선을 저장한다.
- Docker Compose에 호스트 포트를 게시하지 않는 Python 작업을 추가했다.

## 검증
| 명령·시나리오 | 결과 |
|---|---|
| `gradlew.bat compileJava` | 통과 |
| `ForecastPredictionServiceTest`, `ForecastPredictionSaveServiceTest`, `ForecastWorkerClientTest` | 통과 |
| 임시 PyTorch CPU 환경 `python -m unittest discover -s forecast_model/tests -v` | 7개 통과 |
| Python 작업 컨테이너 빌드·운영 연결 | Docker 엔진 미실행으로 검증 대기 |

## 연결과 남은 작업
- 운영 배포 구성에서 Python 작업을 Java 앱의 내부 네트워크에 연결하고 `FORECAST_WORKER_URL`을 설정해야 한다.
- 실제 판매 이력으로 모델 품질을 평가해야 한다. 정확도 수치는 아직 없다.
