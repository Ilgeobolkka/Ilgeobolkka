# Q02 평가 지표·지원 활성화

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 9 / 담당 A
- 선행: [Q01 평가 runner](./Q01-evaluation-runner.md)
- 재평가 자동 전환 선행: [GATE-AIR-04](../00-implementation-gates.md#gate-air-04-재평가-중-공개-지원-상태)
- 후속: [Q03 통합 회귀·출시](./Q03-release-regression.md)

## 목표

Q01 결과와 지정 검수자의 판정을 정본 수식으로 계산하고, 최초 `ai-route-v2` 전체 기준을 통과한 경우에만
비소설 90권의 support flag를 한 transaction으로 활성화합니다.

## 정본 링크

- [PRD 품질과 출시 기준](../../../prd/ai-ink-route.md#품질과-출시-기준)
- [코퍼스 품질 평가 조건](../../../ai-route-content-corpus.md#품질-평가-조건)
- [테스트 전략 품질 계산](../../../test-strategy.md#8-변환과-ai-경로-품질-확인)
- [OpenAI 데이터 정책 프로필](../../../evidence/openai-data-policy/README.md)
- 필수 시나리오: [T-AIR-012·014·015](../../../test-strategy.md#5-필수-시나리오)

## 현재 구현 기준선

- Q01 이전에는 평가 결과 schema·metrics calculator·support activation service가 없습니다.
- C04는 최초 대상 book의 `ai_route_supported=false`를 보장합니다.
- 재평가 중 기존 support 처리 순서는 GATE-AIR-04가 해결하기 전 구현하지 않습니다.

## 입력과 산출물

- 입력: Q01 90 case 결과, evaluation 정답, 검수자 90개 useful 판정
- 산출물: `AiRouteEvaluationMetrics`, `AiRouteEvaluationReport`, `AiRouteSupportActivationService`
- artifact: manifest/evaluation Git revision, 실행 시각, embeddingModel, routeModel, candidate·prompt·schema version, 자동 지표·사람 판정
- Q03에 넘길 것: 통과 report와 support true 90권·소설 false 증거

## 수정 허용 파일

- 새 evaluation metrics·report writer·activation service
- Book Repository의 같은 contentVersion 90권 조건 update/lock query
- 새 `AiRouteEvaluationMetricsTest`, `AiRouteSupportActivationMySqlIntegrationTest`

## 구현 조건

1. 필수 개념 포함률, 무관·중복 비율, 선수 위반률의 분모·합집합 규칙을 정본 그대로 계산합니다.
2. 분모 하나라도 0이면 metric을 0으로 대체하지 않고 평가 실패합니다.
3. 90개 유효 시간을 오름차순으로 정렬한 86번째 값을 p95로 사용하고 20초 초과 0건을 별도 확인합니다.
4. 90개 모두 ROUTE·예산 초과/선차감/무단 본문 0건과 사람 유용성 기준을 함께 판정합니다.
5. report에 provider 원문·purpose·분석 text·API key를 기록하지 않습니다.
6. 최초 활성화는 같은 contentVersion·profile의 비소설 90권을 한 transaction으로 true, 소설 10권은 false로
   유지합니다. 일부 true를 허용하지 않습니다.
7. 입력 revision·embeddingModel·routeModel·candidate·prompt·schema version 중 하나라도 바뀌면 기존 report 재사용을 거부합니다.

## 테스트

- 각 지표의 정확 경계 직전·동일·직후와 분모 0 실패
- duplicate group 합집합, 선수 누락·역순, p95 85/86/87번째 경계
- report 필수 재현 필드와 금지 원문 부재
- 90권 정상 활성화, 89권·소설 포함·profile/version 불일치·중간 SQL 실패 전체 rollback
- 기존 report 재사용 허용·거부 matrix
- 명령: `./gradlew test --tests '*AiRouteEvaluationMetricsTest' --tests '*AiRouteSupportActivationMySqlIntegrationTest'`

## 제외 범위

- 사람 검수 UI·자동 판정
- 재평가 중 기존 공개 support 자동 전환
- 사용자 feedback 70% 관찰 dashboard

## 완료 조건

- T-AIR-012·014·015의 품질·활성화 부분이 경계값 테스트됩니다.
- 최초 활성화는 90권 전체 성공 또는 변화 0건입니다.
- `./gradlew check`가 통과합니다.

## 인계

Q03에 report 위치·checksum, 활성화 SQL 검증과 지원/미지원 book 표본을 전달합니다. 재평가 기능 요청은
GATE-AIR-04 해제 전 별도 작업으로 돌립니다.
