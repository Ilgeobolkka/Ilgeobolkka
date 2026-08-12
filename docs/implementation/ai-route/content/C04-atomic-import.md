# C04 AI 콘텐츠 원자적 적재

[구현 작업 색인](../README.md)으로 돌아갑니다.

- 권장 담당: 파동 6 / 담당 A
- 선행: [C03 콘텐츠 embedding](./C03-content-embeddings.md), [F02 JPA 매핑](../foundation/F02-jpa-mapping.md)
- 후속: [Q01 평가 runner](../release/Q01-evaluation-runner.md)

## 목표

기존 초기 fixture 적재를 보존하면서 검증·embedding이 끝난 `ai-route-v2`의 Book·BookPage·선수 관계를
한 MySQL 트랜잭션으로 반영하고 지원 상태를 false로 유지합니다.

## 정본 링크

- [콘텐츠 변환과 적재 경계](../../../content-conversion.md#변환과-적재-경계)
- [ERD 콘텐츠 저장 규칙](../../../erd.md#콘텐츠-저장-규칙)
- [ERD AI 목표 트랜잭션](../../../erd.md#목표-트랜잭션과-삭제-경계)
- 필수 시나리오: [T-AIR-011·015](../../../test-strategy.md#5-필수-시나리오)

## 현재 구현 기준선

- 이 절은 착수 시점 기록입니다. **구현 조건 1~7을 구현했습니다.**
  [AiRouteContentWriter](../../../../src/main/java/com/example/ilgeobolkka/contentimport/AiRouteContentWriter.java)가
  AI 메타데이터·선수 관계를 한 트랜잭션으로 반영하고,
  [BookPage.updateStructuralPageMetadata](../../../../src/main/java/com/example/ilgeobolkka/book/entity/BookPage.java)가
  목차처럼 후보가 아니면서 분석 메타데이터는 가지는 페이지를 표현합니다.
- `ContentBatchConverter`는 콘텐츠 버전별로 manifest 계약을 분기하고 변환에 필요한 값만 뽑습니다.
  `ai-route-v2`는 권수를 세지 않아 확장 중의 부분 집합도 변환합니다. 정본 20권 569페이지를 실제
  Poppler로 변환하는 검사는 `RUN_CONTENT_IMPORT_INTEGRATION=true`에서 돕니다.
- `ContentPageWriter`도 콘텐츠 버전별로 나뉩니다. `ai-route-v2`는 권수를 세지 않고 manifest에 든
  도서만 적재하며 `total_page_count`를 새 값으로 올립니다. 시연 도서는 여기서 만들지 않습니다 —
  `ensureBooks`가 기존 도서와 시드를 비교하는데, 이 적재가 페이지 수를 올리고 나면 두 번째 실행부터
  반드시 어긋나기 때문입니다. 도서는 앞선 시연 데이터 단계에서 만들어져 있어야 합니다.
- `AiRouteContentImporter`가 검증 → embedding → 적재를 잇습니다. 파일 I/O와 Embeddings 호출은
  트랜잭션 **밖**에서 끝내고 본문 페이지와 AI 메타데이터는 **한 트랜잭션**에 함께 씁니다. 준비와 쓰기를
  두 메서드로 나눈 것은 같은 빈 안에서 부르면 프록시를 거치지 않아 트랜잭션이 걸리지 않기 때문입니다.
- 평가 데이터 경로는 `content-import.evaluation` 설정으로 받습니다.
- **남은 것**: 실제 실행. 후보 페이지마다 Embeddings를 호출하므로 OpenAI 키·프로젝트·지출 한도가 필요하고,
  언제 어느 환경에서 돌릴지는 [배포 절차](../../../deployment.md#콘텐츠-변환적재) 결정입니다.
- [ContentImportService](../../../../src/main/java/com/example/ilgeobolkka/contentimport/ContentImportService.java)는
  변환 batch를 DB writer에 전달합니다.
- [ContentPageWriter](../../../../src/main/java/com/example/ilgeobolkka/contentimport/ContentPageWriter.java)는
  기존 page ID를 보존하며 초기 콘텐츠를 transaction으로 씁니다.
- 현재 `ContentBatchConverter`의 100권·400페이지 상수는 초기 fixture 전용입니다.

## 입력과 산출물

- 입력: 기존 TEXT/IMAGE 변환 결과 + C03의 `EmbeddedAiRouteContent`
- 산출물: version별 import command와 AI metadata writer
- DB 결과: 같은 contentVersion의 Book·BookPage AI 필드·AiRoutePrerequisite 전체, 지원 false
- BookPage의 `ai_route_candidate`는 manifest의 `aiRouteCandidatePage`를 그대로 저장합니다
- Q01에 넘길 것: manifest·DB model/dimensions/profile이 일치하는 평가 후보 DB

## 수정 허용 파일

- 기존 `ContentBatchConverter`, `ContentImportService`, `ContentPageWriter`, 필요한 contentimport 전용 타입
- F02 Entity의 새 API가 필요하면 직접 수정하지 않고 F02 담당자에게 요청
- 새 `AiRouteContentImportMySqlIntegrationTest`, 기존 contentimport 테스트 보강

## 구현 조건

1. `contentVersion`별로 `initial-v1` 100권·400페이지와 `ai-route-v2` manifest 합계 계약을 분기합니다.
2. TEXT/IMAGE 파일은 새 version staging에 완성한 뒤 DB가 참조하게 합니다.
3. 예상하지 않은 기존 page는 삭제하지 않고 전체 실패하며 `(bookId,pageNumber)` ID를 보존합니다.
4. Book contentVersion·권리·policy·support false, BookPage의 모든 AI 필드와 prerequisite를 한 transaction에
   반영합니다.
5. AI metadata·vector·edge 중 일부가 없거나 C03의 key가 page와 다르면 transaction 시작 전 실패합니다.
   vector 유무는 `ai_route_candidate`로 판정합니다. `1`인 page는 ERD가 요구하는 임베딩 세 필드를 모두
   가져야 하고, `0`인 page는 세 필드가 모두 비어 있어야 하며 어긋나면 실패합니다.
6. 실패 시 기존 DB와 기존 공개 파일 reference를 유지하고 새 staging을 공개 경로로 승격하지 않습니다.
7. evaluation 정답은 Repository나 BookPage에 저장하지 않습니다.

## 테스트

- 정상 초기 fixture 적재 회귀와 정상 최소 AI fixture 원자적 적재
- 중간 writer 실패·FK 실패·예상 외 기존 page에서 전체 rollback
- 적재 전후 기존 BookPage.id 보존과 support false 확인
- 목차 page가 `ai_route_candidate=0`과 빈 임베딩 세 필드로 저장되고, 후보 page만 vector를 갖는지 확인
- 잘못된 vector/edge/profile 입력에서 DB 변경 0건
- 명령: `./gradlew test --tests '*AiRouteContentImportMySqlIntegrationTest' --tests '*ContentImport*'`

## 제외 범위

- 실제 90권 PDF·분석 원고 제작
- 품질 평가와 지원 true 활성화
- 사용자 데이터가 있는 DB의 콘텐츠 교체·rollback

## 완료 조건

- 초기와 AI fixture가 서로 다른 계약으로 같은 content-import profile에서 동작합니다.
- 정상 AI 적재는 완전한 한 version, 실패는 DB 변화 0건입니다.
- 기존 contentimport 회귀와 `./gradlew check`, `./gradlew build`가 통과합니다.

## 인계

Q01 담당자에게 적재 명령, contentVersion, manifest·DB 일치 확인 쿼리와 support false 증거를 전달합니다.
