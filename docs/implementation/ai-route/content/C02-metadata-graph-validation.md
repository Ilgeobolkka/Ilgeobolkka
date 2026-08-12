# C02 AI 메타데이터·선수 그래프 검증

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 4 / 담당 A
- 선행: [C01 manifest 파싱](./C01-manifest-parsing.md)
- 후속: [C03 콘텐츠 embedding](./C03-content-embeddings.md), [G02 후보 검색](../generation/G02-candidate-search.md)

## 목표

C01의 manifest 전체를 검증해 외부 전송 권리·파일 무결성·페이지 메타데이터·선수 DAG가 모두 유효한
경우에만 Embeddings 단계로 넘깁니다.

## 정본 링크

- [코퍼스 도서 제작 기준](../../../ai-route-content-corpus.md#도서-제작-기준)
- [코퍼스 구조 메타데이터](../../../ai-route-content-corpus.md#구조-메타데이터)
- [콘텐츠 변환과 적재 경계](../../../content-conversion.md#변환과-적재-경계)
- [경로 생성 정책의 그래프 검증](../../../prd/ai-ink-route.md#경로-생성-정책)
- 필수 시나리오: [T-AIR-011·015](../../../test-strategy.md#5-필수-시나리오)

## 현재 구현 기준선

- 이 절은 착수 시점 기록입니다. 아래 산출물은 그 뒤에 구현했습니다 —
  [AiRouteContentValidator](../../../../src/main/java/com/example/ilgeobolkka/contentimport/validation/AiRouteContentValidator.java),
  [PrerequisiteGraphValidator](../../../../src/main/java/com/example/ilgeobolkka/contentimport/validation/PrerequisiteGraphValidator.java),
  [ValidatedAiRouteContent](../../../../src/main/java/com/example/ilgeobolkka/contentimport/validation/ValidatedAiRouteContent.java).
- [ContentBatch](../../../../src/main/java/com/example/ilgeobolkka/contentimport/ContentBatch.java)은
  AI 선수 관계·중복 그룹·분석 입력을 표현하지 않습니다. 적재 연결은 C04 몫입니다.

## 입력과 산출물

- 입력: C01의 `AiRouteContentManifest`·`AiRouteEvaluationDataset`, 실제 fixture root, 환경 dataPolicyVersion
- 산출물: `ValidatedAiRouteContent`와 도서별 위상 정렬된 prerequisite edge·검증된 page metadata
- 산출물: `AiRouteContentValidator`, `PrerequisiteGraphValidator`
- C03에 넘길 것: 권리·정책·SHA·DAG 검증을 통과한 분석 텍스트 입력 목록과 페이지별
  `aiRouteCandidatePage`. C03은 이 값으로 Gateway에 보낼 페이지를 고르므로 값을 다시 계산하거나
  역할 이름으로 추론하지 않습니다.

## 수정 허용 파일

- 새 `contentimport/validation`의 AI validator 파일
- C01 DTO는 읽기만 하고 변경하지 않음
- 새 `AiRouteContentValidatorTest`, `PrerequisiteGraphValidatorTest`

## 구현 조건

1. manifest에 든 도서만 검사하고 권수는 세지 않습니다. `aiRouteCandidate=true`인 도서는 48~72페이지·최소
   장 수, `false`인 소설은 빈 `pages[]`라는 코퍼스 계약을 검사합니다. 10권짜리 부분 집합도 100권 완성본과
   같은 코드로 통과해야 합니다.
2. PDF·분석 입력 파일과 SHA-256, 전체 페이지 번호의 1부터 연속·중복 없음과 page count를 검사합니다.
3. 지원 페이지의 분석 텍스트, 공개 가이드 주제, 예상 시간, embedding model·dimensions, 선수·중복 목록
   필드를 검사합니다. 지원 도서의 페이지는 `contentRole=FRONT_MATTER`인 것만
   `aiRouteCandidatePage=false`이고 나머지 역할은 모두 `true`인지 확인합니다. 한쪽만 맞으면 실패입니다.
   후보 여부는 역할 이름이나 내용이 아니라 이 두 필드의 일치로만 판정합니다.
4. `aiRouteCandidatePage=false`인 페이지는 `duplicateGroupKeys`가 비어 있어야 하며, 다른 페이지의
   `prerequisitePageNumbers`와 evaluation의 `activeRentalPageNumbers`·`referencePageNumbers`·
   `allowedAlternativePageNumbers`·`irrelevantPageNumbers`·`duplicatePageGroups` 어디에도 나올 수
   없습니다. 후보가 아닌 페이지는 임베딩이 없어 경로 비용·추천·채점 대상이 될 수 없습니다.
5. 선수 edge는 같은 book·contentVersion의 존재 page만 가리키며 자기 참조·중복 edge를 거부합니다.
6. `선수 -> 의존` 방향으로 위상 정렬해 모든 노드를 방문하지 못하면 순환으로 전체 실패합니다.
7. `aiExternalTransferAllowed=false`, dataPolicyVersion 누락·환경 불일치는 Gateway 호출 전에 전체 실패합니다.
8. 지원 도서마다 evaluation case가 정확히 하나인지 확인합니다. 필수 개념은 후보 페이지의
   `primaryConcepts`, 도움 개념은 후보 페이지의 `primaryConcepts` 또는 `secondaryConcepts`에 있어야 하며,
   정답 경로가 필수 개념을 실제로 덮어야 합니다. 정답 경로는 선수 폐쇄이고 `requiredPrerequisites`는 그
   경로 안의 실제 선수 간선 전체여야 합니다. 중복 그룹은 manifest의 그룹 전체와 일치하고 정답 경로에서는
   그룹마다 최대 한 페이지만 고릅니다. 정답·대체 페이지는 각각 무관 페이지와 겹칠 수 없습니다. 이 정답
   데이터는 검증 결과의 runtime candidate 입력에는 포함하지 않습니다.

## 테스트

- 정상 DAG의 위상 순서와 root 빈 prerequisite 허용
- 지원 도서 10권짜리 부분 집합 manifest가 권수 때문에 실패하지 않음
- 미존재·다른 book·자기 참조·duplicate edge·2개 이상 cycle 실패
- 파일 누락·SHA 불일치·페이지 공백·지원 metadata 누락·권리/프로필 불일치 실패
- `FRONT_MATTER`인데 `aiRouteCandidatePage=true`, 반대로 다른 역할인데 `false`인 페이지 각각 실패
- 비후보 페이지를 선수·정답 경로·대체 페이지·무관 페이지에 넣은 입력 각각 실패
- 비후보 페이지를 활성 대여·중복 그룹에 넣은 입력 각각 실패
- 필수·도움 개념이 후보 페이지의 허용된 개념 필드에 없거나 정답 경로가 필수 개념을 덮지 않으면 실패
- 정답·대체와 무관 페이지의 교집합, manifest와 다른 중복 그룹, 불완전한 선수 폐쇄·간선 목록 실패
- 정본 `fixtures/content/ai-route-v2/`가 그대로 통과 (인라인 JSON만 쓰면 정본과 코드가 갈려도
  드러나지 않는다 — C01이 실제로 그렇게 어긋난 적이 있다)
- validator 실패 시 Gateway·DB fake 호출 0회 확인
- 명령: `./gradlew test --tests '*AiRouteContentValidatorTest' --tests '*PrerequisiteGraphValidatorTest'`

## 제외 범위

- vector 생성·검증, PDF TEXT/IMAGE 변환
- DB Entity 변환과 지원 활성화
- runtime 후보 top-K·목적 embedding
- [선수 밀도 상한](../../../ai-route-content-corpus.md#도서-제작-기준)과 정답 경로의 예산·깊이 상한.
  둘 다 도서를 만들 때 지키는 제작 기준이고 코퍼스 도구
  ([`tools/`](../../../evidence/ai-route-corpus/tools/))가 fixture를 커밋하기 전에 강제합니다.
  임계값이 조정 가능한 값이라 적재 시점의 불변식으로 두지 않습니다.

## 완료 조건

- 하나의 `ValidatedAiRouteContent`만 다음 단계의 입력이 됩니다.
- 잘못된 graph·권리·정책 표본은 외부 호출과 DB 변경 전 실패합니다.
- T-AIR-011·015 사전 검증 부분과 `./gradlew check`가 통과합니다.

## 인계

C03 담당자에게 검증 결과의 page 순서·model·dimensions·analysis input 접근 API를 전달합니다. G02 담당자에게
선수 edge 방향과 동일 version 불변식을 전달합니다.
