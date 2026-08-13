# G03 모델 출력 검증

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 4 / 담당 B
- 선행: [F05 Responses adapter](../foundation/F05-responses-adapter.md),
  [해제된 prompt·schema 정책 v1](../../../prd/ai-ink-route.md#후보prompt-정책-v1)
- 후속: [G04 경로 조립](./G04-route-assembly.md), [G07 orchestration](./G07-generation-orchestration.md)

## 목표

모델 proposal의 페이지가 검색 후보와 같은 version의 선수 폐쇄 안에 있는지 검증하고 한 항목이라도
잘못되면 전체 proposal을 거부합니다.

## 정본 링크

- [경로 생성 정책의 허용 집합](../../../prd/ai-ink-route.md#경로-생성-정책)
- [AI 페이지 가이드 경계](../../../prd/ai-ink-route.md#ai-페이지-가이드)
- [INV-015 생성 결과 신뢰 경계](../../../test-strategy.md#inv-015-생성-결과-신뢰-경계)
- 필수 시나리오: [T-AIR-003](../../../test-strategy.md#5-필수-시나리오)

## 현재 구현 기준선

- F05가 schema parse를 마친 `ModelRouteProposal`을 제공합니다.
- C02가 선수 edge 방향을, G02가 서버 후보를 고정하지만 runtime validator는 없습니다.
- 모델 출력에는 bookId·contentVersion이 없으므로 호출 context가 이를 고정해야 합니다.

## 입력과 산출물

- 입력: bookId·contentVersion, `air-candidate-v1`의 확정 순서 후보, 같은 version prerequisite graph, F05 proposal
- 산출물: `AiRouteOutputValidator`, `ValidatedRouteProposal`, `AiRouteInvalidOutputException`
- 산출물: 후보별 전이적 선수 폐쇄와 요청 허용 page 집합
- G04에 넘길 것: 순서·Enum·허용 집합을 통과한 proposal만

## 수정 허용 파일

- 새 `airoute/service/validation`의 validator·결과·예외
- F05 proposal, G02 candidate, C02 graph 타입은 읽기만 함
- 새 `AiRouteOutputValidatorTest`

## 구현 조건

1. G02의 최대 30개 후보를 받은 뒤 후보마다 DAG의 전이적 선수 page를 계산하고 후보와 선수 폐쇄의 합집합을
   허용 집합으로 고정합니다. 선수 폐쇄에는 similarity threshold와 30개 상한을 적용하지 않습니다.
2. proposal page가 다른 book/version, 미존재, 허용 집합 밖, 중복이면 전체 거부합니다. 허용 집합 안의
   선수 페이지만 있고 실제 검색 후보가 하나도 없을 때도 전체 거부합니다.
3. position은 입력 배열 순서로 고정하고 선수 page가 의존 page보다 뒤거나 누락되면 전체 거부합니다.
4. relevance·role·prerequisite가 F05 허용 Enum과 일치하는지 검증합니다.
5. 모델이 prerequisite=false로 보냈더라도 graph상 선수로 포함된 page의 서버 판정을 우선합니다.
6. 잘못된 항목만 제거하거나 순서를 자동 수정해 부분 성공하지 않습니다.
7. 오류에는 page 분석 text·purpose·provider response를 넣지 않고 실패 종류만 제공합니다.

## 테스트

- 정상 후보+`0.30` 미만 선수 폐쇄, 30개 후보 뒤 추가된 다단계 선수 순서
- 다른 book/version·미존재·허용 집합 밖·검색 후보 없이 선수만 있음·duplicate·선수 누락·역순 각각 전체 실패
- 자유 필드/Enum은 F05에서, semantic 허용 경계는 G03에서 실패하는 역할 분리
- 실패 결과에 부분 proposal·분석 text가 없는지 확인
- 명령: `./gradlew test --tests '*AiRouteOutputValidatorTest'`

## 제외 범위

- HTTP schema parse와 Responses 재시도
- 예산·소장 깊이·현재 권한 비용 계산
- guide·estimatedMinutes 조립

## 완료 조건

- T-AIR-003의 각 악성 proposal이 독립 테스트로 전체 거부됩니다.
- validator가 Repository·Gateway·Clock을 참조하지 않는 순수 코드입니다.
- `./gradlew check`가 통과합니다.

## 인계

G04 담당자에게 `ValidatedRouteProposal`과 서버가 확정한 prerequisite 표시를 전달합니다. G07에는 재시도
가능한 invalid-output 실패 타입을 전달합니다.
