# 백엔드 개발 규칙

상태: 기준본 안내 · 최초 정리: 2026-10-14 · 기준본으로 옮김: 2026-11-09

백엔드 규칙의 기준본은 [take-care-of-my-fridge-be/CLAUDE.md](../../take-care-of-my-fridge-be/CLAUDE.md)입니다. 이 문서에는 규칙을 복사하지 않고, 팀 결정·참고만 둡니다. 규칙을 바꾸면 기준본만 고칩니다.

함께 적용: [공통 지침](../../AGENTS.md), [DB 규칙](database.md).

## 팀 결정·참고
- `JPAQueryFactory`·`RedisTemplate`·`KafkaTemplate`은 등록된 빈을 주입하고 새로 만들지 않습니다(S3).
- `BusinessException`은 `ErrorCode`를 가집니다. 하위 예외를 둘 때도 공통 처리기가 `ErrorCode`를 꺼낼 수 있어야 합니다(S3).
- `./gradlew build --dry-run`은 작업 계획 확인일 뿐, 의존성 해석·컴파일 성공의 증거가 아닙니다.
- AFTER_COMMIT 이벤트는 메시지 전달을 보장하지 않습니다. 재시도·복구 요구는 [시스템 설계](../architecture/system-design.md)에 적습니다.
- 처음 공유된 규칙 원문(S1)은 git 기록에 있습니다(`git log -- docs/conventions/backend.md`).
