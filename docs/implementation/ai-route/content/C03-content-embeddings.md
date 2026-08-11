# C03 페이지 분석 텍스트 embedding 생성

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 5 / 담당 A
- 선행: [C02 전체 검증](./C02-metadata-graph-validation.md),
  [F04 Embeddings adapter](../foundation/F04-embeddings-adapter.md)
- 후속: [C04 원자적 적재](./C04-atomic-import.md)

## 목표

검증된 `ai-route-v2` 페이지 분석 텍스트 중 후보 페이지의 것만 manifest의 model·dimensions로 변환하고,
DB 트랜잭션을 열기 전에 완전한 vector batch를 만듭니다.

## 정본 링크

- [콘텐츠 변환과 적재 경계](../../../content-conversion.md#변환과-적재-경계)
- [코퍼스 구조 메타데이터](../../../ai-route-content-corpus.md#구조-메타데이터)
- [배포 가이드 OpenAI 경계](../../../deployment.md#openai-데이터비용-제어)
- 필수 시나리오: [T-AIR-010·015·018](../../../test-strategy.md#5-필수-시나리오)

## 현재 구현 기준선

- 기존 content-import는 OpenAI를 호출하지 않습니다.
- F04가 content 전용 Embeddings Gateway를, C02가 검증된 분석 입력을 제공합니다.
- [ContentImportRunner](../../../../src/main/java/com/example/ilgeobolkka/contentimport/ContentImportRunner.java)는
  비웹 `content-import` profile에서 실행됩니다.

## 입력과 산출물

- 입력: `ValidatedAiRouteContent`, 검증된 OpenAI project·dataPolicy 설정, F04 Gateway
- 산출물: `AiRouteContentEmbeddingService`, `EmbeddedAiRouteContent`
- vector key: bookId·pageNumber·contentVersion; 값: model·dimensions·유한 실수 배열
- C04에 넘길 것: `aiRouteCandidatePage=true`인 모든 page가 정확히 한 vector를 가지고 `false`인 page에는
  vector가 없는 불변 batch

## 수정 허용 파일

- 새 `contentimport/embedding` service·결과 타입
- F04 Gateway와 C02 validator 파일은 수정하지 않음
- 새 `AiRouteContentEmbeddingServiceTest`

## 구현 조건

1. C02 결과 외의 문자열을 Gateway에 보내지 않고 공개 가이드·평가 purpose·정답을 섞지 않습니다.
2. `aiRouteCandidatePage=false`인 page는 목차 같은 구조 페이지이므로 Gateway에 보내지 않고 model·차원·
   vector를 비웁니다. 후보 여부는 페이지 내용이 아니라 이 값으로만 판정합니다.
3. manifest·환경 dataPolicyVersion 일치를 호출 직전에 다시 확인합니다.
4. bookId ASC, pageNumber ASC의 결정적 순서로 요청·결과를 연결합니다.
5. 한 page 실패, 개수·키·차원 불일치, NaN·Infinity가 있으면 전체 batch를 버립니다. 개수는 후보 page
   수와 비교하며, 후보 page에 vector가 없거나 후보가 아닌 page에 vector가 있으면 실패로 봅니다.
6. 부분 vector를 파일이나 DB에 publish하지 않고 Repository·transaction을 호출하지 않습니다.
7. API 키, 분석 text와 vector 원문을 로그·평가 결과에 남기지 않습니다.

## 테스트

- 여러 도서·페이지 결과의 결정적 순서와 정확한 model·dimensions 전달
- 중간 Gateway 실패, 결과 누락·중복·차원 오류에서 batch 미생성·DB 호출 0회
- manifest·환경 프로필 불일치에서 Gateway 호출 0회
- `aiRouteCandidatePage=false`인 page의 Gateway 호출 0회와 vector 미생성, 후보 page만 vector 보유
- 로그 capture의 키·분석 text·vector 비노출
- 명령: `./gradlew test --tests '*AiRouteContentEmbeddingServiceTest'`

## 제외 범위

- HTTP adapter 상세, 재시도 정책
- BookPage·prerequisite DB 저장
- 평가 실행과 `ai_route_supported=true`

## 완료 조건

- 정상 결과는 후보 page 전체의 vector를 가지거나 실패하며 부분 성공 상태가 없습니다.
- T-AIR-010·015·018의 적재 외부 호출 경계가 자동 검증됩니다.
- `./gradlew check`가 통과합니다.

## 인계

C04 담당자에게 `EmbeddedAiRouteContent`의 불변성과 vector key 규칙을 전달합니다. C04는 Gateway를 다시
호출하거나 vector를 재계산하지 않습니다.
