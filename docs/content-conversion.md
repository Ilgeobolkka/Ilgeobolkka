# 콘텐츠 변환과 품질 검증

[PRD 색인](./prd/README.md)으로 돌아갑니다. 이 문서는 원본 PDF를 페이지별 `TEXT` 또는 `IMAGE`
콘텐츠로 변환하고 품질을 검증하는 실행 절차입니다. 콘텐츠 구조의 결정 근거는
[ADR-0006](./adr/content/0006-use-poppler-and-private-s3-for-image-pages.md)과
[ADR-0011](./adr/content/0011-align-page-content-with-source-pdf.md)을 따릅니다. 공개 AI 시연 PDF의 저장
예외는 [ADR-0013](./adr/content/0013-store-public-ai-fixture-pdfs.md)을 따릅니다.

## 변환 원칙

- `Book.totalPageCount`는 원본 PDF 페이지 수와 같아야 합니다.
- 변환 콘텐츠는 `(bookId, pageNumber)`로 원본 PDF의 같은 페이지와 일대일 연결합니다.
- 읽기 순서를 보존할 수 있고 추출 결과가 비어 있지 않은 일반 본문은 `TEXT`, 추출 결과가 비어 있거나
  표·수식·삽화나 복잡한 배치는 `IMAGE`로 변환합니다.
- 표지는 PDF 페이지와 별도인 공개 메타데이터 자산으로 준비하고 페이지 수에 포함하지 않습니다.
- 실제 사용 원본 PDF와 이미지 저장소는 비공개로 유지하고 브라우저에는 내부 경로를 제공하지 않습니다.
  공개 AI 시연 PDF에는 ADR-0013의 제한된 Git fixture 예외만 적용합니다.

## MVP 시연 PDF 기준선

SCRUM-403의 `books.json`, 합성 페이지 규칙과 PNG는 목록·검색·잉크·서재 API를 먼저 검증하기 위한
임시 입력입니다. SCRUM-404에서는 이 입력과 내용이 같은 공개 가능 AI 시연 PDF 100권을 한 번 만들고
`fixtures/content/pdfs/`에 고정합니다. PDF를 고정한 뒤에는 해당 PDF가 페이지 수와 번호의 정본입니다.

`fixtures/content/manifest.json`은 도서마다 `bookId`, PDF 상대 경로, PDF SHA-256과 전체 페이지 수만
기록합니다. PDF 생성 방법과 도구는 최초 fixture 준비 근거로 남기되, 반복 변환이 기존 합성 페이지 규칙을
읽어 PDF를 다시 만들지는 않습니다.

최초 PDF와 현재 합성 페이지의 도서·페이지 번호·`TEXT`·`IMAGE` 내용이 같다는 검증을 보존한 뒤
`DemoBookCatalog#createPage`와 `src/main/resources/demo/book-pages/`의 페이지 PNG는 제거합니다.
`books.json`은 도서 메타데이터 입력으로 계속 사용합니다.

공개 시연 PDF와 manifest는 변환 입력일 뿐 애플리케이션 JAR·배포 이미지·정적 자산에 포함하거나 HTTP로
제공하지 않습니다. 실제 사용 콘텐츠와 공개 여부가 확인되지 않은 PDF는 비공개 저장소에서 관리합니다.

## 변환과 적재 경계

- 변환은 고정한 PDF와 manifest만 입력으로 받아 전체 페이지의 `TEXT`·`IMAGE` 산출물을 먼저 만듭니다.
- `TEXT`는 DB에 저장하고 `IMAGE`는 로컬에서 Git 제외한 `var/content/pages/`에 저장합니다. 운영에서는
  [ADR-0006](./adr/content/0006-use-poppler-and-private-s3-for-image-pages.md)의 비공개 S3로 교체합니다.
- 파일 산출물은 새 버전 디렉터리에 완성하고 검증이 끝나기 전 DB에서 해당 경로를 참조하지 않습니다.
- 전체 100권의 변환·자동 검증이 통과한 뒤 한 DB 트랜잭션에서 기존 `(bookId, pageNumber)`의
  `BookPage` 내용을 갱신하고 없는 페이지만 추가합니다. 기존 식별자는 보존하며 예상하지 않은 기존
  페이지가 있으면 삭제하지 않고 전체 적재를 실패시킵니다.
- 하나라도 실패하면 DB를 변경하지 않고 기존 시연 콘텐츠를 유지합니다. 별도 공개 상태 컬럼, 수동 승인
  파일과 재적재 삭제 정책은 MVP 범위에 포함하지 않습니다.
- 구체적인 실행 명령은 SCRUM-404 구현과 함께 추가합니다. 구현 전 일반 로컬 실행 절차에는 존재하지 않는
  변환 명령을 추가하지 않습니다.

## 이미지 변환 기준

- 변환 도구는 Poppler `pdftoppm`을 사용하고 애플리케이션 요청과 분리된 사전 배치로 실행합니다.
- 기준 버전은 검증 근거와 같은 Poppler 26.05.0입니다. 버전을 바꾸면 같은 표본을 다시 변환하고
  결과를 비교합니다.
- 기본 출력은 150 DPI, RGB JPEG이며 `quality=85,optimize=y,progressive=y`를 사용합니다.
- 품질 85가 실패하면 품질 92, 150 DPI PNG, 200 DPI PNG 순서로 다시 검증합니다.
- 채택한 형식·DPI·품질과 원본 SHA-256은 변환 결과 manifest에 기록합니다.

## 변환 완전성

- manifest에는 시연 PDF 100권·400페이지가 있고 `bookId`가 중복 없이 현재 시연 도서와 일치해야 합니다.
- 각 PDF의 SHA-256과 페이지 수가 manifest와 일치해야 합니다.
- 원본 PDF 페이지 수와 생성한 페이지 콘텐츠 수가 같아야 합니다.
- 모든 페이지가 `TEXT` 또는 `IMAGE` 중 정확히 하나의 형식을 가져야 합니다.
- `TEXT`는 공백만으로 구성되지 않은 본문만, `IMAGE`는 공백이 아닌 경로가 가리키는 0바이트 초과 파일만
  가져야 하며 다른 형식의 값은 없어야 합니다.
- 모든 페이지 번호가 1부터 중복·누락 없이 연결되는지 자동 검증합니다.
- 변환 실패나 번호 불일치가 있으면 DB 적재를 시작하지 않습니다.
- 적재 전후 같은 `(bookId, pageNumber)`의 `BookPage.id`가 유지되어야 합니다.

## 품질 표본

품질 표본 manifest에는 최소한 `bookId`, `sourceSha256`, `category`, `pageNumber`, `reason`, `result`를
기록합니다. 100권 전체의 위험 요소를 고려해 카테고리별 대표 도서 한 권씩 총 10권을 선정하고, 수동 검수
대상 도서는 이 10권으로 제한합니다.

- 대표 도서의 첫 페이지, `(totalPageCount + 1) / 2`의 정수 나눗셈으로 계산한 중간 페이지,
  마지막 페이지를 기록합니다.
- 선정한 10권 안에서 작은 글자, 빽빽한 표·도표, 수식·삽화와 가는 선을 대표하는 페이지를 추가합니다.
- 같은 페이지가 여러 조건에 해당해도 실제 이미지 검사는 한 번만 수행합니다.
- 해당 위험 요소가 없으면 페이지 번호를 비우고 제외 근거를 기록합니다.

## 품질 판정

- `TEXT`는 문단·문장·글자의 읽기 순서를 원본과 비교합니다.
- `IMAGE`는 원본과 변환 이미지를 100%와 200% 크기로 비교해 한글 자소, 문장부호, 가는 선,
  표·수식·삽화의 누락·잘림과 압축 흔적을 확인합니다.
- 이미지 충실도 실패는 변환 조건을 조정합니다. `1280×800` 데스크톱 뷰어 표시 검증은 뷰어를 구현하는
  SCRUM-406에서 같은 표본으로 수행합니다.

## 보존된 검증 근거

- 원본: [`book_001.pdf`](./evidence/page-image-quality/book_001.pdf)의 3페이지
- SHA-256: `af434962fde401334f08c2df5afd1979b1f049b9efeda6790637779c839c2fd7`
- 변환 도구: Poppler `pdftoppm` 26.05.0
- JPEG 명령:
  `pdftoppm -f 3 -l 3 -singlefile -r 150 -jpeg -jpegopt quality=85,optimize=y,progressive=y docs/evidence/page-image-quality/book_001.pdf book_001-page-003`
- PNG 명령:
  `pdftoppm -f 3 -l 3 -singlefile -r 150 -png docs/evidence/page-image-quality/book_001.pdf book_001-page-003`
- 결과: JPEG 195,313바이트, PNG 266,163바이트로 JPEG가 70,850바이트 작았습니다.
- 육안 판정: 875×1,241픽셀 원본 해상도에서 한글 자소와 문장부호를 구분할 수 있고 글자 획을 가리는
  블록이나 번짐이 없었습니다.

현재 근거는 단일 표본의 변환 기준을 재현하기 위한 자료입니다. 전체 도서와 실제 뷰어 품질이 이미
검증됐다는 의미는 아니며, 전체 데이터 적재 전 [테스트 전략](./test-strategy.md#8-변환-품질-확인)의
게이트를 통과해야 합니다.
