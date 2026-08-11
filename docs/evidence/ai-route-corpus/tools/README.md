# 코퍼스 제작 도구 (참고용, 빌드 비대상)

SCRUM-485 90권 확장에서 재사용하는 Python 스크립트다. 애플리케이션 코드가 아니고 Gradle
빌드·테스트 대상이 아니다 — 콘텐츠 제작 중 fixture(`manifest.json`, `evaluation.json`, PDF)를
생성·검증하는 저작 도구다. 실행에는 표준 라이브러리만 쓰고 외부 패키지가 필요 없다.

## 구성

| 파일 | 역할 |
| --- | --- |
| `corpus_lib.py` | 절 식별자로 선수 관계를 걸고 페이지 순서에서 최종 번호를 자동 계산하는 빌더. 페이지를 끼워 넣어도 번호가 깨지지 않는다. `validate_book()`으로 생성 즉시 검증한다. |
| `validate_manifest.py` | `manifest.json`의 모든 도서와 `evaluation.json`을 한 번에 검사한다. 인자 없이 실행하면 정본 fixture를, 디렉터리를 주면 그 fixture를 검사한다. 검사 집합은 `validate_fragment.py`와 같게 유지한다 — 한쪽에만 넣으면 조각으로 들어온 도서와 손으로 고친 정본의 기준이 갈린다. |
| `validate_fragment.py` | 공유 파일에 합치기 전 도서 하나를 독립 검증한다. `python3 validate_fragment.py ../_fragments/book-0NN.json`. |
| `merge_fragments.py` | `_fragments/`의 조각을 `manifest.json`·`evaluation.json`에 병합한다. bookId·caseId 중복을 병합 전에 막고, **병합 결과를 임시 디렉터리에서 먼저 검증해 통과한 경우에만 정본에 쓰며**, 성공하면 역할이 끝난 조각을 지운다. `--dry-run`으로 미리 확인하고 bookId를 인자로 주면 그 권만 병합한다. |
| `pdfcheck.py` | Poppler 없이 PDF 객체를 직접 파싱해 페이지 수·TEXT/IMAGE 구성을 확인한다. `python3 pdfcheck.py <pdf경로> <기대페이지수> <기대이미지목록,쉼표구분>`. |
| `figures0NN.py` | 도서별 도표 SVG 생성 스크립트(011·041·042·061·064·066·067·071). 새 도서의 도표를 그릴 때 `head()`/`svg()` 헬퍼를 그대로 가져다 쓴다. |
| `build_pdf_generic.py` | 원고 JSON을 A4 조판 HTML 한 장으로 조립해 저장한다. PDF 출력은 하지 않으므로 저장된 HTML을 Chrome Headless로 인쇄하는 단계가 따로 필요하다. `python3 build_pdf_generic.py <bookId> <이미지페이지,쉼표>`로 바로 실행. |
| `selftest.py` | 검증 도구가 깨진 입력을 실제로 잡는지 확인하는 음성 테스트. 정본 fixture에서 입력을 만들어 한 곳씩 고의로 깨뜨리고 각각 FAIL로 걸리는지 본다. `validate_fragment.py`·`validate_manifest.py`·`corpus_lib.validate_book()` 셋과 `merge_fragments.py`의 실패 경로를 덮는다. `python3 selftest.py`. 양성 표본은 정본 전체이고, 조각 단위 음성 검사는 예산·대여를 깨뜨릴 수 있는 예산 0 사례(`book-011`)에서 만든다. |

## 새 도서를 만드는 순서

1. 카테고리 계획 문서(예: `economics-category-plan.md`)에서 개념 범위 확인
2. 페이지 원고를 `dict(ch=, sec=, fmt=, role=, pri=, sec_c=, dup=, topic=, body=, analysis=)` 형태의
   리스트로 작성. 페이지 번호는 적지 않는다 — 리스트 순서가 곧 순서다.
3. `corpus_lib.assemble(pages, section_edges)`로 번호·선수 관계·목차를 계산
4. `corpus_lib.build_pages(...)`로 manifest/원고 페이지 생성, `validate_book()`으로 검증
5. 조각 파일 `_fragments/book-0NN.json`에 `manifestBook`과 `evaluationCase`를 담아 저장.
   공유 `manifest.json`·`evaluation.json`을 직접 고치면 여러 카테고리를 동시에 작업할 때 충돌한다.
   `manifestBook.title`은 `ai-route-v2` 제목의 정본이므로 반드시 채운다 — `src/main/resources/demo/books.json`은
   동결된 `initial-v1` fixture라 고치지 않는다
6. 이미지 페이지는 SVG로 그려 Chrome Headless로 PNG 렌더링 후 눈으로 확인
7. `build_pdf_generic.py`로 HTML 조립 → 저장된 HTML을 Chrome Headless로 인쇄해 PDF 출력
8. PDF를 fixture에 배치하고 SHA-256을 조각에 반영한 뒤 `validate_fragment.py`로 검증
9. `merge_fragments.py`로 정본에 병합 (중복 검사·전체 재검증·조각 정리까지 한 번에)

검증 도구를 고쳤으면 `python3 selftest.py`로 음성 검사가 여전히 걸리는지 확인한다. 모든 스크립트는
`__file__` 기준으로 저장소 루트를 찾으므로 어느 디렉터리에서 실행해도 된다.

## 정답 경로와 선수 폐쇄

평가 정답의 비용·분량은 `referencePageNumbers`가 아니라 **그 페이지들의 전이적 선수 폐쇄**로 센다.
[PRD 경로 생성 정책](../../../prd/ai-ink-route.md#경로-생성-정책)이 최종 경로에 선수 페이지를 모두
포함하도록 요구하기 때문이다. 폐쇄를 빼고 세면 실현할 수 없는 정답이 검증을 통과한다.

- 비소장: `|폐쇄 − activeRentalPageNumbers| ≤ maxAdditionalInk`
- 소장: `|폐쇄| ≤` 깊이별 상한 (QUICK 5 · BALANCED 10 · DEEP 15)

정답 경로는 폐쇄 자체를 적는다. 고른 페이지만 적으면 검증기가 폐쇄를 펼칠 때 값이 달라지고, 폐쇄를
적으면 정답이 스스로 실현 가능해진다. 예산 0 사례는 결국 `폐쇄 ⊆ activeRentalPageNumbers`가 되므로
대여 목록도 폐쇄 전체에 맞춰 잡는다.

정답을 어떻게 골라도 상한에 못 맞추는 도서는 선수 관계를 너무 촘촘히 건 것이다. 그래서 도서 단위로
[선수 밀도 상한](../../../ai-route-content-corpus.md#도서-제작-기준)을 함께 검사한다.

- 폐쇄 ≤5인 후보 페이지가 서로 다른 장 3개 이상 (가장 작은 예산·빠른 깊이로 만들 재료)
- 후보 페이지의 75% 이상이 폐쇄 ≤15 (가장 큰 예산으로도 닿지 못하는 페이지가 1/4을 넘지 않게)

이 검사는 `corpus_lib.validate_book()`에도 있어서 **원고를 쓰기 전 구조 단계에서** 걸린다. 50페이지를
쓰고 나서 도서를 다시 설계하는 일을 막는 게 목적이다. 선수 관계는 manifest에만 있고 원고·PDF에는 없으므로,
이미 만든 도서가 걸리면 `prerequisitePageNumbers`만 다시 걸면 되고 원고나 PDF는 다시 만들지 않는다.

## 재생성할 수 있는 범위

기존 10권의 2~4단계 스크립트(원고 리스트와 `corpus_lib` 호출)는 남기지 않았다. `-manuscript.json`은
`pageNumber`·`chapter`·`section`·`contentFormat`·`body`만 담고 있어 역할·개념·분석 텍스트·선수 관계 같은
manifest 메타데이터를 여기서 되돌릴 수 없다. 따라서 **기존 도서의 `manifest.json` 항목은 재생성 대상이
아니라 정본 자체**이고, 고칠 일이 생기면 `manifest.json`을 직접 고친 뒤 `validate_manifest.py`로 검사한다.

원고에서 다시 만들 수 있는 것은 PDF뿐이다 (`build_pdf_generic.py` + 도서별 `figures0NN.py` → Chrome
Headless 인쇄). PDF를 다시 만들면 SHA-256이 달라지므로 `manifest.json`의 `pdfSha256`도 함께 갱신한다.

새로 만드는 도서는 위 순서를 그대로 따르고, 2~4단계 스크립트를 `_fragments/`와 함께 남겨 다음 사람이
같은 입력에서 다시 만들 수 있게 한다.
