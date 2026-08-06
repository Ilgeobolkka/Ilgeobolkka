# 공개 AI 시연 PDF fixture

이 디렉터리의 PDF 100권은 실존 작품을 옮기지 않은 시연 전용 가상 콘텐츠다. 개인 정보, 실존 저자·작품
본문, 권리 제한 자료는 입력에 포함하지 않았다. 이 공개 예외는 `fixtures/content/`에 고정한 시연 PDF와
최소 manifest에만 적용한다.

## 동결 결과

- 동결일: 2026-07-30
- 콘텐츠 버전: `initial-v1`
- 도서·페이지: 100권·400페이지
- PDF 경로: `pdfs/book-001.pdf`부터 `pdfs/book-100.pdf`
- manifest SHA-256: `e91609452a5a85baec4f61464cd127e6e79d2223336a28867027f58c57924408`
- 최초 PDF 생성 도구: Google Chrome Headless `150.0.7871.187`
- 반복 변환 도구: Poppler `pdftotext`·`pdftoppm` `26.05.0`

`manifest.json`은 최상위 `contentVersion=initial-v1`과 도서별 `bookId`, PDF 상대 경로, PDF SHA-256,
전체 페이지 수를 기록한다. 동결 뒤
변환 배치는 이 파일과 PDF만 입력으로 사용하며 아래 최초 합성 규칙이나 삭제된 PNG를 읽지 않는다.

## 최초 생성 입력과 규칙

메타데이터 입력 `src/main/resources/demo/books.json`의 SHA-256은
`2b8da48bc08e67c1b042ec855c01da893477f7e4a64f45e1dda0a1c562fab52b`다.

각 도서의 2페이지는 당시 `src/main/resources/demo/book-pages/`에 있던 카테고리 PNG를 A4 페이지
안에 비율 유지해 넣었다. 나머지 페이지에는 당시 `DemoBookCatalog#createPage`와 같은 순서로 제목,
`<저자>이 작성한 가상 본문의 <페이지>페이지입니다.`, 검증 문장을 배치했다. HTML은 A4, 여백 0,
권별 페이지 나눔을 적용했고 텍스트 내부 공백은 `pre-wrap`, 이미지는 `object-fit: contain`으로
보존했다.

삭제 전 카테고리 PNG의 SHA-256은 다음과 같다.

| 입력 | SHA-256 |
| --- | --- |
| `category-01.png` | `d3b54a9006bdb82d814aad34a8093360a167a69c369ed10a046584783dee22fb` |
| `category-02.png` | `e0a2456701d4fa31334a8ebf9ee28a4ef72b10dc8a9b96da89c98c5ec679bf66` |
| `category-03.png` | `ebcba8bf056968bbe7b4858d9335c85086221ed00738c86ea064ba90577e4939` |
| `category-04.png` | `bc2ec49715f682b2d95db28f2a416dd0bbf3b266ec2a0505c4dd3dbed8bffdec` |
| `category-05.png` | `1b97f31ffcb69bffd33f8a964e55dbad6acae9ae43e0933970f76f4923d3e241` |
| `category-06.png` | `980bbc314e9d0c83d0483671f99bbb08e898485bf4a1a6ba23e3465472808366` |
| `category-07.png` | `4d9bee8a09d44b8ca1b7d610612e593aacd4dd3c8a36f11d56591fa0d9326def` |
| `category-08.png` | `e9742c7058941a8e98d42afdf84af4a1a5581eaf4550d48c345d23d7c0e3571a` |
| `category-09.png` | `d61ade4f24ee4a44568f99188cf9302c90067ba20884e6c4af59819fb557bab1` |
| `category-10.png` | `b4efff4d9533bda82db25f89fec0cab920aca90ac31e02660ac9b9e52e5ab3d1` |

최초 생성 시 권별 임시 HTML에 대해 다음 Chrome 명령 형식을 사용했다. 임시 HTML과 생성 코드는 동결
후 제거했으며 PDF를 반복 생성하는 제품 경로는 제공하지 않는다.

```bash
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
  --headless=new \
  --disable-background-networking \
  --disable-dev-shm-usage \
  --disable-gpu \
  --disable-sync \
  --metrics-recording-only \
  --no-default-browser-check \
  --no-first-run \
  --no-pdf-header-footer \
  --allow-file-access-from-files \
  --run-all-compositor-stages-before-draw \
  --print-to-pdf="<저장소>/fixtures/content/pdfs/book-001.pdf" \
  "file://<권별-임시-HTML>"
```

## 동결 검증

자동 검증은 다음을 확인했다.

- PDF·manifest SHA-256 일치
- `bookId` 1~100의 중복·누락 없음
- PDF의 1부터 manifest 끝 페이지까지 존재하고 끝 다음 페이지는 없음
- 총 400페이지, TEXT 300개, IMAGE 100개
- TEXT의 제목·문장·글자 읽기 순서가 최초 합성 규칙과 일치
- IMAGE가 빈 텍스트로 판정되고 150 DPI, JPEG 품질 85 산출물이 0바이트보다 큼
- 기존 `(bookId, pageNumber)`의 `BookPage.id` 보존과 MySQL 트랜잭션 롤백

Poppler는 PDF에 시각적으로 보존된 연속 공백과 문단 여백을 기본 읽기 순서 추출에서 단일 공백·연속
줄로 반환한다. 검증은 공백 수가 아니라 제목과 두 문장의 글자 순서가 같은지 비교하며, 변환기는 Poppler
출력의 내부 순서를 추가 재구성하지 않는다.

대표 10권의 수동 검수 결과는
[`docs/evidence/content-quality/quality-samples.json`](../../docs/evidence/content-quality/quality-samples.json)에
있다.
