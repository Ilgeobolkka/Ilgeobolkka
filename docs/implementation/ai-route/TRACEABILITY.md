# AI 잉크 경로 구현 추적성

[구현 작업 색인](./README.md)으로 돌아갑니다. 이 표는 요구사항·필수 시나리오를 leaf 작업에 연결할 뿐
정본 값을 복사하거나 구현 완료를 표시하지 않습니다.

## 요구사항 연결

| 요구사항 | 주 구현 작업 | 회귀·소비 작업 |
| --- | --- | --- |
| AIR-001 | [G01](./generation/G01-purpose-input.md), [G08](./generation/G08-generation-api.md) | [W01](./web/W01-generation-page.md) |
| AIR-002 | [G01](./generation/G01-purpose-input.md), [G04](./generation/G04-route-assembly.md) | [W01](./web/W01-generation-page.md) |
| AIR-003 | [G01](./generation/G01-purpose-input.md), [G04](./generation/G04-route-assembly.md) | [W01](./web/W01-generation-page.md) |
| AIR-004 | [C02](./content/C02-metadata-graph-validation.md), [G02](./generation/G02-candidate-search.md), [G03](./generation/G03-output-validation.md), [G04](./generation/G04-route-assembly.md) | [Q01](./release/Q01-evaluation-runner.md) |
| AIR-005 | [G04](./generation/G04-route-assembly.md), [G07](./generation/G07-generation-orchestration.md) | [S01](./saved-route/S01-save-generation.md) |
| AIR-006 | [F05](./foundation/F05-responses-adapter.md), [G03](./generation/G03-output-validation.md), [G04](./generation/G04-route-assembly.md) | [W01](./web/W01-generation-page.md) |
| AIR-007 | [G05](./generation/G05-idempotency-daily-limit.md), [G06](./generation/G06-generation-lifecycle.md), [S01](./saved-route/S01-save-generation.md) | [G08](./generation/G08-generation-api.md) |
| AIR-008 | [G04](./generation/G04-route-assembly.md), [S04](./saved-route/S04-content-progress.md) | [W02](./web/W02-route-detail-page.md) |
| AIR-009 | [S04](./saved-route/S04-content-progress.md), [S05](./saved-route/S05-feedback.md) | [W02](./web/W02-route-detail-page.md) |
| AIR-010 | [S03](./saved-route/S03-current-delete.md) | [W02](./web/W02-route-detail-page.md) |
| AIR-011 | [G05](./generation/G05-idempotency-daily-limit.md), [G06](./generation/G06-generation-lifecycle.md) | [G08](./generation/G08-generation-api.md) |
| AIR-012 | [G03](./generation/G03-output-validation.md), [G07](./generation/G07-generation-orchestration.md) | [Q01](./release/Q01-evaluation-runner.md) |
| AIR-013 | [F03](./foundation/F03-openai-configuration.md), [F04](./foundation/F04-embeddings-adapter.md), [F05](./foundation/F05-responses-adapter.md) | [Q03](./release/Q03-release-regression.md) |
| AIR-014 | [C01](./content/C01-manifest-parsing.md), [Q01](./release/Q01-evaluation-runner.md), [Q02](./release/Q02-metrics-activation.md) | [Q03](./release/Q03-release-regression.md) |
| AIR-015 | [F03](./foundation/F03-openai-configuration.md), [C03](./content/C03-content-embeddings.md) | [Q02](./release/Q02-metrics-activation.md) |
| AIR-016 | [G08](./generation/G08-generation-api.md), [S01](./saved-route/S01-save-generation.md), [S02](./saved-route/S02-route-query.md), [S03](./saved-route/S03-current-delete.md), [S04](./saved-route/S04-content-progress.md), [S05](./saved-route/S05-feedback.md) | [Q03](./release/Q03-release-regression.md) |
| AIR-017 | [F03](./foundation/F03-openai-configuration.md), [F04](./foundation/F04-embeddings-adapter.md), [F05](./foundation/F05-responses-adapter.md) | [G08](./generation/G08-generation-api.md) |
| AIR-018 | [F03](./foundation/F03-openai-configuration.md), [C03](./content/C03-content-embeddings.md), [G08](./generation/G08-generation-api.md) | [Q03](./release/Q03-release-regression.md) |

## 필수 시나리오 연결

| 시나리오 | 주 검증 작업 |
| --- | --- |
| T-AIR-001 | [G01 입력 정규화](./generation/G01-purpose-input.md), [G08 생성 API](./generation/G08-generation-api.md), [W01 생성 화면](./web/W01-generation-page.md) |
| T-AIR-002 | [G04 경로 조립](./generation/G04-route-assembly.md) |
| T-AIR-003 | [G03 출력 검증](./generation/G03-output-validation.md) |
| T-AIR-004 | [S01 단일 저장](./saved-route/S01-save-generation.md) |
| T-AIR-005 | [S01 단일 저장](./saved-route/S01-save-generation.md), [S03 현재·삭제](./saved-route/S03-current-delete.md) |
| T-AIR-006 | [S04 콘텐츠·진행](./saved-route/S04-content-progress.md) |
| T-AIR-007 | [G05 멱등·일일 한도](./generation/G05-idempotency-daily-limit.md) |
| T-AIR-008 | [G05 멱등·일일 한도](./generation/G05-idempotency-daily-limit.md), [G06 생명주기](./generation/G06-generation-lifecycle.md) |
| T-AIR-009 | [G06 생명주기](./generation/G06-generation-lifecycle.md), [G07 orchestration](./generation/G07-generation-orchestration.md) |
| T-AIR-010 | [F04 Embeddings](./foundation/F04-embeddings-adapter.md), [F05 Responses](./foundation/F05-responses-adapter.md) |
| T-AIR-011 | [C02 그래프 검증](./content/C02-metadata-graph-validation.md), [C04 원자적 적재](./content/C04-atomic-import.md) |
| T-AIR-012 | [Q01 평가 runner](./release/Q01-evaluation-runner.md), [Q02 지표·활성화](./release/Q02-metrics-activation.md) |
| T-AIR-013 | [G04 경로 조립](./generation/G04-route-assembly.md), [G06 생명주기](./generation/G06-generation-lifecycle.md) |
| T-AIR-014 | [F03 설정](./foundation/F03-openai-configuration.md), [Q02 지표·활성화](./release/Q02-metrics-activation.md) |
| T-AIR-015 | [C03 콘텐츠 embedding](./content/C03-content-embeddings.md), [G08 생성 API](./generation/G08-generation-api.md) |
| T-AIR-016 | [G08 생성 API](./generation/G08-generation-api.md), [S01 저장](./saved-route/S01-save-generation.md), [S02 조회](./saved-route/S02-route-query.md), [S03 현재·삭제](./saved-route/S03-current-delete.md), [S04 콘텐츠·진행](./saved-route/S04-content-progress.md), [S05 피드백](./saved-route/S05-feedback.md) |
| T-AIR-017 | [S05 피드백](./saved-route/S05-feedback.md) |
| T-AIR-018 | [F03 설정](./foundation/F03-openai-configuration.md), [C03 콘텐츠 embedding](./content/C03-content-embeddings.md), [Q03 출시 회귀](./release/Q03-release-regression.md) |
| T-AIR-019 | [G06 생명주기](./generation/G06-generation-lifecycle.md), [S01 단일 저장](./saved-route/S01-save-generation.md), [S03 현재·삭제](./saved-route/S03-current-delete.md) |

## 교차 불변식

- `INV-014` 생성 무과금: [G04](./generation/G04-route-assembly.md), [G07](./generation/G07-generation-orchestration.md), [S01](./saved-route/S01-save-generation.md), [Q03](./release/Q03-release-regression.md)
- `INV-015` 생성 결과 신뢰 경계: [C02](./content/C02-metadata-graph-validation.md), [G03](./generation/G03-output-validation.md), [G04](./generation/G04-route-assembly.md), [S01](./saved-route/S01-save-generation.md)
- `INV-016` 생성 횟수 원자성과 멱등성: [G05](./generation/G05-idempotency-daily-limit.md), [G06](./generation/G06-generation-lifecycle.md)
- `INV-017` AI 페이지 가이드 보호: [G04](./generation/G04-route-assembly.md), [W01](./web/W01-generation-page.md), [W02](./web/W02-route-detail-page.md)
- `INV-018` 외부 AI 데이터 경계: [F04](./foundation/F04-embeddings-adapter.md), [F05](./foundation/F05-responses-adapter.md), [C03](./content/C03-content-embeddings.md)
- `INV-019` 공급자 비용과 정책 확인: [F03](./foundation/F03-openai-configuration.md), [G08](./generation/G08-generation-api.md), [Q02](./release/Q02-metrics-activation.md)
- `INV-020` 대표 목적 품질 회귀: [Q01](./release/Q01-evaluation-runner.md), [Q02](./release/Q02-metrics-activation.md)
- `INV-021` AI 경로 소유권: [G08](./generation/G08-generation-api.md), [S01](./saved-route/S01-save-generation.md), [S02](./saved-route/S02-route-query.md), [S03](./saved-route/S03-current-delete.md), [S04](./saved-route/S04-content-progress.md), [S05](./saved-route/S05-feedback.md)
