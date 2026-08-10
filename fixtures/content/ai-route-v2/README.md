# AI 경로 콘텐츠 코퍼스 `ai-route-v2`

[AI 경로 콘텐츠 코퍼스 정본](../../../docs/ai-route-content-corpus.md)의 `ai-route-v2` 입력이다.
이 디렉터리의 PDF와 본문은 실존 작품을 옮기지 않은 시연 전용 가상 콘텐츠다.

## 현재 상태: 파일럿 (미완성)

**정본은 100권 전체를 요구하지만 현재 1권만 들어 있다.** SCRUM-485의 파일럿 단계이며 스키마와
정답 데이터 구조를 먼저 확정하기 위한 것이다. 이 상태의 `manifest.json`은 C02 validator의 코퍼스
완전성 검사를 통과하지 못한다.

| 항목 | 정본 요구 | 현재 |
| --- | --- | --- |
| 도서 수 | 100권 (비소설 90 + 소설 10) | **1권** (book-047) |
| 평가 케이스 | 90건, 일곱 시나리오 전부 | **1건**, 비소장·예산 10 하나 |
| 소설 10권 | 기존 PDF·SHA-256 유지, `aiRouteCandidate=false` | **미포함** |

확장 시 비소설 89권을 `books[]`에, 평가 89건을 `cases[]`에 추가하고, 소설 10권은
`fixtures/content/manifest.json`의 PDF 경로·SHA-256·페이지 수를 그대로 옮겨 `aiRouteCandidate=false`와
빈 `pages[]`로 기록한다.

## 구성

| 경로 | 내용 |
| --- | --- |
| `manifest.json` | 최상위 4필드 + 도서별 페이지 메타데이터 13필드 |
| `evaluation.json` | 도서별 대표 목적과 정답 데이터 |
| `pdfs/book-047.pdf` | 원본 PDF 57페이지. `pdfPath`는 `manifest.json` 기준 상대 경로 |

본문 원고와 이미지 페이지 명세는 fixture가 아니라 검수 근거로
[`docs/evidence/ai-route-corpus/`](../../../docs/evidence/ai-route-corpus/)에 둔다. 구조 설계 근거는
같은 디렉터리의 `book-047-design.md`에 있다.

`aiAnalysisInputSha256`은 같은 페이지 `aiAnalysisText`의 UTF-8 바이트에 대한 SHA-256(소문자 hex)이다.
검수를 통과한 분석 텍스트와 Embeddings API 입력이 같음을 확인하는 값이다.

## 수록 도서

| `bookId` | 제목 | 카테고리 | 페이지 | 장 | 이미지 페이지 |
| --- | --- | --- | --- | --- | --- |
| 47 | 가격은 어디에서 오는가 | 경제 | 57 | 7 | 8, 13, 21, 27, 39, 46 |

1페이지는 장 7개와 절 34개를 시작 페이지와 함께 싣는 목차이고 본문은 2페이지부터다. 목차의
페이지 번호는 원고에 직접 적지 않고 최종 배열에서 산출하므로 본문 분량이 바뀌어도 어긋나지 않는다.

본문은 데스크톱 웹 뷰어 기준으로 `TEXT` 페이지당 500~700자, 전체 약 3만 3천 자다.
`estimatedReadingSeconds`는 고정값이 아니라 페이지 본문 길이를 분당 330자로 환산해 산출하며,
`IMAGE` 페이지는 45초, 목차는 40초로 둔다. 도서 전체 예상 독서 시간은 약 93분이다.

목차의 `contentRole`은 정본이 구조 페이지용으로 정한 `FRONT_MATTER`다. 도서 제작 기준의 역할
존재 여부를 셀 때는 세지 않으므로 내용 역할 5종은 본문 56페이지가 모두 채운다.

페이지 단위 후보 여부는 `aiRouteCandidatePage`로 표시한다. 도서 단위 `aiRouteCandidate`와 달리
`book_page.ai_route_candidate`로 저장하는 영속 값이며, `FRONT_MATTER` 페이지는 항상 `false`다.
`false`인 페이지는 임베딩을 만들지 않고 다른 페이지의 선수 관계 대상이나 평가 정답이 될 수 없다.

이 필드는 정본과 ERD에 반영했으나 구현은 아직 없다. `book_page.ai_route_candidate` 컬럼 추가
마이그레이션과 C01 파싱·C02 검증·C03 임베딩 제외·G02 후보 필터가 남아 있다.

`bookId`·제목·저자·카테고리·소개는 `src/main/resources/demo/books.json`의 기존 값을 유지하고 본문만
새로 제작했다. 기존 플레이스홀더 본문 5페이지는 보존하지 않았다.

## 검증 결과

작성 시점에 다음을 확인했다. C02 validator 구현 전이므로 별도 스크립트로 검사했다.

- manifest 최상위·book·page 필수 필드 존재와 목록 필드의 빈 배열 사용
- `pageNumber` 1부터 연속, 중복 없음, `totalPageCount`와 일치
- 48~72페이지, 최소 6개 장, 내용 역할 5종 모두 사용, 목차만 `FRONT_MATTER`
- `FRONT_MATTER`와 `aiRouteCandidatePage=false`가 정확히 일치
- 후보 제외 페이지가 선수 관계 대상·정답 경로·대체 페이지 어디에도 없음
- 목차의 장·절 항목 41개가 실제 시작 페이지 번호와 일치
- `aiAnalysisInputSha256`이 `aiAnalysisText`의 SHA-256과 일치, 페이지마다 분석 텍스트가 서로 다름
- `aiPublicGuideTopic`에 수치 없음, 분석 텍스트를 그대로 옮기지 않음, 500자 이내
- 선수 관계가 존재 페이지만 참조, 자기 참조·중복 간선 없음, 위상 정렬로 57페이지 전체 방문
- 중복 그룹이 각각 2개 이상 페이지를 가지고 `evaluation.json`의 그룹과 일치
- 평가 정답 개념이 도서의 `primaryConcepts`·`secondaryConcepts`에 실재
- 정답 경로 7페이지가 예산 10잉크 이내, 정답 경로에 같은 중복 그룹 페이지가 둘 이상 없음
- 원고와 manifest의 페이지 번호·장·절 일치

- PDF가 존재하고 0바이트를 넘으며 실제 SHA-256이 `manifest.json`의 `pdfSha256`과 일치
- PDF 페이지 수 57이 manifest 페이지 수와 일치, 텍스트 페이지 51개와 이미지 페이지 6개가 설계와 일치

PDF 조립 도구와 검증 절차는
[`docs/evidence/ai-route-corpus/book-047-assembly.md`](../../../docs/evidence/ai-route-corpus/book-047-assembly.md)에 있다.
PDF SHA-256은 `57da4e679ebca508bd388db5c0a4a1e842c51f584e84fbf7f9e13cca43b622f8`다.

## 권리와 개인정보

- 실존 작품·저자·본문을 모방하거나 인용하지 않은 신규 창작 콘텐츠만 사용한다.
- 사례의 상호·인물·지명은 모두 가상이며 실존 개인을 식별할 수 있는 정보를 넣지 않았다.
- 이미지 페이지는 외부 자산을 반입하지 않고 도표를 직접 작성한다.
- `dataPolicyVersion`은 `OPENAI_DEFAULT_RETENTION_V1`, 지원 도서의 `aiExternalTransferAllowed`는 `true`다.
- 최종 검수자는 저장소 소유자다.

## 남은 작업

1. 비소설 89권 확장과 평가 89건 배분, 일곱 시나리오 충족
2. 소설 10권을 기존 PDF·SHA-256으로 편입
3. `aiRouteCandidatePage` 구현 (컬럼 마이그레이션, C01 파싱, C02 검증, C03 임베딩 제외, G02 후보 필터)
