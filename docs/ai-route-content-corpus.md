# AI 경로 콘텐츠 코퍼스

이 문서는 [AI 잉크 경로 2차 MVP](./prd/ai-ink-route.md)를 검증하기 위한 시연 도서 재제작과 정답 평가
데이터의 정본입니다. 기존 PDF 변환·권리·공개 fixture 경계는
[콘텐츠 변환과 품질 검증](./content-conversion.md)과
[ADR-0013](./adr/content/0013-define-page-content-and-public-ai-fixture-boundary.md)을 따릅니다.
외부 전송 조건의 식별자와 비교 규칙은
[OpenAI 데이터 정책 프로필](./evidence/openai-data-policy/README.md)을 따릅니다.

## 범위

- 기존 도서 ID, 저자, 카테고리와 소개를 유지합니다.
- 제목은 과학·경제·철학·예술·기술 카테고리 50권에 한해 카테고리 톤에 맞게 갱신하며, 갱신한 제목은
  `ai-route-v2` manifest의 `books[].title`을 정본으로 따릅니다. 나머지 50권은 기존 제목을 그대로
  `title`에 적습니다.
- `initial-v1`의 `src/main/resources/demo/books.json`은 고치지 않습니다. 그 파일의 제목은 동결된
  `fixtures/content/pdfs/` PDF 본문에 함께 합성돼 있고 PDF를 다시 만드는 경로가 없어서, 제목만 바꾸면
  `ContentImportFullMySqlIntegrationTest`의 본문 대조와 `DemoBookWriter`의 기존 시연 DB 대조가 깨집니다.
- 소설 카테고리 10권은 기존 콘텐츠를 유지하고 AI 경로를 지원하지 않습니다.
- 소설을 제외한 90권의 본문과 PDF를 새로 제작합니다.
- 기존 3~5페이지 본문은 새 본문의 소재 참고용으로만 사용하며 페이지를 그대로 보존하지 않습니다.
- 새 코퍼스는 `ai-route-v2` 콘텐츠 버전이며 기존 운영 콘텐츠 수정 기능이 아니라 시연 fixture의 새
  버전입니다. `initial-v1` 100권·400페이지 fixture는 이전 버전의 재현 입력으로 보존합니다.
- `ai-route-v2` manifest는 새 비소설 90권과 이전 버전 PDF·SHA-256을 그대로 쓰는 소설 10권을 합친
  100권 전체를 기록합니다. 전체 페이지 수는 manifest의 도서별 페이지 수 합계로 계산합니다.
- `ai-route-v2`는 대여·소장·내역이 없는 새 시연 DB에 전체 적재합니다. 이전 시연 DB의 콘텐츠를 제자리에서
  바꾸지 않습니다. `initial-v1` manifest로 새 시연 DB를 만드는 되돌리기는 후보 DB가 HTTP 트래픽을 받기 전에만
  허용합니다. 한 번이라도 HTTP 트래픽을 받아 사용자 기록이 생긴 DB를 폐기하거나 이전 manifest의 새 DB로
  교체하는 롤백은 금지하며, 해당 콘텐츠 버전 마이그레이션은 2차 MVP 범위 밖입니다.

## 도서 제작 기준

- 새로 제작하는 비소설 90권은 각각 원본 PDF 기준 48~72페이지입니다.
- 최소 6개 장을 가지며 장과 절의 순서를 명시합니다.
- 한 책 안에 핵심 개념, 선수 개념, 적용 사례, 반례 또는 한계와 결론 역할의 페이지를 포함합니다.
- 같은 개념을 표현만 바꿔 반복하지 않고, 중복 후보를 평가할 수 있는 제한된 유사 페이지는 의도와
  근거를 메타데이터에 표시합니다.
- 표·수식·도표·삽화처럼 배치 보존이 필요한 이미지 페이지를 포함합니다.
- 모든 페이지는 화면용 `TEXT` 또는 `IMAGE` 콘텐츠와 별개로 비공개 AI 분석 텍스트를 가집니다.
- 이미지 페이지의 분석 텍스트는 원본 추출 또는 OCR과 사람 검수로 작성하며 API에 노출하지 않습니다.
- 실제 작품을 모방하거나 인용하지 않는 새 AI 시연 콘텐츠만 사용합니다.
- 파일별 권리 상태와 개인정보 부재를 확인한 뒤 Git fixture로 반입합니다.

## 구조 메타데이터

`ai-route-v2` 콘텐츠 입력은 `fixtures/content/ai-route-v2/manifest.json`, PDF는 그 파일 기준 상대 경로인
`fixtures/content/ai-route-v2/pdfs/`에 둡니다. manifest의 최소 필드는 다음과 같습니다.

| 위치 | 필수 필드 |
| --- | --- |
| 최상위 | `contentVersion`, `dataPolicyVersion`, `embeddingModel`, `embeddingDimensions`, `books[]` |
| `books[]` | `bookId`, `title`, `pdfPath`, `pdfSha256`, `totalPageCount`, `aiRouteCandidate`, `aiExternalTransferAllowed`, `pages[]` |
| `aiRouteCandidate=true`인 `books[].pages[]` | `pageNumber`, `chapter`, `section`, `primaryConcepts[]`, `secondaryConcepts[]`, `contentRole`, `aiRouteCandidatePage`, `aiAnalysisText`, `aiAnalysisInputSha256`, `aiPublicGuideTopic`, `estimatedReadingSeconds`, `prerequisitePageNumbers[]`, `duplicateGroupKeys[]` |

재제작한 비소설 90권은 `aiRouteCandidate=true`이고 모든 페이지 메타데이터를 가지며, 소설 10권은
`aiRouteCandidate=false`와 빈 `pages[]`를 사용합니다. 지원 후보의 `primaryConcepts[]`는 하나 이상이고 나머지
목록 필드는 항목이 없으면 빈 배열을 사용합니다.

`contentRole`은 `PREREQUISITE`, `CORE`, `EXAMPLE`, `COUNTERPOINT`, `CONCLUSION`, `FRONT_MATTER` 중 하나인
콘텐츠 제작·평가용 분류이며 생성 결과의 경로별 `role` 정답으로 사용하지 않습니다.

`FRONT_MATTER`는 목차처럼 본문 설명을 담지 않는 구조 페이지를 위한 값입니다. 도서 제작 기준이 요구하는
핵심 개념·선수 개념·적용 사례·반례·결론 역할의 존재 여부를 셀 때 `FRONT_MATTER` 페이지는 세지 않습니다.
구조 페이지도 페이지 번호의 연속성과 전체 페이지 수에는 포함하며, 다른 페이지의 선수 관계 대상으로
지정하지 않습니다.

`aiRouteCandidatePage`는 도서 단위 `aiRouteCandidate`와 달리 페이지 하나를 후보 집합에 넣을지 정하는
값입니다. 지원 도서 안에서도 구조 페이지는 후보가 아니므로 `contentRole=FRONT_MATTER`인 페이지는 항상
`false`여야 하고, 나머지 역할의 페이지는 `true`여야 합니다. `false`인 페이지는 다른 페이지의
`prerequisitePageNumbers`나 평가 데이터의 `referencePageNumbers`·`allowedAlternativePageNumbers`에 나올 수
없습니다. 도달할 수 없는 선수 관계와 정답을 적재 전에 막기 위한 제약입니다.

`false`인 페이지에는 Embeddings API를 호출하지 않고 임베딩 모델·차원·벡터를 비웁니다. 후보 검색은 이
값이 `true`인 페이지만 고른 뒤 그 집합 안에서 벡터 유효성을 검증하며, 벡터가 있는 페이지만 고르는
방식으로 대신하지 않습니다. 임베딩이 누락된 페이지를 조용히 건너뛰지 않고 실패로 드러내기 위해서입니다.

`chapter`, `section`, `primaryConcepts`, `secondaryConcepts`, `contentRole`, `aiAnalysisInputSha256`,
도서 단위 `aiRouteCandidate`는 manifest에서 제작 완전성과 평가 연결을 검증하는 비영속 메타데이터입니다.
런타임 후보 생성 입력이나 공개 API에 포함하지 않습니다. 이름이 비슷한 페이지 단위
`aiRouteCandidatePage`는 여기 해당하지 않으며 `book_page`에 저장하는 영속 값입니다.

적재 시 `title`, `contentVersion`, `dataPolicyVersion`, `aiExternalTransferAllowed`는 `book`의 대응 필드로,
`aiRouteCandidatePage`, `aiAnalysisText`, `aiPublicGuideTopic`, `estimatedReadingSeconds`, 임베딩
모델·차원·벡터와 `duplicateGroupKeys`는 `book_page`의 대응 필드로 저장합니다.
`prerequisitePageNumbers`는 현재 페이지를 의존 페이지로 하는 `ai_route_prerequisite` 행으로
저장합니다. 구체적인 물리 필드는
[ERD의 AI 잉크 경로 목표 모델](./erd.md#ai-잉크-경로-2차-mvp-목표-모델-구현-전)을 따릅니다.

manifest는 AI 경로 지원 후보를 정의할 뿐 `ai_route_supported=true`를 선언하지 않습니다. 적재 직후에는
`false`로 두고 아래 품질 평가를 통과한 비소설 도서만 `true`로 전환합니다.

`ai-route-v2`의 `dataPolicyVersion`은 `OPENAI_DEFAULT_RETENTION_V1`이며 지원 후보 도서의
`aiExternalTransferAllowed`는 `true`여야 합니다. 개별 페이지 하나라도 외부 전송 권리를 확인하지 못하면
도서 전체를 `false`로 판정합니다. 구조 메타데이터 중 화면 공개가 합의되지 않은 값은 외부 API에 제공하지
않습니다.

`ai-route-v2`의 `embeddingModel`은
[ADR-0014](./adr/application/0014-use-openai-and-mysql-for-ai-route-generation.md)가 정한
`text-embedding-3-small`이고 `embeddingDimensions`는 그 모델의 기본 차원인 `1536`입니다. 한 콘텐츠 버전의
모든 페이지 vector를 이 모델·차원으로 생성하며, 목적 vector와 model·dimensions가 다르면 후보 검색을
시작하지 않고 실패합니다. 값을 바꾸면 해당 콘텐츠 버전의 페이지 임베딩을 전부 다시 생성해야 하므로
기존 콘텐츠 버전에서 바꾸지 않고 새 콘텐츠 버전에서만 변경합니다.

선수 관계는 같은 도서·콘텐츠 버전 안에서 `선수 페이지 -> 의존 페이지` 방향 그래프로 해석합니다. 모든
참조 페이지가 존재해야 하고 다른 도서·콘텐츠 버전을 가리킬 수 없으며, 자기 참조와 방향 순환이 없어야
합니다. 적재 전 위상 정렬로 전체 페이지를 방문할 수 있는지 검증하고 하나라도 실패하면 해당 콘텐츠 버전의
외부 호출과 DB 변경을 시작하지 않습니다.

## 품질 평가 데이터

각 지원 도서에는 대표 독서 목적 1개를 만들며 90권 전체에 90개를 둡니다. 이 데이터는 저장소에서 버전
관리하고 프롬프트·후보 정책 회귀와 출시 전 품질 확인에 함께 사용합니다. 독립적인 미관측 평가셋이 아니므로
결과를 일반 사용자 전체의 성과로 확대 해석하지 않습니다.

평가 입력은 `fixtures/content/ai-route-v2/evaluation.json`에 두며 최소 필드는 다음과 같습니다.

| 위치 | 필수 필드 |
| --- | --- |
| 최상위 | `contentVersion`, `cases[]` |
| `cases[]` 식별·입력 | `caseId`, `bookId`, `purpose`, `owned`, `maxAdditionalInk`, `depth`, `activeRentalPageNumbers[]` |
| `cases[]` 정답 | `requiredConcepts[]`, `helpfulConcepts[]`, `requiredPrerequisites[]`, `irrelevantPageNumbers[]`, `duplicatePageGroups[]`, `referencePageNumbers[]`, `allowedAlternativePageNumbers[]` |

`caseId`는 파일 안에서 고유합니다. 비소장 사례는 `owned=false`, `maxAdditionalInk`에 0·5·10·15 중 하나,
`depth=null`을 사용하고, 소장 사례는 `owned=true`, `maxAdditionalInk=null`, `depth`에 `QUICK`, `BALANCED`,
`DEEP` 중 하나를 사용합니다. `requiredConcepts[]`는 하나 이상이고 나머지 목록 필드는 항목이 없으면 빈 배열을
사용합니다. `requiredPrerequisites[]`의 각 항목은 `beforePageNumber`와 `afterPageNumber`를 가집니다.
`duplicatePageGroups[]`의 각 항목은 중복으로 판정할 페이지 번호 배열입니다.

정답 필드는 후보 검색이나 경로 구성 입력에 사용하지 않습니다. 독서 목적만 일반 사용자 입력과 같은
경계로 전달하며 정확한 페이지 번호 일치보다 개념 충족 여부를 우선 판정합니다.

### 품질 평가 조건

- 대표 목적 90개를 `bookId ASC`로 실행하며 일곱 평가 시나리오가 모두 포함되어야 합니다. 예산 0은 정답
  데이터가 지정한 활성 대여 페이지를 입력하고, 나머지 비소장 시나리오는 활성 대여가 없습니다.
- 비웹 평가는 운영과 같은 후보 검색·경로 구성·서버 출력 검증 코드를 호출하되 사용자 계정, 잉크 원장,
  대여·소장·결제와 저장 경로를 만들지 않습니다. 예산과 권한 상태는 평가 입력으로 직접 전달합니다.
- 필수 개념 포함률은 전체 필수 개념 수를 분모로 사용합니다. 무관·중복 비율은 전체 추천 페이지 수를
  분모로 하고 무관한 페이지와 각 중복 그룹의 첫 페이지를 제외한 추가 페이지의 합집합을 분자로 사용합니다.
- 선수 순서 위반률은 정답 데이터의 전체 필수 선수 관계를 분모로 사용합니다. 필수 선수 누락이나 의존
  페이지보다 뒤에 배치한 관계를 위반으로 셉니다.
- 지정 검수자 한 명이 표시 경로를 `유용` 또는 `유용하지 않음`으로 판정합니다. OpenAI 요청·응답 원문과
  비공개 분석 텍스트는 검수 결과에 기록하지 않습니다.
- 각 요청의 서버 처리 시간은 비웹 평가 진입부터 목적 임베딩, 경로 구성, 서버 검증과 허용된 한 번의 검증
  재시도를 거쳐 최종 결과를 확정할 때까지 측정합니다.
- 평가 결과에는 콘텐츠 manifest의 SHA-256과 Git 리비전, 평가 데이터의 Git 리비전, 실행 시각,
  임베딩·경로 모델, 후보 정책·prompt·schema 버전, 자동 지표와 사람 판정을 기록합니다. 기존 결과의
  재사용 거부 판정은 manifest는 SHA-256, 평가 데이터는 Git 리비전으로 비교합니다.
- 판정과 재평가 대상은
  [AI 잉크 경로 PRD의 품질과 출시 기준](./prd/ai-ink-route.md#품질과-출시-기준)을, 재평가 환경과 지원
  활성화 순서는 [재평가와 지원 활성화 순서](./prd/ai-ink-route.md#재평가와-지원-활성화-순서)를 따릅니다.
  일반 코드 변경이나 공급자 처리 티어 차이만으로 기존 평가 결과를 자동 폐기하지 않습니다.

## 생성·검수 순서

1. 기존 메타데이터에서 책의 주제와 독자 수준을 확정합니다.
2. 비소설 도서별 48~72페이지를 충족하는 장·절, 개념 지도와 선수 관계를 설계합니다.
3. 페이지별 역할·공개 가이드 주제와 도서별 대표 목적·정답을 작성합니다.
4. 본문과 이미지 원고를 생성하고 원본 PDF를 조립합니다.
5. 기존 콘텐츠 배치로 `TEXT`·`IMAGE` 페이지를 변환합니다.
6. 모든 페이지의 AI 분석 텍스트·임베딩 입력과 공개 가이드 주제를 서로 분리해 검수합니다.
7. PDF 페이지 수·번호, 변환 콘텐츠, 분석 텍스트와 평가 데이터의 연결을 검증합니다.
8. 비웹 평가로 대표 목적 90개를 실행하고 PRD의 품질 기준을 통과한 비소설 콘텐츠만 AI 경로 지원으로
   활성화합니다.

## 완료 조건

- 소설을 제외한 90권이 모두 48~72페이지이고 최소 6개 장을 가집니다.
- 각 도서의 PDF 페이지 수, 변환 페이지 수와 분석 페이지 수가 일치합니다.
- 모든 이미지 페이지에 검수한 분석 텍스트가 있습니다.
- 모든 지원 페이지에 원문·결론·수치·사례 결과가 없는 검수된 공개 가이드 주제가 있습니다.
- 모든 페이지에 선수 관계·중복 그룹 소속 목록 필드가 하나씩 있고, 선수 관계는 같은 도서·콘텐츠 버전의
  존재하는 페이지만 가리키며 자기 참조와 방향 순환이 없습니다.
- 각 지원 도서에 대표 목적 1개와 개념 중심 정답 데이터가 있고 일곱 평가 시나리오가 모두 포함됩니다.
- 정답 평가 데이터가 런타임 추천 입력과 분리됩니다.
- 공개 fixture 권리·개인정보 검사를 통과합니다.
- 외부 전송 권리나 `dataPolicyVersion`이 누락·거부됐거나 배포 환경의 지원 프로필과 다른 표본은
  Embeddings API 호출과 지원 활성화 전에 거부됩니다.
- 대표 목적 90개가 [AI 잉크 경로 PRD의 품질·출시 기준](./prd/ai-ink-route.md#품질과-출시-기준)을
  통과하고 결과에 재현에 필요한 manifest SHA-256·Git 리비전·임베딩 모델·경로 모델·후보 정책·prompt·schema 버전이 기록됩니다.
