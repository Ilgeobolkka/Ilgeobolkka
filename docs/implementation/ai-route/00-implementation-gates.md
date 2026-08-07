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

- 차단 작업: [S01 generation 저장](./saved-route/S01-save-generation.md)
- 확인된 불일치: [저장 정책](../../prd/ai-ink-route.md#저장과-생명주기)은 권한 변동으로 비용이 생성 예산을
  넘으면 저장을 거부하지만 [API 오류 계약](../../api-spec.md#목표-오류)은 공개 status·code를 정의하지
  않습니다.
- 결정할 내용: HTTP status, 안정된 ErrorCode, generation 유지·소비 여부, 화면 안내
- 반영 위치: API 계약과 `T-AIR-004`
- 금지: `AI_ROUTE_CONTENT_CHANGED` 또는 `INVALID_INPUT`으로 임의 매핑

## GATE-AIR-04 재평가 중 공개 지원 상태

- 차단 작업: [Q02 평가 지표·활성화](./release/Q02-metrics-activation.md)의 재평가 자동 전환 부분
- 확인된 공백: 재평가 시작부터 통과까지 기존 `ai_route_supported=true`의 처리 순서가 없습니다.
- 결정할 내용: 재평가 전 비활성화 여부, 실패 시 기존 버전 유지 여부, 콘텐츠 버전·지원 플래그 배포 순서
- 반영 위치: PRD 품질 기준, 콘텐츠 변환, 배포 가이드
- 최초 `ai-route-v2`는 평가 전 false, 전체 통과 뒤 true라는 기존 계약으로 구현할 수 있습니다.
- 금지: 게이트 해소 전 기존 공개 버전의 자동 비활성화·재활성화

## 해제 절차

1. 결정 값을 대응 정본에 먼저 반영합니다.
2. 차단 leaf 문서의 입력·테스트 기대값을 같은 내용으로 갱신합니다.
3. 색인의 차단 표시는 지우지 않고 해제된 정본 링크로 바꿔 결정 이력을 남깁니다.
4. Jira에는 결정 본문을 복사하지 않고 정본과 leaf 문서 링크만 연결합니다.
