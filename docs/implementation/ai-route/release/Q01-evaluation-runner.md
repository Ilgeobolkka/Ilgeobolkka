# Q01 비웹 평가 runner

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 7 / 담당 A
- 선행: [C04 AI 콘텐츠 적재](../content/C04-atomic-import.md),
  [G07 생성 orchestration](../generation/G07-generation-orchestration.md)
- 후속: [Q02 평가 지표·활성화](./Q02-metrics-activation.md)

## 목표

`evaluation.json`의 대표 목적 전체(`N`건)를 사용자·잉크·대여·소장·결제·저장 route 없이 운영과 같은 후보 검색,
Responses, 서버 검증·경로 조립 코드로 실행하고 원시 판정 입력을 만듭니다.

## 정본 링크

- [품질 평가 데이터](../../../ai-route-content-corpus.md#품질-평가-데이터)
- [품질 평가 조건](../../../ai-route-content-corpus.md#품질-평가-조건)
- [PRD 품질과 출시 기준](../../../prd/ai-ink-route.md#품질과-출시-기준)
- [테스트 전략 AI 품질 확인](../../../test-strategy.md#8-변환과-ai-경로-품질-확인)
- 필수 시나리오: [T-AIR-012](../../../test-strategy.md#5-필수-시나리오)

## 현재 구현 기준선

- [ContentImportRunner](../../../../src/main/java/com/example/ilgeobolkka/contentimport/ContentImportRunner.java) 외에
  AI evaluation 비웹 profile·runner는 없습니다.
- C04 DB에는 support false인 AI content가 있고 G07은 공급자 중립 생성 엔진 진입점을 제공합니다.
- evaluation 정답을 runtime 입력에서 분리하는 코드 경계가 없습니다.

## 입력과 산출물

- 입력: `fixtures/content/ai-route-v2/evaluation.json`, 같은 manifest·DB contentVersion
- 입력: case의 owned·budget/depth·activeRentalPageNumbers snapshot
- 산출물: `AiRouteEvaluationRunner`, `AiRouteEvaluationService`, evaluation 전용 reader
- 산출물: caseId별 route 결과·준비 구간을 포함한 처리 시간·정답 비교용 page/concept 자료와 같은 실행에서
  계산한 후보 임계값 검토 결과; provider 원문 제외

## 수정 허용 파일

- 새 non-web evaluation profile·runner·service·결과 타입
- G07의 평가용 공급자 중립 engine API는 호출만 하며 production Facade 수정 금지
- 새 `AiRouteEvaluationRunnerTest`, 기술 실행은 opt-in 통합 테스트로 분리

## 구현 조건

1. `evaluation.json`의 case 전체를 bookId ASC로 실행하고 일곱 시나리오가 모두 존재하는지 실행 전에
   확인합니다. 건수는 세지 않으며 `N`은 파일에 든 case 수입니다.
2. caseId·purpose·권한 snapshot만 생성 engine에 전달하고 required/helpful/reference/alternative 정답은 전달하지
   않습니다.
3. Reader·InkAccount·Ledger·Rental·Ownership·Payment·Generation·Route row를 만들거나 조회하지 않습니다.
4. 운영과 같은 normalization·Embeddings·`air-candidate-v1`·Responses·동일 snapshot 한 번 retry·output validation·assembly를 사용합니다.
5. 처리 시간은 evaluation entry부터 최종 route 확정까지 monotonic clock으로 측정합니다.
6. `N`건 모두 ROUTE여야 하며 case 실패를 건너뛰거나 NO_ROUTE를 정상 통과로 바꾸지 않습니다.
7. purpose·분석 text·provider request/response·API key를 결과 artifact와 로그에 기록하지 않습니다.

### 후보 임계값 변경 평가

- 후속 후보 정책 승격을 검토할 때만 같은 content·evaluation revision, 임베딩 모델·vector와
  30개 상한·동점 규칙을 고정한 채 `0.35`, `0.40`, `0.45`를 각각 실행합니다.
- 선수 페이지 폐쇄 전 후보의 `primaryConcepts[]`와 `requiredConcepts[]`는 대소문자를 구분한 문자열 완전
  일치로 비교합니다. `N`건 전체의 필수 개념 수를 분모로, 하나 이상의 후보에 정확히 일치한 필수 개념 수를
  분자로 사용합니다. trim·Unicode 정규화·부분 문자열·의미 유사도 비교는 적용하지 않습니다.
- 같은 도서에서 `aiRouteCandidatePage=true`인 후보 페이지의 `primaryConcepts[]`에 정확히 일치하지 않는
  `requiredConcepts[]`가 하나라도 있으면 평가 데이터 불일치로 임계값 비교를 실패합니다. 정답은 후보 선택
  후 지표 계산에만 사용하고 runtime 입력에 전달하지 않습니다.
- 재현율 95% 이상을 만족하는 가장 높은 값만 새 version 검토값으로 선택합니다.
  세 값이 모두 미달하면 `air-candidate-v1`의 `0.30`을 유지하며, 선택한 값은 새 version으로 `N`건 전체 경로를 재평가하기 전에 운영에 적용하지 않습니다.
- 승격을 결정한 뒤의 적용 순서는 [재평가 배포 순서](../../../deployment.md#재평가-배포-순서)를 따릅니다.

## 테스트

- case 전체 실행·bookId 순서·7 시나리오 분포와 중복 case/book 누락 실패
- 지원 도서 10권짜리 `evaluation.json`이 건수 때문에 실패하지 않음
- engine spy로 정답 필드 전달 0개 확인
- persistence spy/DB count로 사용자·결제·generation·route row 변화 0건
- 일부 case provider 실패·NO_ROUTE·timeout 때 전체 평가 실패와 완료 case 결과 구분
- 같은 engine의 embeddingModel·routeModel·candidatePolicyVersion·promptVersion·schemaVersion 기록 입력 확인
- 후보 정책 승격 평가의 `0.35`·`0.40`·`0.45` 고정, 문자열 완전 일치와 불일치 입력 실패, 선수 폐쇄 전
  필수 개념 재현율 95% 경계와 가장 높은 통과값 선택·전체 미달 시 `0.30` 유지
- 명령: `./gradlew test --tests '*AiRouteEvaluationRunnerTest'`
- 실제 공급자 평가는 `SPRING_PROFILES_ACTIVE=evaluation ./gradlew bootRun`으로 opt-in 실행하며,
  `OPENAI_PROJECT_ID`, `OPENAI_API_KEY`, `OPENAI_DATA_POLICY_VERSION`, manifest·evaluation Git revision과
  기존 파일이 아닌 결과 output 경로를 환경 변수로 제공합니다.

## 제외 범위

- 품질 비율·p95·사람 판정 최종 계산
- support true 활성화
- 별도 평가 DB·평가 사용자·OpenAI Evals 제품

## 완료 조건

- T-AIR-012의 `N`건 동일 엔진·무사용자 실행 경계가 자동 검증됩니다.
- 평가 정답이 runtime 추천 입력 타입에 들어갈 경로가 없습니다.
- `./gradlew check`가 통과합니다.

## 인계

Q02 담당자에게 case 결과 schema, 처리 시간 기준, manifest SHA-256, manifest/evaluation Git
revision·embeddingModel·routeModel과 candidate·prompt·schema version을 전달합니다. Q02의 report 재사용
판정이 manifest SHA-256을 쓰므로 이 값을 빠뜨리지 않습니다.
