# 콘텐츠 변환과 품질 검증

[PRD 색인](./prd/README.md)으로 돌아갑니다. 이 문서는 원본 PDF를 페이지별 `TEXT` 또는 `IMAGE`
콘텐츠로 변환하고 품질을 검증하는 실행 절차입니다. 콘텐츠 구조의 결정 근거는
[ADR-0006](./adr/content/0006-use-poppler-and-private-s3-for-image-pages.md)과
[ADR-0011](./adr/content/0011-align-page-content-with-source-pdf.md)을 따릅니다.

## 변환 원칙

- `Book.totalPageCount`는 원본 PDF 페이지 수와 같아야 합니다.
- 변환 콘텐츠는 `(bookId, pageNumber)`로 원본 PDF의 같은 페이지와 일대일 연결합니다.
- 읽기 순서를 보존할 수 있는 일반 본문은 `TEXT`, 표·수식·삽화나 복잡한 배치는 `IMAGE`로 변환합니다.
- 표지는 PDF 페이지와 별도인 공개 메타데이터 자산으로 준비하고 페이지 수에 포함하지 않습니다.
- 원본 PDF와 이미지 저장소는 비공개로 유지하고 브라우저에는 내부 경로를 제공하지 않습니다.

## 시연 시드와 변환 산출물의 구분

SCRUM-403의 로컬·시연 시드는 목록·검색·잉크·서재 API와 상태별 사용자 검증을 위한 합성 fixture입니다.
시드가 만드는 `TEXT`·`IMAGE` 페이지는 원본 PDF에서 변환한 콘텐츠가 아니며, 이 문서의 페이지 일대일
연결·manifest·첫/중간/마지막 페이지 대조·이미지 품질을 통과했다는 근거로 사용할 수 없습니다.

100권 원본 PDF의 변환과 비공개 저장, 변환 manifest와 품질 검증은 후속 SCRUM-404의 범위입니다.
SCRUM-404의 게이트를 통과하기 전 합성 fixture를 변환 완료 콘텐츠로 공개하지 않습니다.

## 이미지 변환 기준

- 변환 도구는 Poppler `pdftoppm`을 사용하고 애플리케이션 요청과 분리된 사전 배치로 실행합니다.
- 기준 버전은 검증 근거와 같은 Poppler 26.05.0입니다. 버전을 바꾸면 같은 표본을 다시 변환하고
  결과를 비교합니다.
- 기본 출력은 150 DPI, RGB JPEG이며 `quality=85,optimize=y,progressive=y`를 사용합니다.
- 품질 85가 실패하면 품질 92, 150 DPI PNG, 200 DPI PNG 순서로 다시 검증합니다.
- 채택한 형식·DPI·품질과 원본 SHA-256은 변환 결과 manifest에 기록합니다.

## 변환 완전성

- 원본 PDF 페이지 수와 생성한 페이지 콘텐츠 수가 같아야 합니다.
- 모든 페이지가 `TEXT` 또는 `IMAGE` 중 정확히 하나의 형식을 가져야 합니다.
- 첫·중간·마지막 페이지에서 원본과 변환 콘텐츠의 페이지 번호를 대조합니다.
- 변환 실패나 번호 불일치가 있는 도서는 공개하지 않습니다.

## 품질 표본

품질 표본 manifest에는 최소한 `bookId`, `sourceSha256`, `category`, `pageNumber`, `reason`을 기록합니다.

- 도서마다 첫 페이지, `(totalPageCount + 1) / 2`의 정수 나눗셈으로 계산한 중간 페이지,
  마지막 페이지를 기록합니다.
- 작은 글자, 빽빽한 표·도표, 가는 선을 대표하는 페이지를 각각 추가합니다.
- 같은 페이지가 여러 조건에 해당해도 실제 이미지 검사는 한 번만 수행합니다.
- 해당 위험 요소가 없으면 페이지 번호를 비우고 제외 근거를 기록합니다.

## 품질 판정

- `TEXT`는 문단·문장·글자의 읽기 순서를 원본과 비교합니다.
- `IMAGE`는 원본과 변환 이미지를 100%와 200% 크기로 비교해 한글 자소, 문장부호, 가는 선,
  표·수식·삽화의 누락·잘림과 압축 흔적을 확인합니다.
- 이미지 충실도를 통과한 표본은 `1280×800` 데스크톱 뷰포트의 초기 페이지 너비 맞춤 상태로
  확인합니다.
- 이미지 충실도 실패는 변환 조건을 조정하고, 화면 표시 크기 실패는 원본 지면 구성이나 뷰어 계약을
  별도로 검토합니다.

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
