# MVP1 기준선 진단 근거

진단 실행은 정식 Average·Peak 3회에 포함하지 않았다.

## history-heavy 고정 데이터

| 항목 | 값 |
| --- | ---: |
| 생성 입력 | 독자별 과거 대여 500건 |
| 생성 시간 | 5초 |
| DB 크기 | 265,273,344 bytes |
| reader / book / book_page | 1,000 / 100 / 400 |
| page_rental | 500,333 |
| ink_ledger | 506,333 |
| ink_purchase | 6,000 |

10분·2GB 경계 안에서 생성됐고 잔액·차감·소장·서재 불변식 6종이 모두 0이었다. 이후 이력 성장 비교는
`historyRentalsPerReader=500`을 고정 입력으로 사용한다.

## JFR

- 파일: `var/performance/jfr/stage2-history-heavy-20260811T1208Z.jfr`
- 구간: `20260811T120904Z-baseline-diagnostic-history-jfr`, 120초
- 부하 결과: checks 6,451/6,451, 오류 0, p50 5.282ms, p95 25.434ms, p99 78.829ms
- `jdk.ExecutionSample` 1,228개 중 BCrypt `key`가 880개(71.66%), `encipher`가 19개(1.55%)였다.
- JVM user CPU는 119개 표본 평균 7.12%, 최대 42.26%였다.
- GC pause는 21회, 합계 147.178ms, 최대 18.404ms로 전체 p99를 설명하는 장기 정지는 아니었다.

로그인 비밀번호 검증의 BCrypt cost를 낮추는 것은 보안 회귀이므로 개선 후보가 아니다. 5% 로그인 흐름과
세션 재사용 경계를 유지하면서 불필요한 재인증 여부만 후속 단계에서 확인한다.

## Performance Schema

digest를 비운 뒤 history-heavy에서 1분 격리 실행한 결과는 checks 3,270/3,270, 오류 0,
p50 4.209ms, p95 19.904ms, p99 77.964ms다.

| 제품 SQL | 호출 | 평균 | 최대 | examined/call |
| --- | ---: | ---: | ---: | ---: |
| 독자·페이지의 최신 대여 1건 | 785 | 0.6020ms | 3.4406ms | 1,906.86 |
| 서재의 마지막 페이지 최신 대여 상관 조회 | 80 | 1.9174ms | 4.4210ms | 1,511.00 |
| 독자 원장 최신순 목록 | 80 | 1.3398ms | 13.1831ms | 544.00 |
| 독자 원장 count | 80 | 0.2474ms | - | 507.00 |

단일 호출 지연은 잠정 게이트 안이지만, 이력 증가에 비례한 examined rows와 정렬은 복합 인덱스 후보의
근거다. 불변식 검증 SQL처럼 제품 요청이 아닌 진단 쿼리는 후보에서 제외했다.

## slow query와 실행 계획

0.2초 slow log의 5건은 history-heavy 생성 INSERT 2건과 불변식 verifier SELECT 3건이다. 격리된 제품
요청 중 0.2초를 넘은 SQL은 없었다.

대표 원장 최신순 조회 `EXPLAIN ANALYZE`는 `fk_ink_ledger_purchase(reader_id, ink_purchase_id)`를 사용해
567행을 읽고 `occurred_at DESC, id DESC`로 정렬한 뒤 20행을 반환했다. 실제 구간은 약
0.304~0.331ms였다. count는 같은 covering index에서 567행을 읽고 약 0.048ms였다. 개선 전후에는
동일 history-heavy 입력에서 actual rows·정렬·HTTP p95를 함께 비교한다.
