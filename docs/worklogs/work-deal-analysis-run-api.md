# 할인 후보 분석 실행 API

`POST /api/admin/deal-analyses`는 ADMIN만 실행한다. 요청은 `targetWeekStart`(월요일), `dayOfWeek`(MON~SUN), `discountRate`(1~99)이며 목표 날짜는 오늘 또는 미래여야 한다. 성공 시 201과 기존 `DealAnalysisResponse`를 반환한다. 실제 일반 특가 이력 부족은 D007(422), 잘못된 요청은 C001(400)이다. 성공·실패·권한 거부를 관리자 작업 이력에 저장한다.
