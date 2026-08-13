# G04 예산·선수·가이드 경로 조립

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 5 / 담당 B
- 선행: [G02 후보 검색](./G02-candidate-search.md), [G03 출력 검증](./G03-output-validation.md)
- 후속: [G07 orchestration](./G07-generation-orchestration.md), [S01 단일 저장](../saved-route/S01-save-generation.md)

## 목표

검증된 proposal을 현재 권한 snapshot의 비용·선수 조건으로 순회해 ROUTE 또는 두 NO_ROUTE 결과를 만들고,
공개 metadata만으로 표시 항목을 조립합니다.

## 정본 링크

- [비소장·소장 입력 정책](../../../prd/ai-ink-route.md#독서-목적과-예산-입력)
- [경로 생성 정책](../../../prd/ai-ink-route.md#경로-생성-정책)
- [AI 페이지 가이드](../../../prd/ai-ink-route.md#ai-페이지-가이드)
- [예상 독서 시간](../../../prd/ai-ink-route.md#예상-독서-시간)
- 필수 시나리오: [T-AIR-002·013](../../../test-strategy.md#5-필수-시나리오)

## 현재 구현 기준선

- G02가 관련 후보, G03이 허용·순서를 검증하지만 비용·guide assembler는 없습니다.
- 기존 [OwnershipService](../../../../src/main/java/com/example/ilgeobolkka/ownership/service/OwnershipService.java),
  [RentalService](../../../../src/main/java/com/example/ilgeobolkka/rental/service/RentalService.java),
  [InkService](../../../../src/main/java/com/example/ilgeobolkka/ink/service/InkService.java)는 G07이 snapshot을 만들 때만 사용합니다.

## 입력과 산출물

- 입력: `ValidatedRouteProposal`, request command, owned 또는 balance·activeRentalPageIds snapshot,
  publicGuideTopic·estimatedReadingSeconds
- 산출물: `AiRouteAssembler`, `AiRouteGuideFactory`, `AiRouteGenerationResult`
- 결과: ROUTE items 또는 NO_RELEVANT_PAGES/INSUFFICIENT_BUDGET+minimumRequiredInk
- G07·S01에 넘길 것: 영속화 가능한 공급자 중립 결과와 생성 때 사용한 비용 snapshot

## 수정 허용 파일

- 새 `airoute/service/assembly`의 assembler·guide factory·결과 타입
- 깊이 상한을 G07이 구분할 새 `airoute/exception` 전용 예외 타입
- G04가 처음 드러낸 선수 폐쇄·중복 불변식을 보강하는 C02 validator·테스트, 정본 코퍼스·평가 데이터와
  제작 검증 도구
- 기존 ownership/rental/ink 코드는 수정하지 않음
- 새 `AiRouteAssemblerTest`, `AiRouteGuideFactoryTest`

## 구현 조건

1. owned는 모든 page 추가 비용 0, QUICK 5·BALANCED 10·DEEP 15 상한을 적용합니다.
2. non-owned는 `bookPageId`로 active rental page를 판정해 0, 나머지를 1로 계산하고 누적 새 비용이
   예산을 넘지 않게 합니다.
3. 선수를 비용 때문에 제외하면 그 선수에 의존하는 page도 제외합니다.
4. 상한·예산을 채우려고 무관 page를 추가하지 않고 같은 page를 중복 포함하지 않습니다.
   C02를 통과한 후보와 선수 폐쇄에는 같은 중복 그룹 페이지가 둘 이상 없으며, 조립 입력에서도 이 불변식을
   다시 확인해 위반하면 후보 입력 예외로 중단합니다.
5. 관련 후보가 없으면 NO_RELEVANT_PAGES/null, 비소장 관련 후보 묶음의 최소 비용이 선택 예산보다 크면
   minimumRequiredInk의 INSUFFICIENT_BUDGET을 반환합니다. 소장 후보 묶음을 깊이 상한 안에서 완성할 수
   없는 경우는 현재 두 NO_ROUTE 계약으로 잘못 분류하지 않고 `AiRouteDepthLimitExceededException`으로
   중단하며 [SCRUM-486](https://rkdworn-1784629548680.atlassian.net/browse/SCRUM-486)의 G07에
   인계합니다.
6. guide는 publicGuideTopic과 server role 템플릿으로만 만들고 analysisText·모델 문구를 입력받지 않습니다.
7. estimated minutes와 `ONE_INK|ACTIVE_RENTAL|OWNED`를 정본 계산으로 만들며 상태를 변경하지 않습니다.
8. 예산·중복으로 모든 후보를 넣지 못하면 `HIGH`를 `MEDIUM`보다 먼저 선택합니다. 같은 relevance에서는
   더 큰 선수 묶음을 먼저 검토해 앞선 중복 형제가 뒤 후보의 완전한 묶음을 막지 않게 하고, 크기도 같으면
   proposal 순서를 유지합니다. 최종 표시 순서는 선택 우선순위가 아니라 G03이 검증한 proposal 읽기 순서를
   그대로 유지합니다.

## 테스트

- non-owned 0·5·10·15 예산, active rental 혼합과 잔액 상한
- owned 세 depth와 관련 page 부족
- owned 후보·선수 묶음이 depth 상한을 넘을 때 내부 예외
- 다단계 선수 비용 제외·의존 제거, duplicate group 중복 억제
- 앞선 중복 형제와 뒤 선수 묶음의 경합, `HIGH`·`MEDIUM` 선택 우선순위와 최종 읽기 순서
- C02와 정본 제작 검증기의 후보·전이적 선수 폐쇄 중복 그룹 충돌 거부
- 두 NO_ROUTE와 minimumRequiredInk 경계
- guide에 공개 topic·role만 있고 분석 text·결론·수치가 없는지 확인
- 명령: `./gradlew test --tests '*AiRouteAssemblerTest' --tests '*AiRouteGuideFactoryTest'`

## 제외 범위

- DB에서 현재 권한 조회, OpenAI 호출·재시도
- generation Entity 저장과 route 저장
- 사용자별 독서 속도·비율 표시

## 완료 조건

- T-AIR-002·013과 INV-014·017 계산 경계가 순수 단위 테스트됩니다.
- assembler 실행 전후 어떤 Entity도 변경되지 않습니다.
- `./gradlew check`가 통과합니다.

## 인계

G07 담당자에게 `bookPageId` 기반 권한 snapshot과 결과 타입을 전달합니다. 소장 후보·선수 묶음이 depth
상한을 넘는 전용 예외의 정상 결과 계약은
[SCRUM-486](https://rkdworn-1784629548680.atlassian.net/browse/SCRUM-486)의 G07에서 결정합니다.
S01 담당자에게 저장 시 재계산할 비용 입력과 생성 예산 필드를 전달하며, 공통 비용 상태 판정 추출은
[SCRUM-487](https://rkdworn-1784629548680.atlassian.net/browse/SCRUM-487)에서 추적합니다.
