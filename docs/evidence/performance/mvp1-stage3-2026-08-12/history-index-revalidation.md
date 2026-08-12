# 이력 조회 복합 인덱스 재검증

## 재검증 이유

최초 실험은 `history-heavy`에서 단순 probe의 실행 계획을 확인한 뒤, HTTP Average 직전
`reset-mvp.sh`로 이력 데이터를 제거했다. 또한 변경 후 probe가 실제 repository 전체 SQL을 포함하지
않았다. 따라서 기본 MVP 전체 p95만으로 폐기한 판정을 취소하고 다음 조건으로 다시 측정했다.

- 행 수: `page_rental` 500,333, `ink_ledger` 506,333, `library_entry` 666
- 대표 독자: `100001` 대여 500·원장 506, `100002` 대여 501·원장 507·서재 1
- 동일 MySQL 8.4.11 container·동일 DB·동일 JAR
- control: 두 후보 index를 `INVISIBLE`로 두고 `use_invisible_indexes=off`
- candidate: 같은 index를 `use_invisible_indexes=on`으로 선택
- 제품 SQL 시간: 같은 MySQL session에서 control/candidate 순서를 교차해 각 31회, root actual time 중앙값
- endpoint: global visible 상태만 변경하고 매 실행 새 앱·버리는 30초 Warm-up·2분 본 측정

고정 15% 전체 p95는 인덱스 채택 조건에서 제외했다. 실제 제품 SQL에서 기본 optimizer가 새 index를
선택하고, 이력 증가에 비례하던 읽기와 정렬을 의미 있게 줄이며, 정확성·오류·쓰기 흐름에 회귀가 없으면
채택한다.

## 실제 제품 SQL 실행 계획

### 최신 대여 엔티티 조회

제품 repository 형태와 같은 엔티티 컬럼 조회를 사용했다.

control:

```text
Limit 1: actual rows=1
Sort rented_at DESC: expected rows=2, actual rows=1
Filter reader_id/book_page_id: expected rows=2, actual rows=2
Index range uk_page_rental_reader_id: expected rows=500, actual rows=500
Index range fk_page_rental_book_page: expected rows=2003, actual rows=6
```

candidate:

```text
Limit 1: actual rows=1
Index lookup idx_page_rental_reader_page_latest:
  expected matching rows=2, actual rows emitted=1 because LIMIT stopped the iterator
```

- 새 index를 실제 사용했다.
- 기존 `book_page_id` 단독 branch는 예상 2,003행과 실제 6행으로 약 334배 차이가 있었지만, 최종 교차
  예상 2행과 실제 2행은 일치했다.
- 읽기는 기존 reader 500행+page 6행 교차에서 새 index 첫 1행으로 줄었다.
- top-N Sort가 제거됐고 임시 테이블은 없었다.
- 31회 root actual time 중앙값: `0.0745ms → 0.00292ms`, 약 **25.5배**.

### 실제 서재 전체 SQL

`LibraryEntryRepository.findEntriesByReaderId`의 join과 최신 대여 상관 서브쿼리를 그대로 실행했다.

control의 상관 서브쿼리:

```text
Index lookup uk_page_rental_reader_id: expected rows=725, actual rows=501, loops=3
Filter book_page_id: actual rows=3, loops=3
Sort rented_at DESC, id DESC: actual rows=1, loops=3
```

candidate의 상관 서브쿼리:

```text
Covering index lookup idx_page_rental_reader_page_latest:
  expected rows=1.39, actual rows=3, loops=3
Sort rented_at DESC, id DESC: actual rows=1, loops=3
```

- 기존 `library_entry` 조회는 `uk_library_entry_reader_book`로 예상·실제 1행이었다.
- 별도 entry index가 아니라 `page_rental` 복합 index가 실제 병목 경로에 선택됐다.
- 상관 서브쿼리의 index 입력은 loop당 501행에서 3행으로 약 **167배** 줄었다.
- MySQL의 작은 top-N Sort는 남았지만 정렬 입력이 3행으로 제한됐고 임시 테이블은 없었다.
- 31회 전체 SQL root actual time 중앙값: `0.604ms → 0.0143ms`, 약 **42.2배**.

### 실제 원장 목록 SQL

`InkLedgerRepository.findEntriesByReaderId`의 목록 SQL과 동일한 세 `LEFT JOIN`을 포함했다.

control:

```text
Index lookup fk_ink_ledger_purchase: expected rows=506, actual rows=506
Sort occurred_at DESC, id DESC: expected rows=506, actual rows=20
세 LEFT JOIN: actual rows=20
```

candidate:

```text
Index lookup idx_ink_ledger_reader_occurred:
  expected matching rows=506, actual rows emitted=20 because LIMIT stopped the iterator
세 LEFT JOIN: actual rows=20
```

- 새 index를 실제 사용했다.
- 읽기·join 진입은 506행에서 20행으로 약 **25.3배** 줄었다.
- Sort가 제거됐고 임시 테이블은 없었다.
- 31회 전체 SQL root actual time 중앙값: `0.305ms → 0.0623ms`, 약 **4.9배**.
- 별도 count SQL은 페이지 전체 수 계산 때문에 계속 506행을 읽으며 기존 covering index를 사용한다. 새
  index의 채택 근거나 효과에 포함하지 않았다.

## 분리 endpoint 3회

각 본 실행은 서재·원장을 각각 10 iteration/s로 2분 실행했다. login/CSRF 준비 요청에는 measurement
tag를 붙이지 않아 endpoint 지연 통계에서 제외했다.

| endpoint | 상태 | r1 p95 | r2 p95 | r3 p95 | 중앙값 p95 | 중앙값 p50 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| `/api/library` | control | 9.058ms | 9.160ms | 8.167ms | 9.058ms | 7.416ms |
| `/api/library` | candidate | 7.410ms | 7.448ms | 7.651ms | 7.448ms | 5.862ms |
| `/api/ink/ledger?page=1` | control | 9.101ms | 9.161ms | 8.207ms | 9.101ms | 7.437ms |
| `/api/ink/ledger?page=1` | candidate | 7.815ms | 7.871ms | 7.750ms | 7.815ms | 6.011ms |

- 서재: p95 17.8%, p50 21.0% 개선.
- 원장: p95 14.1%, p50 19.2% 개선.
- 각 실행은 2,401~2,402 endpoint iteration을 완료했다.
- checks 실패 0, HTTP 오류율 0, dropped iteration 0.
- Prometheus status `ok`, Hikari pending 최대 0.

## history-heavy mixed 회귀

control/candidate마다 `reset-mvp → history-heavy → 새 앱 → 1분 Warm-up → 3분 Average`를 적용했다.
두 실행 모두 실제 HTTP RPS 약 29.53, checks 9,644/9,644, HTTP 오류·dropped iteration·Hikari pending
0이었다.

candidate는 control 대비 전체 p95 3.35%, 인증 조회 p95 3.88%, 신규 대여 p95 6.00% 개선됐다. 전체
p99만 0.54% 높았으며 신규 대여와 JVM CPU에는 회귀가 없었다. 실행 뒤 잔액 불일치·음수 잔액·대여 없는
차감·중복 소장·활성 대여·소장 서재 연결 위반은 모두 0이었다.

## 판정

- `page_rental` 복합 index: 채택.
  - 최신 대여 SQL 25.5배, 서재 전체 SQL 42.2배.
  - 서재 endpoint p95 17.8% 개선.
- `ink_ledger` 복합 index: 채택.
  - 원장 목록 SQL 4.9배, 읽기 25.3배 감소, Sort 제거.
  - 원장 endpoint p95 14.1% 개선.
- `library_entry` 전용 index: 미추가.
  - entry 자체는 실제 1행이며, 서재 병목은 `page_rental` 상관 서브쿼리였다.

두 인덱스는 이력 크기에 비례하던 읽기와 정렬을 제한하는 낮은 복잡도의 Flyway 변경이고, 실제 API와
쓰기·정확성 경계에서 회귀가 없어 채택한다.
