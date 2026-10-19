# WORK-043: 주간 판매량 신경망 구조

상태: 검증 대기 · 작성일: 2026-10-19 · 담당: 관리자·분석

## 목표와 완료 기준

- 18번 첫 단위로 최근 28일 일별 순판매량에서 다음 7일 총량을 예측하는 신경망 구조를 만든다.
- 배치 입력의 상품별 출력 개수와 음수가 아닌 출력 범위를 확인한다.

## 근거와 범위

- 사용자 결정: PyTorch 모델을 먼저 만들고 배치 연결은 다음 단계로 진행한다.
- 28일 입력은 첫 모델의 가정이며 실제 데이터로 성능을 검증한 설계는 아니다.
- 학습·예측 함수, 실제 데이터 연결, 모델 저장과 배치는 다음 변경으로 분리한다.

## 구현 결과

- `forecast_model/model.py`: 2개 은닉층 신경망.
- `forecast_model/requirements.txt`: PyTorch 2.13.0 의존성.
- `forecast_model/tests/test_model.py`: 출력 형태와 범위 확인.
- `docs/architecture/system-design.md`: PyTorch 모델의 첫 구현 상태 기록.

## 검증

- 임시 가상환경의 PyTorch 2.13.0 CPU로 `python -m unittest discover -s forecast_model/tests -p test_model.py -v`: 1개 통과.
- Python 문법 검사: 통과.
- 실제 판매 데이터의 예측 정확도는 검증하지 못했다.

## 연결과 남은 작업

- 학습·예측 함수와 가상 데이터 테스트를 다음 작은 변경에 추가한다.
