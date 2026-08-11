# AI 경로 콘텐츠 코퍼스 `ai-route-v2`

[AI 경로 콘텐츠 코퍼스 정본](../../../docs/ai-route-content-corpus.md)의 `ai-route-v2` 입력이다.
이 디렉터리의 PDF와 본문은 실존 작품을 옮기지 않은 시연 전용 가상 콘텐츠다.

## 현재 상태: 20권 — 비소설 10 + 소설 10 (확장 중)

정본은 최종적으로 100권을 요구하지만 지금은 20권이 들어 있고, **이 상태로 적재하는 것이 현재
목표다.** 평가 시나리오 일곱 가지를 모두 채웠고, 남은 비소설 80권은 같은 절차로 늘린다. 소설 10권은
`initial-v1`의 PDF·SHA-256·페이지 수를 그대로 쓰며 이미 들어 있다.

검증은 권수를 세지 않고 manifest에 실제로 든 도서만 계약대로 검사하므로
([정본 완료 조건](../../../docs/ai-route-content-corpus.md#완료-조건)) 권수 자체는 통과 조건이 아니다.
100권 완성 여부는 확장 진행 상황이지 적재 통과 조건이 아니다.

모든 계약을 만족해 `validate_manifest.py`가 전체 통과한다. 평가 정답 경로는 선수 전이 폐쇄로
적으며, 그렇게 다시 계산한 기록은
[평가 정답 경로 재조정](../../../docs/evidence/ai-route-corpus/evaluation-replan.md)에 있다.

| 항목 | 정본 최종 목표 | 현재 |
| --- | --- | --- |
| 도서 수 | 100권 (비소설 90 + 소설 10) | **20권** (비소설 10 + 소설 10) |
| 평가 케이스 | 90건, 일곱 시나리오 전부 | **10건**, 일곱 시나리오를 모두 사용 |
| 소설 10권 | 기존 PDF·SHA-256 유지, `aiRouteCandidate=false` | **완료** (`bookId` 1~10) |

확장 시 비소설 80권을 `books[]`에, 평가 80건을 `cases[]`에 추가한다. 소설 10권은
`fixtures/content/manifest.json`의 PDF·SHA-256·페이지 수를 그대로 쓰고 `aiRouteCandidate=false`와
빈 `pages[]`로 기록했다. PDF는 같은 바이트를 `pdfs/`에 두어 이 디렉터리만으로 완결되게 했다 —
내용이 같으면 Git이 blob을 공유하므로 저장소 용량은 늘지 않는다.

## 구성

| 경로 | 내용 |
| --- | --- |
| `manifest.json` | 최상위 4필드 + `books[]`, 도서별 7필드 + `pages[]`, 페이지 메타데이터 13필드 |
| `evaluation.json` | 도서별 대표 목적과 정답 데이터 |
| `pdfs/book-0NN.pdf` | 원본 PDF. `pdfPath`는 `manifest.json` 기준 상대 경로 |

본문 원고와 이미지 페이지 명세는 fixture가 아니라 검수 근거로
[`docs/evidence/ai-route-corpus/`](../../../docs/evidence/ai-route-corpus/)에 둔다. 도서별 구조 설계
근거는 같은 디렉터리의 `book-0NN-design.md`, 카테고리 단위 개념 배분은 `*-category-plan.md`에 있다.

`aiAnalysisInputSha256`은 같은 페이지 `aiAnalysisText`의 UTF-8 바이트에 대한 SHA-256(소문자 hex)이다.
검수를 통과한 분석 텍스트와 Embeddings API 입력이 같음을 확인하는 값이다.

## 수록 도서

| `bookId` | 제목 | 카테고리 | 페이지 | 장 | 이미지 | 평가 시나리오 |
| --- | --- | --- | --- | --- | --- | --- |
| 11 | 느린 아침을 수집하는 법 | 에세이 | 50 | 7 | 4 | 비소장 예산 0 |
| 41 | 분업과 비교우위: 교환은 왜 이익인가 | 경제 | 52 | 7 | 4 | 소장 QUICK |
| 42 | 거래비용과 계약의 경제학 | 경제 | 49 | 7 | 4 | 비소장 예산 5 |
| 43 | 소규모 사업체의 회계와 현금흐름 | 경제 | 53 | 7 | 4 | 소장 BALANCED |
| 47 | 가격 형성의 원리: 수요·공급과 시장실패 | 경제 | 57 | 7 | 6 | 비소장 예산 10 |
| 61 | 형태와 구도: 화면을 구성하는 원리 | 예술 | 52 | 7 | 6 | 비소장 예산 0 |
| 64 | 종이 조형: 접기 구조와 안정 | 예술 | 56 | 8 | 6 | 소장 DEEP |
| 66 | 공공 벽화의 구성 원리 | 예술 | 54 | 8 | 6 | 비소장 예산 15 |
| 67 | 영상 사운드 디자인의 이해 | 예술 | 56 | 8 | 6 | 소장 DEEP |
| 71 | 요청과 응답: 서버 처리의 구조 | 기술 | 50 | 7 | 4 | 비소장 예산 15 |

소설 10권(`bookId` 1~10)은 `initial-v1`의 PDF·SHA-256·페이지 수를 그대로 쓰고 `aiRouteCandidate=false`와
빈 `pages[]`로 기록한다. 제목도 `books.json`의 기존 값을 유지하며, AI 경로 지원 대상이 아니라 평가
케이스도 없다.

비소설 10권 합계는 529페이지이며 그중 목차 10페이지, 이미지 50페이지, 본문 TEXT 469페이지다.
소설 10권 40페이지를 더하면 manifest 전체는 569페이지다.

각 권의 1페이지는 장과 절을 시작 페이지와 함께 싣는 목차이고 본문은 2페이지부터다. 목차의
페이지 번호는 원고에 직접 적지 않고 최종 배열에서 산출하므로 본문 분량이 바뀌어도 어긋나지 않는다.

본문은 데스크톱 웹 뷰어 기준으로 `TEXT` 페이지당 272~701자, 10권 합계 약 20.9만 자다. 도서별 평균이
327자(book-042)에서 579자(book-047)까지 갈린다.

| 페이지당 글자 수 | 도서 |
| --- | --- |
| 500자 이상 (최대 701) | 11 · 43 · 47 · 71 |
| 320~490자 | 41 · 61 · 64 · 66 · 67 |
| 270~380자 | 42 |

`estimatedReadingSeconds`는 고정값이 아니라 페이지 본문 길이를 분당 330자로 환산해 5초 단위로
반올림하되 **최소 60초**로 두며, `IMAGE` 페이지는 45초, 목차는 40초다. 330자 미만 페이지는 모두 60초로
눌리므로 짧은 도서에서는 페이지별 차이가 사라진다 — book-042는 44개 `TEXT` 페이지 중 35개, book-066은
47개 중 11개가 60초다. 남은 80권은 페이지당 500자 이상을 목표로 해서 이 쏠림을 줄인다.

목차의 `contentRole`은 정본이 구조 페이지용으로 정한 `FRONT_MATTER`다. 도서 제작 기준의 역할
존재 여부를 셀 때는 세지 않으므로 내용 역할 5종은 본문 페이지가 모두 채운다.

페이지 단위 후보 여부는 `aiRouteCandidatePage`로 표시한다. 도서 단위 `aiRouteCandidate`와 달리
`book_page.ai_route_candidate`로 저장하는 영속 값이며, `FRONT_MATTER` 페이지는 항상 `false`다.
`false`인 페이지는 임베딩을 만들지 않고 다른 페이지의 선수 관계 대상이나 평가 정답이 될 수 없다.

이 필드는 정본·ERD와 C03·C04·G02 구현 가이드에 반영했고 C01 파서가 읽는다.
`book_page.ai_route_candidate` 컬럼은 V3 마이그레이션으로 추가했으며, C02 검증기와 C03 임베딩 배치도
이 값을 기준으로 동작한다. 남은 것은 C04 적재다.

`bookId`·저자·카테고리·소개는 `src/main/resources/demo/books.json`의 기존 값을 유지하고 본문만 새로
제작했다. 기존 플레이스홀더 본문 3~5페이지는 보존하지 않았다. 제목은 과학·경제·철학·예술·기술
카테고리에 한해 갱신했고 그 정본은 이 디렉터리 `manifest.json`의 `books[].title`이다. `books.json`은
동결된 `initial-v1` fixture이므로 고치지 않는다 — 그 파일의 제목이 `fixtures/content/pdfs/`의 PDF 본문에
함께 합성돼 있어 제목만 바꾸면 `ContentImportFullMySqlIntegrationTest`가 깨진다.

## 검증 결과

작성 시점에는 C02 validator 구현 전이었으므로
[`docs/evidence/ai-route-corpus/tools/`](../../../docs/evidence/ai-route-corpus/tools/)의 스크립트로
도서마다 다음을 확인했다. 병합 전 도서 단위 검사는 `validate_fragment.py`, 병합 후 전체 검사는
`validate_manifest.py`, 검증 도구 자체의 음성 검사는 `selftest.py`다. 이후 구현한 C02 검증기
(`AiRouteContentValidator`)가 적재 경로에서 같은 계약을 다시 검사한다.

- manifest 최상위·book·page 필수 필드 존재와 목록 필드의 빈 배열 사용
- `pageNumber` 1부터 연속, 중복 없음, `totalPageCount`와 일치
- 48~72페이지, 최소 6개 장, 내용 역할 5종 모두 사용, 목차만 `FRONT_MATTER`
- `FRONT_MATTER`와 `aiRouteCandidatePage=false`가 정확히 일치
- 후보 제외 페이지가 선수 관계 대상·정답 경로·대체 페이지 어디에도 없음
- 목차의 장·절 항목이 실제 시작 페이지 번호와 일치
- `aiAnalysisInputSha256`이 `aiAnalysisText`의 SHA-256과 일치, 페이지마다 분석 텍스트가 서로 다름
- `aiPublicGuideTopic`에 수치 없음, 분석 텍스트를 그대로 옮기지 않음, 500자 이내
- 선수 관계가 존재 페이지만 참조, 자기 참조·중복 간선 없음, 위상 정렬로 전체 페이지 방문
- 중복 그룹이 각각 2개 이상 페이지를 가지고 `evaluation.json`의 그룹과 집합 단위로 일치
- 평가 정답 개념이 도서의 `primaryConcepts`·`secondaryConcepts`에 실재
- 정답 경로에 같은 중복 그룹 페이지가 둘 이상 없음
- **정답 경로를 선수 전이 폐쇄까지 펼친 뒤** 비소장은 미대여 페이지 수가 예산 이하, 소장은 경로 전체가
  깊이 상한(QUICK 5·BALANCED 10·DEEP 15) 이하
- 도서 단위 선수 밀도 상한 — 폐쇄 ≤5인 페이지가 서로 다른 장 3개 이상, 폐쇄 ≤15가 후보의 75% 이상
  (10권 모두 통과)
- 소설(비후보 도서)의 빈 `pages[]`, PDF 존재와 SHA-256, 그리고 `initial-v1` manifest의 같은 `bookId`와
  PDF SHA-256·페이지 수가 일치
- 원고와 manifest의 페이지 번호·장·절 일치
- PDF가 존재하고 0바이트를 넘으며 실제 SHA-256이 `manifest.json`의 `pdfSha256`과 일치
- PDF 페이지 수가 manifest 페이지 수와 일치, 텍스트·이미지 페이지 구성이 설계와 일치

PDF 조립 도구와 검증 절차는
[`docs/evidence/ai-route-corpus/book-047-assembly.md`](../../../docs/evidence/ai-route-corpus/book-047-assembly.md)에 있다.
PDF SHA-256은 도서마다 `manifest.json`의 `pdfSha256`에 있다.

## 권리와 개인정보

- 실존 작품·저자·본문을 모방하거나 인용하지 않은 신규 창작 콘텐츠만 사용한다.
- 사례의 상호·인물·지명은 모두 가상이며 실존 개인을 식별할 수 있는 정보를 넣지 않았다.
- 이미지 페이지는 외부 자산을 반입하지 않고 도표를 직접 작성한다.
- `dataPolicyVersion`은 `OPENAI_DEFAULT_RETENTION_V1`, 지원 도서의 `aiExternalTransferAllowed`는 `true`다.
- 최종 검수자는 저장소 소유자다.

## 남은 작업

1. 비소설 80권 확장과 평가 80건 배분
2. `aiRouteCandidatePage` 적재 구현 — C04 원자적 적재 (컬럼·매핑 V3, C02 검증, C03 임베딩은 완료)
