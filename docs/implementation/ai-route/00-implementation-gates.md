# AI 잉크 경로 구현 전 결정 게이트

[구현 작업 색인](./README.md)으로 돌아갑니다. 현재 정본과 코드만으로 한 가지 구현을 결정할 수 없는
사항입니다. 구현자는 아래 값·오류·전환 순서를 추측하지 않습니다.

## GATE-AIR-01 초기 콘텐츠 버전 해제

- 결정 정본: [콘텐츠 변환의 초기 MVP 시연 PDF 기준선](../../content-conversion.md#초기-mvp-시연-pdf-기준선)
- 초기 manifest의 `contentVersion`은 `initial-v1`입니다.
- 버전 컬럼 도입 전 V1 schema의 모든 기존 `book` 행은 조건 없이 `initial-v1`로 backfill합니다.
- F01의 최종 `book.content_version`은 기존 writer 호환을 위해
  `VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'initial-v1'`입니다.
- manifest와 AI 콘텐츠 import는 DB 기본값에 의존하지 않고 `contentVersion`을 명시합니다.
- `ai-route-v2`는 사용자 기록과 HTTP 트래픽이 없는 새 시연 DB에 전체 적재하며 `initial-v1` fixture는
  재현·회귀 입력으로 보존합니다.
- 해제된 차단 작업: [F01 스키마 migration](./foundation/F01-schema-migration.md)
- 금지: 일부 ID만 선별 backfill, nullable 임시 컬럼, V1 수정, manifest 버전 누락 허용

## GATE-AIR-02 후보·prompt 정책 v1

- 결정 정본: [후보·prompt 정책 v1](../../prd/ai-ink-route.md#후보prompt-정책-v1)
- 후보 정책은 `air-candidate-v1`, 최소 cosine similarity는 `0.30` 이상, 상한은 30개이며 similarity
  내림차순·`pageNumber` 오름차순으로 고정합니다. 선수 폐쇄는 후보 선정 뒤 별도로 추가합니다.
- prompt·schema는 classpath의 고정 resource에서 읽고 논리 버전과 UTF-8 원본 byte의 SHA-256을 함께
  기록합니다. Responses의 `text.format`은 `type=json_schema`, `name=ai_route_proposal_v1`, `strict=true`로
  고정하고 malformed·semantic invalid output만 같은 후보·정책으로 전체 20초 안에서 한 번 재시도합니다.
- 해제된 차단 작업: [F05 Responses](./foundation/F05-responses-adapter.md),
  [G02 후보 검색](./generation/G02-candidate-search.md),
  [G03 출력 검증](./generation/G03-output-validation.md)
- 금지: 평가 정답의 runtime 입력 사용, v1 값의 무버전 변경, 재시도 후보·정책 교체, 임시 prompt·schema와
  중복 version 타입

## GATE-AIR-03 저장 전 권한 변동 오류

- 결정 정본: [저장과 생명주기 3단계](../../prd/ai-ink-route.md#저장과-생명주기),
  [저장 경로 결과와 상태 변경](../../api-spec.md#저장-경로-결과와-상태-변경),
  [목표 오류](../../api-spec.md#목표-오류)
- 저장 시점에 현재 권한으로 다시 계산한 추가 잉크가 생성 예산을 넘으면 `409
  AI_ROUTE_ENTITLEMENT_CHANGED`로 거부합니다. 입력이 아니라 서버 권한 상태가 생성 시점과 달라진
  충돌이므로 409입니다.
- `generationId`는 소비하지 않고 최초 거부를 기록하는 상태·전이도 추가하지 않습니다.
- 거부 응답과 생성 결과 재조회에는 재계산한 비용·권한 상세를 노출하지 않습니다(재조회는 생성 시점
  스냅샷 그대로). 저장 경로 상세 조회의 재계산은 기존 계약 그대로입니다. 이 스냅샷을 어떻게 실현할지
  (`ai_route_generation_item` 저장 vs `page_rental`·`book_ownership` 이력으로 재구성, 후자면 기준 시각)는
  생성 결과 조회 계약이므로 [G08](./generation/G08-generation-api.md)에서 정합니다.
- 화면은 **경로 다시 생성 한 가지만** 안내합니다. 서버가 재저장을 막지 않는데도 유도하지 않는 이유는,
  권한 상세를 노출하지 않아 어느 페이지를 대여해야 하는지 알릴 수 없고 대여 확보 자체가 경로 밖 잉크
  사용을 유발하기 때문입니다. 화면 조건은 [W01](./web/W01-generation-page.md)에 있습니다.
- 테스트 기대값은 [`T-AIR-020`](../../test-strategy.md#5-필수-시나리오)에 있습니다. 저장 자체의
  조작·만료 거부는 기존 `T-AIR-004`가 담당합니다.
- 코드 정의 주체는 [공유 파일 소유권](./README.md#공유-파일-소유권)을 따릅니다.
  `AI_ROUTE_GENERATION_CONSUMED`는 저장 endpoint와 생성 endpoint의 멱등 재조회
  ([api-spec](../../api-spec.md#생성-입력과-결과))가 함께 반환하므로, 먼저 진행하는 S01이 정의하고
  G08은 재사용합니다.
- 해제된 차단 작업: [S01 generation 저장](./saved-route/S01-save-generation.md)
- 금지: **권한 변동 케이스를** `AI_ROUTE_CONTENT_CHANGED`·`INVALID_INPUT`·`INSUFFICIENT_INK`로 매핑,
  거부하면서 `generationId` 소비, 재계산 비용·권한 상세 노출

## GATE-AIR-04 재평가 중 공개 지원 상태

- 결정 정본: [재평가와 지원 활성화 순서](../../prd/ai-ink-route.md#재평가와-지원-활성화-순서),
  [재평가 배포 순서](../../deployment.md#재평가-배포-순서),
  [AI 잉크 경로 2차 fixture 기준선](../../content-conversion.md#ai-잉크-경로-2차-fixture-기준선)
- 재평가는 재현 정보 일곱 항목 중 하나가 바뀌었을 때 대표 목적 전체를 다시 실행하는 것입니다. 일부
  도서·시나리오만 다시 실행하는 부분 재평가는 없습니다.
- 재평가는 HTTP 트래픽을 받지 않는 환경에서 수행하고 전체 기준을 통과한 조합만 공개 환경에 적용합니다.
  따라서 재평가 시작 시점에 기존 `ai_route_supported=true`를 내리지 않고, 미통과는 롤백이 아니라
  배포하지 않음입니다.
- 처리 순서는 콘텐츠 재적재가 포함되는지로만 갈립니다. manifest 리비전과 임베딩 모델은 재적재를 포함하고
  나머지 다섯 항목은 포함하지 않으며, 여러 항목이 함께 바뀌면 재적재를 포함하는 쪽을 따릅니다.
- 재적재가 포함되면 새 후보 DB에 `false`로 적재하고 통과 뒤 manifest 지원 도서 전체를 한 transaction으로 활성화한
  다음 HTTP 트래픽을 공개합니다. 사용자 기록이 생긴 환경의 콘텐츠 버전 변경은 2차 MVP 범위가 아니므로
  공개 전에만 실행합니다.
- 재적재가 없으면 `contentVersion`과 `ai_route_supported`를 바꾸지 않고 바뀐 코드·설정만 배포합니다.
  재평가는 별도 평가 DB를 만들지 않고 기존 콘텐츠 DB를 읽는 비공개 실행 환경에서 수행합니다.
- 분기와 무관하게 임베딩 모델이든 경로 모델이든 모델을 바꾸면 데이터 정책 프로필 재확인을 먼저 끝냅니다.
- `contentVersion`은 재적재에도 새로 발급하지 않으므로 `AI_ROUTE_CONTENT_CHANGED`는 2차 MVP에서 발생할
  사용자 시나리오가 없습니다. 그래도 콘텐츠 적재 배치는 코드상 임의 DB를 대상으로 실행할 수 있고 기존
  `(bookId, pageNumber)` 갱신을 허용하므로, 운영자 오적재를 저장 시점에 탐지하는 불변식 방어 검사로
  계약과 코드를 유지합니다. 습관성 방어 코드가 아니라 탐지 대상이 실재하는 검사입니다.
- 해제된 차단 작업: [Q02 평가 지표·활성화](./release/Q02-metrics-activation.md)
- 금지: 재평가를 이유로 한 공개 버전의 자동 비활성화·재활성화, 미통과 조합의 공개 환경 배포, 지원
  도서 중 일부만 활성화, 다른 version 조합의 기존 report 재사용, 데이터 정책 프로필 재확인 없는 모델 배포,
  `AI_ROUTE_CONTENT_CHANGED` 계약 삭제

## 해제 절차

1. 결정 값을 대응 정본에 먼저 반영합니다.
2. 차단 leaf 문서의 입력·테스트 기대값을 같은 내용으로 갱신합니다.
3. 색인의 차단 표시는 지우지 않고 해제된 정본 링크로 바꿔 결정 이력을 남깁니다.
4. Jira에는 결정 본문을 복사하지 않고 정본과 leaf 문서 링크만 연결합니다.
