# AI 잉크 경로 구현 전 결정 게이트

[구현 작업 색인](./README.md)으로 돌아갑니다. 현재 정본과 코드만으로 한 가지 구현을 결정할 수 없는
사항입니다. 구현자는 아래 값·오류·전환 순서를 추측하지 않습니다.

## GATE-AIR-01 초기 콘텐츠 버전

- 차단 작업: [F01 스키마 migration](./foundation/F01-schema-migration.md)
- 확인된 불일치: [ERD](../../erd.md#기존-테이블-확장)는 `book.content_version NOT NULL`을 요구하지만
  [현재 manifest](../../../fixtures/content/manifest.json)에는 `contentVersion`이 없습니다.
- 결정할 내용: 초기 manifest 버전 식별자와 기존 `book` 행 backfill 규칙
- 반영 위치: [콘텐츠 변환 정본](../../content-conversion.md), 초기 manifest, F01 테스트 입력
- 금지: `initial-v1` 같은 값 추측, nullable 임시 컬럼, V1 수정

## GATE-AIR-02 후보·prompt 정책 v1

- 차단 작업: [F05 Responses](./foundation/F05-responses-adapter.md),
  [G02 후보 검색](./generation/G02-candidate-search.md),
  [G03 출력 검증](./generation/G03-output-validation.md)
- 확인된 공백: [경로 생성 정책](../../prd/ai-ink-route.md#경로-생성-정책)은 cosine 후보와 정책 버전 기록을
  요구하지만 후보 `top-K` 또는 최소 유사도, 동점 정렬, prompt·schema 버전 저장 위치를 정하지 않습니다.
- 결정할 내용: 최초 후보 범위·최소 관련성·동점 기준, prompt와 strict schema의 저장 위치·버전 계산,
  검증 재시도의 동일 후보·정책 사용 여부
- 반영 위치: 버전 고정 구현 artifact와 [평가 결과](../../prd/ai-ink-route.md#품질과-출시-기준)
- 금지: 평가 정답을 후보 입력으로 사용하거나 구현자 취향으로 threshold 선택

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
