# 코퍼스 제작 도구 (참고용, 빌드 비대상)

SCRUM-485 90권 확장에서 재사용하는 Python 스크립트다. 애플리케이션 코드가 아니고 Gradle
빌드·테스트 대상이 아니다 — 콘텐츠 제작 중 fixture(`manifest.json`, `evaluation.json`, PDF)를
생성·검증하는 저작 도구다. 실행에는 표준 라이브러리만 쓰고 외부 패키지가 필요 없다.

## 구성

| 파일 | 역할 |
| --- | --- |
| `corpus_lib.py` | 절 식별자로 선수 관계를 걸고 페이지 순서에서 최종 번호를 자동 계산하는 빌더. 페이지를 끼워 넣어도 번호가 깨지지 않는다. `validate_book()`으로 생성 즉시 검증한다. |
| `validate_manifest.py` | `manifest.json`의 모든 도서와 `evaluation.json`을 한 번에 검사한다. `check.py`(book-047 전용, 이 디렉터리에 없음)를 대신한다. |
| `validate_fragment.py` | 공유 파일에 합치기 전 도서 하나를 독립 검증한다. `python3 validate_fragment.py ../_fragments/book-0NN.json`. |
| `merge_fragments.py` | `_fragments/`의 조각을 `manifest.json`·`evaluation.json`에 병합한다. bookId·caseId 중복을 병합 전에 막고, 합친 뒤 전체를 재검증하며, 성공하면 역할이 끝난 조각을 지운다. `--dry-run`으로 미리 확인하고 bookId를 인자로 주면 그 권만 병합한다. |
| `pdfcheck.py` | Poppler 없이 PDF 객체를 직접 파싱해 페이지 수·TEXT/IMAGE 구성을 확인한다. `python3 pdfcheck.py <pdf경로> <기대페이지수> <기대이미지목록,쉼표구분>`. |
| `figures041.py` | book-041 도표 4개의 SVG 생성 예시. 새 도서의 도표를 그릴 때 `head()`/`svg()` 헬퍼를 그대로 가져다 쓴다. |
| `build_pdf_generic.py` | 원고 JSON을 A4 HTML로 조립해 Chrome Headless로 PDF를 뽑는다. `python3 build_pdf_generic.py <bookId> <이미지페이지,쉼표>`로 바로 실행. |

## 새 도서를 만드는 순서

1. 카테고리 계획 문서(예: `economics-category-plan.md`)에서 개념 범위 확인
2. 페이지 원고를 `dict(ch=, sec=, fmt=, role=, pri=, sec_c=, dup=, topic=, body=, analysis=)` 형태의
   리스트로 작성. 페이지 번호는 적지 않는다 — 리스트 순서가 곧 순서다.
3. `corpus_lib.assemble(pages, section_edges)`로 번호·선수 관계·목차를 계산
4. `corpus_lib.build_pages(...)`로 manifest/원고 페이지 생성, `validate_book()`으로 검증
5. 조각 파일 `_fragments/book-0NN.json`에 `manifestBook`과 `evaluationCase`를 담아 저장.
   공유 `manifest.json`·`evaluation.json`을 직접 고치면 여러 카테고리를 동시에 작업할 때 충돌한다
6. 이미지 페이지는 SVG로 그려 Chrome Headless로 PNG 렌더링 후 눈으로 확인
7. `build_pdf_generic.py`로 HTML 조립 → Chrome Headless로 PDF 출력
8. PDF를 fixture에 배치하고 SHA-256을 조각에 반영한 뒤 `validate_fragment.py`로 검증
9. `merge_fragments.py`로 정본에 병합 (중복 검사·전체 재검증·조각 정리까지 한 번에)
