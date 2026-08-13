# 콘텐츠 변환과 품질 검증

[PRD 색인](./prd/README.md)으로 돌아갑니다. 이 문서는 원본 PDF를 페이지별 `TEXT` 또는 `IMAGE`
콘텐츠로 변환하고 품질을 검증하는 실행 절차입니다. 콘텐츠 구조와 공개 AI 시연 fixture 경계는
[ADR-0013](./adr/content/0013-define-page-content-and-public-ai-fixture-boundary.md)을 따르며, 이미지
변환과 저장은 [ADR-0006](./adr/content/0006-use-poppler-and-private-s3-for-image-pages.md)을 따릅니다.

## 변환 원칙

- `Book.totalPageCount`는 원본 PDF 페이지 수와 같아야 합니다.
- 변환 콘텐츠는 `(bookId, pageNumber)`로 원본 PDF의 같은 페이지와 일대일 연결합니다.
- 읽기 순서를 보존할 수 있고 추출 결과가 비어 있지 않은 일반 본문은 `TEXT`, 추출 결과가 비어 있거나
  표·수식·삽화나 복잡한 배치는 `IMAGE`로 변환합니다.
- 표지는 PDF 페이지와 별도인 공개 메타데이터 자산으로 준비하고 페이지 수에 포함하지 않습니다.
- 실제 사용 원본 PDF와 이미지 저장소는 비공개로 유지하고 브라우저에는 내부 경로를 제공하지 않습니다.
  공개 AI 시연 PDF에는 ADR-0013의 제한된 Git fixture 예외만 적용합니다.

## 초기 MVP 시연 PDF 기준선

SCRUM-403의 `books.json`, 합성 페이지 규칙과 PNG는 목록·검색·잉크·서재 API를 먼저 검증하기 위한
임시 입력입니다. SCRUM-404에서는 이 입력과 내용이 같은 공개 가능 AI 시연 PDF 100권을 한 번 만들고
`fixtures/content/pdfs/`에 고정합니다. PDF를 고정한 뒤에는 해당 PDF가 페이지 수와 번호의 정본입니다.

`fixtures/content/manifest.json`의 콘텐츠 버전은 `initial-v1`입니다. manifest는 최상위
`contentVersion`을 생략하지 않고 도서마다 `bookId`, PDF 상대 경로, PDF SHA-256과 전체 페이지 수를
기록합니다. `contentVersion`은 구조·호환 계약을, manifest SHA-256은 정확한 입력 파일 리비전을
식별합니다. PDF 생성 방법과 도구는 최초 fixture 준비 근거로 남기되, 반복 변환이 기존 합성 페이지 규칙을
읽어 PDF를 다시 만들지는 않습니다.

버전 컬럼 도입 전 V1 schema에 존재하는 모든 `book` 행은 ID·페이지 수·manifest 포함 여부로 선별하지 않고
`initial-v1`로 backfill합니다. F01 migration의 최종 `book.content_version`은 기존 도서 writer와 테스트
fixture의 호환을 위해 `NOT NULL DEFAULT 'initial-v1'`로 유지합니다. 이 DB 기본값은 기존 삽입 경로의
호환 장치일 뿐이며 콘텐츠 manifest와 import batch는 `contentVersion`을 명시하고 기본값으로 추측하지
않습니다. `ai-route-v2` 적재도 해당 버전을 명시적으로 기록합니다.

최초 PDF와 현재 합성 페이지의 도서·페이지 번호·`TEXT`·`IMAGE` 내용이 같다는 검증을 보존한 뒤
`DemoBookCatalog#createPage`와 `src/main/resources/demo/book-pages/`의 페이지 PNG는 제거합니다.
`books.json`은 도서 메타데이터 입력으로 계속 사용합니다.

공개 시연 PDF와 manifest는 변환 입력일 뿐 애플리케이션 JAR·배포 이미지·정적 자산에 포함하거나 HTTP로
제공하지 않습니다. 실제 사용 콘텐츠와 공개 여부가 확인되지 않은 PDF는 비공개 저장소에서 관리합니다.

## AI 잉크 경로 2차 fixture 기준선

[AI 경로 콘텐츠 코퍼스](./ai-route-content-corpus.md)의 `ai-route-v2`는 `initial-v1` fixture를 덮어쓰지 않는
별도 콘텐츠 버전입니다.

- `ai-route-v2` manifest의 도서 구성과 페이지 범위는
  [AI 경로 콘텐츠 코퍼스의 범위](./ai-route-content-corpus.md#범위)와
  [도서 제작 기준](./ai-route-content-corpus.md#도서-제작-기준)을 따릅니다. manifest는
  `contentVersion`, `dataPolicyVersion`, 도서별 `aiExternalTransferAllowed`, 임베딩 모델·차원, 지원
  페이지의 분석 메타데이터 입력과 입력 SHA-256을 기록하며 이 결정적 입력이 하나라도 바뀌면 manifest
  SHA-256도 바뀌어야 합니다.
- `ai-route-v2`의 전체 페이지 수는 manifest의 도서별 페이지 수 합계로 계산하며 400으로 고정하지
  않습니다.
- 현재 변환기의 `100권·400페이지` 검증은 `initial-v1` fixture에만 적용합니다. 2차 MVP 구현에서는 선택한
  `contentVersion`에 따라 초기 계약 또는 `ai-route-v2` 계약을 검증하도록 바꿉니다.
- `ai-route-v2`는 대여·소장·내역이 없고 아직 HTTP 트래픽을 받지 않는 공개 전 후보 시연 DB에 전체
  적재합니다. 기존 사용자 기록이 있는 DB의 콘텐츠를 제자리에서 교체하거나 페이지를 삭제하지 않습니다.
- `initial-v1` manifest와 새 시연 DB로 되돌리는 절차는 후보 DB가 HTTP 트래픽을 받기 전에만 허용합니다. 한 번이라도
  HTTP 트래픽을 받아 사용자 기록이 생긴 DB를 폐기하거나 이전 manifest의 새 DB로 교체하는 롤백은
  금지합니다. 운영 사용자 데이터의 콘텐츠 버전 마이그레이션과 롤백은 2차 MVP 범위가 아닙니다.
- 재평가로 콘텐츠를 다시 적재할 때도 위 조건을 그대로 적용하므로 공개 전에만 실행합니다. 판정과 금지
  사항은 [PRD의 재평가와 지원 활성화 순서](./prd/ai-ink-route.md#재평가와-지원-활성화-순서)를 따릅니다.
  재적재는 새 후보 DB 전체 적재이므로 `contentVersion`을 새로 발급하지 않고, 달라진 manifest 리비전과
  임베딩 모델은 평가 결과의 재현 정보로 구분합니다.
- 재적재의 정의와 재적재가 없는 재평가의 실행 환경은
  [PRD의 재평가와 지원 활성화 순서](./prd/ai-ink-route.md#재평가와-지원-활성화-순서)가 정본입니다. 어느
  경우에도 공개 중인 DB의 콘텐츠를 제자리에서 교체해 재평가 대상 입력을 바꾸지 않습니다.

## 변환과 적재 경계

- 변환은 선택한 콘텐츠 버전의 고정 PDF와 manifest만 입력으로 받아 전체 페이지의 `TEXT`·`IMAGE`
  산출물을 먼저 만듭니다.
- `TEXT`는 DB에 저장하고 `IMAGE`는 로컬에서 Git 제외한 `var/content/pages/`에 저장합니다. 운영에서는
  [ADR-0006](./adr/content/0006-use-poppler-and-private-s3-for-image-pages.md)의 비공개 S3로 교체합니다.
- 파일 산출물은 새 버전 디렉터리에 완성하고 검증이 끝나기 전 DB에서 해당 경로를 참조하지 않습니다.
- 선택한 버전의 전체 도서와 버전별 페이지 수 검증이 통과한 뒤 한 DB 트랜잭션에서 기존
  `(bookId, pageNumber)`의 `BookPage` 내용을 갱신하고 없는 페이지만 추가합니다. 기존 식별자는 보존하며
  예상하지 않은 기존 페이지가 있으면 삭제하지 않고 전체 적재를 실패시킵니다.
- `ai-route-v2`는 DB 트랜잭션 전에 외부 전송 권리와 데이터 보관 조건을 확인한 페이지 분석 텍스트로
  임베딩을 모두 생성하고, 분석 텍스트·공개 가이드 주제·선수 관계·중복 그룹·예상 독서 시간 입력값·임베딩을
  `contentVersion`, `dataPolicyVersion`, 임베딩 모델·차원과 함께 검증합니다. 검증 뒤 `BookPage`와 같은 적재
  트랜잭션에 저장하며 일부 분석 데이터나 임베딩만 반영하지 않습니다. manifest 전용 비영속 필드와 평가
  fixture의 경로·최소 스키마는 [AI 경로 콘텐츠 코퍼스](./ai-route-content-corpus.md#구조-메타데이터)만
  따르며 평가 정답은 콘텐츠 적재나 런타임 후보 생성 입력으로 사용하지 않습니다.
- Embeddings API 호출 전에 manifest의 `dataPolicyVersion`과 배포 환경의
  `OPENAI_DATA_POLICY_VERSION`이 같고, 운영자가 전용 OpenAI 프로젝트의 적용 데이터 제어가 해당 프로필과
  같거나 더 엄격함을 확인했는지 검사합니다. 확인 기준은
  [OpenAI 데이터 정책 프로필](./evidence/openai-data-policy/README.md)을 따르며, 조건이 맞지 않으면 외부
  호출과 DB 변경 없이 전체 적재를 실패시킵니다.
- 도서·콘텐츠 버전별 AI 경로 지원 여부를 영속화합니다. 지원 대상과 평가 데이터는
  [AI 경로 콘텐츠 코퍼스](./ai-route-content-corpus.md), 활성화 판정은
  [AI 잉크 경로 PRD의 품질·출시 기준](./prd/ai-ink-route.md#품질과-출시-기준)을 통과한 경우에만
  지원합니다.
- 지원 후보 도서의 `aiExternalTransferAllowed`가 `false`이거나 지원 데이터 정책 프로필이 누락·불일치하면
  사전 검증에서 전체 적재를 실패시키고 Embeddings API를 호출하지 않으며 AI 경로 지원으로 활성화하지
  않습니다.
- 하나라도 실패하면 DB를 변경하지 않고 기존 시연 콘텐츠를 유지합니다. AI 경로 지원 여부 외의 일반 콘텐츠 공개
  상태, 운영 백오피스와 기존 사용자 데이터에 대한 재적재 삭제 정책은 MVP 범위에 포함하지 않습니다.
- 배치는 `content-import` 프로필의 비웹 애플리케이션으로만 실행합니다. 일반 서버 시작, HTTP 요청과
  `local`·`demo` 프로필은 변환을 자동 실행하지 않습니다.
- 구체적인 Poppler 경로와 로컬 실행 순서는 [배포 가이드](./deployment.md#콘텐츠-변환적재)를 따릅니다.

## 이미지 변환 기준

- 변환 도구는 Poppler `pdftoppm`을 사용하고 애플리케이션 요청과 분리된 사전 배치로 실행합니다.
- 허용 버전은 Poppler 26.05.0과 26.08.0입니다. 두 버전은 같은 표본의 JPEG·PNG 바이트와 픽셀 크기가
  같고, 26.05.0으로 만들어 둔 배치 이미지도 전부 바이트 단위로 동일합니다. 비교 결과는 아래
  [보존된 검증 근거](#보존된-검증-근거)에 있습니다.
- 목록에 버전을 더하려면 먼저 같은 표본을 그 버전으로 다시 변환해 결과를 비교하고 근거를 남깁니다.
  근거가 없는 버전은 넣지 않습니다 — 26.07.0을 넣지 않은 이유입니다.
- 한 버전으로 고정하지 않는 이유는
  [ADR-0015](./adr/content/0015-allow-verified-poppler-version-set.md)에 있습니다.
- 기본 출력은 150 DPI, RGB JPEG이며 `quality=85,optimize=y,progressive=y`를 사용합니다.
- 품질 85가 실패하면 품질 92, 150 DPI PNG, 200 DPI PNG 순서로 다시 검증합니다.
- 채택한 형식·DPI·품질과 원본 SHA-256은 변환 결과 manifest에 기록합니다.

## 배치 식별자와 결과

선택한 입력 manifest 파일 바이트의 SHA-256을 결정적 콘텐츠 배치 식별자로 사용합니다. 기본 출력은
`var/content/pages/<manifestSha256>/`이고 이 디렉터리의 `manifest.json`에는 입력 manifest SHA,
콘텐츠 버전, Poppler 버전, 이미지 형식·DPI·품질, 도서별 원본 SHA와 실제 페이지 결과 수를 기록합니다.
`ai-route-v2` 결과에는 입력 manifest에 고정한 `dataPolicyVersion`, 임베딩 모델·차원, 페이지별 분석 입력
SHA-256과 생성한 임베딩 벡터의 SHA-256도 기록합니다.

배포 시 확인한 실제 OpenAI 프로젝트 ID와 데이터 제어 상태는 콘텐츠 바이트가 아니므로 결정적 결과
manifest에 넣지 않습니다. 콘텐츠가 요구하는 `dataPolicyVersion`은 입력 manifest에 포함하며, 이 값이나
임베딩 모델·차원·분석 입력이 바뀌면 입력 manifest와 콘텐츠 배치 식별자가 함께 바뀝니다.

변환 중에는 같은 출력 루트의 숨김 staging 디렉터리만 사용합니다. 전체 검증이 끝나면 staging
디렉터리를 최종 배치 식별자 경로로 이동합니다. 같은 manifest 결과가 이미 있으면 결정적 결과 manifest와
manifest가 선언한 모든 IMAGE 파일의 바이트가 같은 경우에만 재사용하고, 하나라도 다르면 DB 적재 전에
실패합니다.

## 변환 완전성

- manifest의 `bookId`는 중복 없이 선택한 콘텐츠 버전의 시연 도서와 일치해야 합니다.
- `initial-v1`은 전체 400페이지여야 합니다. `ai-route-v2`의 도서 구성·페이지 범위·기존 콘텐츠 보존 조건은
  [AI 경로 콘텐츠 코퍼스의 완료 조건](./ai-route-content-corpus.md#완료-조건)을 따르며, 전체 페이지 수는
  manifest 합계와 일치해야 합니다.
- 각 PDF의 SHA-256과 페이지 수가 manifest와 일치해야 합니다.
- 원본 PDF 페이지 수와 생성한 페이지 콘텐츠 수가 같아야 합니다.
- 모든 페이지가 `TEXT` 또는 `IMAGE` 중 정확히 하나의 형식을 가져야 합니다.
- `TEXT`는 공백만으로 구성되지 않은 본문만, `IMAGE`는 공백이 아닌 경로가 가리키는 0바이트 초과 파일만
  가져야 하며 다른 형식의 값은 없어야 합니다.
- 모든 페이지 번호가 1부터 중복·누락 없이 연결되는지 자동 검증합니다.
- `ai-route-v2`의 모든 지원 페이지에는 분석 텍스트, 검수된 공개 가이드 주제, 예상 독서 시간 입력값과
  선언한 모델·차원의 임베딩이 정확히 하나씩 있어야 합니다. 선수 관계 목록과 중복 그룹 소속 목록 필드는
  페이지마다 정확히 하나씩 있어야 하지만 구성원은 각각 0개 이상이며, 선수 페이지가 없는 루트나 의미상
  고유한 페이지는 빈 목록을 사용합니다.
- 선수 관계는 같은 도서·콘텐츠 버전의 존재하는 페이지를 `선수 페이지 -> 의존 페이지` 방향으로만
  참조해야 합니다. 자기 참조, 다른 도서·콘텐츠 버전 참조 또는 방향 순환이 하나라도 있으면 위상 정렬
  검증에서 전체 적재를 실패시켜야 합니다.
- AI 경로 지원은 위 콘텐츠 완전성과 외부 전송 권리·데이터 보관 조건을 모두 통과한 비소설 도서에만
  허용합니다.
- 모든 지원 후보 도서에는 `aiExternalTransferAllowed=true`, manifest에는 지원하는 `dataPolicyVersion`이
  있어야 합니다. 하나라도 누락되거나 배포 환경의 프로필과 다르면 Embeddings API 호출 전에 실패해야 합니다.
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
- 이미지 충실도 실패는 변환 조건을 조정합니다. `1280×800` 데스크톱 뷰어 표시 검증은 같은 표본으로
  수행하며, SCRUM-406 결과는 `docs/evidence/content-quality/quality-samples.json`의
  `viewerInspection`에 기록합니다.

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

### Poppler 26.05.0 → 26.08.0 기준 갱신

기준 버전을 올리면서 위 표본을 같은 명령으로 다시 변환해 비교했습니다.

| 항목 | 26.05.0 | 26.08.0 |
| --- | --- | --- |
| JPEG 바이트 | 195,313 | 195,313 |
| PNG 바이트 | 266,163 | 266,163 |
| 픽셀 크기 | 875×1,241 | 875×1,241 |

표본 외에 26.05.0으로 만들어 둔 기존 배치 산출물의 **이미지 100장 전부를 26.08.0 결과와 바이트 단위로
비교했고 모두 동일**했습니다. 실제 `initial-v1` 100권 400페이지 변환·적재도 26.08.0으로 통과했습니다.
위 26.05.0 기록은 그때의 측정값이므로 고치지 않고 남깁니다.

배치 디렉터리는 입력 manifest의 SHA-256으로만 이름이 정해지므로, 도구 버전을 올리면 같은 디렉터리에
이전 버전 결과가 남아 있어 `verifyExistingBatch`가 결과 manifest의 버전 문자열 차이로 실패합니다.
이미지가 같아도 걸리므로, 버전을 올린 뒤 처음 변환할 때는 이전 배치 디렉터리를 비켜 두고 실행합니다.

현재 근거는 단일 표본의 변환 기준을 재현하기 위한 자료입니다. 전체 도서와 실제 뷰어 품질이 이미
검증됐다는 의미는 아니며, 전체 데이터 적재 전
[테스트 전략](./test-strategy.md#8-변환과-ai-경로-품질-확인)의
게이트를 통과해야 합니다.
