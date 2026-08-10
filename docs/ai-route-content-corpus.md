# AI 경로 콘텐츠 코퍼스

이 문서는 [AI 잉크 경로 2차 MVP](./prd/ai-ink-route.md)를 검증하기 위한 시연 도서 재제작과 정답 평가
데이터의 정본입니다. 기존 PDF 변환·권리·공개 fixture 경계는
[콘텐츠 변환과 품질 검증](./content-conversion.md)과 [ADR-0016](./adr/content/0016-publish-korean-original-ebook-content-for-ai-fixtures.md)을 따릅니다.
외부 전송 조건의 식별자와 비교 규칙은
[OpenAI 데이터 정책 프로필](./evidence/openai-data-policy/README.md)을 따릅니다.

## 범위

- 기존 도서 ID, 제목, 저자, 카테고리와 소개를 유지합니다.
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
  모든 도서를 같은 길이로 만들지 않고 `contentVersion`, `bookId`, 기존 제목의 결정적 seed로 범위 안에서 길이를 다르게 배정합니다.
- 최소 6개 장을 가지며 장과 절의 순서를 명시합니다.
- 한 책 안에 핵심 개념, 선수 개념, 적용 사례, 관련 맥락과 결론 역할의 페이지를 포함합니다. 장 시작에는
  독자가 읽을 관점을 잡도록 짧은 도입 문장 상자를 둘 수 있지만, 장 끝에는 독립적인 성찰 질문 상자를 두지
  않습니다. 반대 관점 전용 페이지를 별도로 만들지는 않고, 본문에서 실제 반례를 다룬 페이지에
  `COUNTERPOINT` 역할을 표시합니다.
- 핵심 개념과 평가 대상 내용은 도서별로 구분합니다. 합성 코퍼스의 일반적인 연결 문장은 일부 반복될 수 있으며, 평가용 중복 후보는 의도와 근거를 메타데이터에 표시합니다.
- 사진·도표·삽화 등 도서 내용을 이해하는 데 필요한 시각 자료는 내용에 맞는 형식을 선택해 배치 보존이 필요한 이미지 페이지로 포함합니다.
- 모든 페이지는 화면용 `TEXT` 또는 `IMAGE` 콘텐츠와 별개로 비공개 AI 분석 텍스트를 가집니다. 검색 대상
  본문의 분석 텍스트는 PDF의 실제 페이지 내용을 바탕으로 작성하며, 공통 슬롯에 개념어만 치환하지 않습니다.
- 이미지 페이지의 분석 텍스트는 원본 추출 또는 OCR과 사람 검수로 작성하며 API에 노출하지 않습니다.
- 비소설은 기존 제목·저자·카테고리·소개에 맞춘 한국어 창작 전자책으로 편집합니다. 표지·판권·머리말·목차,
  최소 6개 장의 이어지는 본문, 사례·관련 맥락·맺음말·참고 자료를 포함합니다.
- 독자용 본문에는 제작 과정이나 페이지 역할을 설명하는 반복 문장을 넣지 않습니다. 화면 본문과 AI 분석
  텍스트는 분리하고 영어는 URL·원전 고유 서지정보처럼 추적에 필요한 곳으로 제한합니다.
- 장 끝 질문 상자를 제거한 일반 본문은 제목 아래와 쪽번호 위의 안전 영역 안에서 세로 가운데에 배치하고,
  위·아래 여백을 검사해 마지막 문장이나 줄이 가려지지 않도록 합니다.
- 권리 검증 공개 원전은 책별 참고 자료로만 추적하고 원문·번역문·스캔·삽화를 PDF 본문에 직접 싣지 않습니다.
  배치 보존 이미지 페이지에는 새로 생성한 사진 또는 새로 작성한 한국어 도표·삽화 등 내용에 맞는 시각 자료를 사용합니다.
- 파일별 권리 상태와 개인정보 부재를 확인한 뒤 Git fixture로 반입합니다.

## 구조 메타데이터

`ai-route-v2` 콘텐츠 입력은 `fixtures/content/ai-route-v2/manifest.json`, PDF는 그 파일 기준 상대 경로인
`fixtures/content/ai-route-v2/pdfs/`에 둡니다. manifest의 최소 필드는 다음과 같습니다.

| 위치 | 필수 필드 |
| --- | --- |
| 최상위 | `contentVersion`, `dataPolicyVersion`, `embeddingModel`, `embeddingDimensions`, `books[]` |
| `books[]` | `bookId`, `pdfPath`, `pdfSha256`, `totalPageCount`, `aiRouteCandidate`, `aiExternalTransferAllowed`, `pages[]` |
| `aiRouteCandidate=true`인 `books[].pages[]` | `pageNumber`, `chapter`, `section`, `primaryConcepts[]`, `secondaryConcepts[]`, `contentRole`, `aiRouteSearchEligible`, `aiAnalysisText`, `aiAnalysisInputSha256`, `aiPublicGuideTopic`, `estimatedReadingSeconds`, `prerequisitePageNumbers[]`, `duplicateGroupKeys[]` |

재제작한 비소설 90권은 `aiRouteCandidate=true`이고 모든 페이지 메타데이터를 가지며, 소설 10권은
`aiRouteCandidate=false`와 빈 `pages[]`를 사용합니다. 지원 후보의 `primaryConcepts[]`는 하나 이상이고 나머지
목록 필드는 항목이 없으면 빈 배열을 사용합니다.

`contentRole`은 `PREREQUISITE`, `CORE`, `EXAMPLE`, `COUNTERPOINT`, `CONCLUSION` 중 하나인 콘텐츠 제작·평가용
분류이며 생성 결과의 경로별 `role` 정답으로 사용하지 않습니다. 각 비소설은 실제 반례 문장이 있는 본문
페이지 하나 이상을 `COUNTERPOINT`로 가집니다. `aiRouteSearchEligible`은 장 본문에서만 `true`이고
표지·판권·머리말·차례·맺음말·참고 자료에서는 `false`입니다. `false`인 페이지는 C03에서 임베딩을 만들지
않고 G02 후보에 포함하지 않습니다. `chapter`, `section`, `primaryConcepts`, `secondaryConcepts`,
`contentRole`, `aiRouteSearchEligible`, `aiAnalysisInputSha256`, `aiRouteCandidate`는 manifest에서 제작 완전성과
평가 연결을 검증하는 비영속 메타데이터입니다. 공개 API에 포함하지 않습니다.

적재 시 `contentVersion`, `dataPolicyVersion`, `aiExternalTransferAllowed`는 `book`의 대응 필드로,
`aiAnalysisText`, `aiPublicGuideTopic`, `estimatedReadingSeconds`와 `duplicateGroupKeys`는 `book_page`의 대응
필드로 저장합니다. 임베딩 모델·차원·벡터는 `aiRouteSearchEligible=true`인 페이지만 저장하고 나머지는
`NULL`로 둡니다. `prerequisitePageNumbers`는 현재 페이지를 의존
페이지로 하는 `ai_route_prerequisite` 행으로 저장합니다. 구체적인 물리 필드는
[ERD의 AI 잉크 경로 목표 모델](./erd.md#ai-잉크-경로-2차-mvp-목표-모델-구현-전)을 따릅니다.

manifest는 AI 경로 지원 후보를 정의할 뿐 `ai_route_supported=true`를 선언하지 않습니다. 적재 직후에는
`false`로 두고 아래 품질 평가를 통과한 비소설 도서만 `true`로 전환합니다.

`ai-route-v2`의 `dataPolicyVersion`은 `OPENAI_DEFAULT_RETENTION_V1`이며 지원 후보 도서의
`aiExternalTransferAllowed`는 `true`여야 합니다. 개별 페이지 하나라도 외부 전송 권리를 확인하지 못하면
도서 전체를 `false`로 판정합니다. 구조 메타데이터 중 화면 공개가 합의되지 않은 값은 외부 API에 제공하지
않습니다.

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

대표 목적은 도서 제목이나 `requiredConcepts[]`의 유의미한 어휘를 축자 포함하지 않습니다. 각 도서의
`referencePageNumbers[]`는 서로 다른 비연속 위치를 사용하고 다섯 `contentRole`을 모두 포함합니다.
`aiRouteSearchEligible=false`인 모든 페이지는 해당 사례의 `irrelevantPageNumbers[]`에 넣어 검색 제외 정책이
평가에서도 오류로 집계되게 합니다.

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
- 검색 대상 분석 텍스트는 6슬롯 고정 형식을 사용하지 않으며, 같은 30자 이상 문장의 최대 빈도는 5회,
  20페이지 이상에서 반복되는 5단어 구문의 페이지별 비율 중앙값은 10% 이하입니다.
- 모든 지원 페이지에 원문·결론·수치·사례 결과가 없는 검수된 공개 가이드 주제가 있습니다.
- 모든 페이지에 선수 관계·중복 그룹 소속 목록 필드가 하나씩 있고, 선수 관계는 같은 도서·콘텐츠 버전의
  존재하는 페이지만 가리키며 자기 참조와 방향 순환이 없습니다.
- 각 지원 도서에 대표 목적 1개와 개념 중심 정답 데이터가 있고 일곱 평가 시나리오가 모두 포함됩니다.
- 대표 목적에 도서 제목·정답 개념 어휘 누출이 없고, 90개 기준 페이지 집합이 모두 다른 비연속 위치이며
  다섯 `contentRole`을 평가합니다.
- 정답 평가 데이터가 런타임 추천 입력과 분리됩니다.
- 공개 fixture 권리·개인정보 검사를 통과합니다.
- 외부 전송 권리나 `dataPolicyVersion`이 누락·거부됐거나 배포 환경의 지원 프로필과 다른 표본은
  Embeddings API 호출과 지원 활성화 전에 거부됩니다.
- 대표 목적 90개가 [AI 잉크 경로 PRD의 품질·출시 기준](./prd/ai-ink-route.md#품질과-출시-기준)을
  통과하고 결과에 재현에 필요한 manifest SHA-256·Git 리비전·임베딩 모델·경로 모델·후보 정책·prompt·schema 버전이 기록됩니다.
