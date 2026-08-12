# 3단계 새 상태 인프라 없는 1차 개선 결과

## 최종 판정

- 단계 결과: 통과
- 기준 Git SHA: `4736e95c3ed2772e222f27221976f888d959793f`
- control JAR SHA-256:
  `911626816238389c8b7c7c8d30a97fb53fddb7f056a4d740ad4d7d33a7fb6ed6`
- 채택 후보:
  - `page_rental(reader_id, book_page_id, rented_at DESC, id DESC)`
  - `ink_ledger(reader_id, occurred_at DESC, id DESC)`
- 최종 판정: **이력 조회 복합 인덱스 2개 채택**
- 범위: 로컬 Compose의 백엔드 코드 회귀 비교. 운영 SLO·운영 용량 근거가 아니다.

첫 판정은 전체 HTTP p95 15%를 인덱스의 필수 채택 하한으로 적용해 두 후보를 제거했다. 이후 검토에서
실행 계획은 `history-heavy`에서 확인했지만 HTTP Average 직전 `reset-mvp.sh`가 이력 데이터를 제거한 사실과,
변경 후 계획이 단순 `SELECT id` probe였다는 한계를 확인했다. 고정 15% 하한은 인덱스 채택 조건에서
제외하고, 동일 `history-heavy` 데이터의 실제 제품 SQL·분리 endpoint·mixed 회귀를 다시 측정했다.

재검증에서 기본 optimizer가 두 인덱스를 실제 선택했고, 제품 SQL 중앙 실행 시간이 각각 약 25.5배,
42.2배, 4.9배 빨라졌다. 분리 API 3회 중앙값도 서재 p95 17.8%, 원장 p95 14.1% 개선됐으며 mixed
Average에서 오류·dropped iteration·Hikari·신규 대여·도메인 불변식 회귀가 없었다. 따라서 실행 계획과
이력 성장 효과를 주 근거로 두 인덱스를 채택한다.

## 현재 HEAD control

첫 유효 control은 새 `mvp` 데이터와 새 애플리케이션에서 Smoke 뒤 3분 Warm-up을 수행하고, Warm-up 표본을
버린 뒤 10분 Average를 실행했다.

| 항목 | 결과 |
| --- | ---: |
| Git SHA | `4736e95c3ed2772e222f27221976f888d959793f` |
| dirty | `false` |
| JAR SHA-256 | `911626816238389c8b7c7c8d30a97fb53fddb7f056a4d740ad4d7d33a7fb6ed6` |
| 실행 UTC | 2026-08-12 01:48:50~01:58:53 |
| 실제 HTTP RPS | 29.220207 |
| p50 / p95 / p99 | 4.104ms / 17.168ms / 82.185ms |
| 공개 / 인증 조회 / 신규 대여 p95 | 6.371ms / 7.256ms / 16.818ms |
| checks | 31,940 / 31,940 |
| HTTP 오류율 / dropped iteration | 0 / 0 |
| generator CPU 평균 / 최대 | 2.252% / 3.99% |
| JVM process CPU 평균 / 최대 | 9.644% / 29.10% |
| Hikari pending 최대 | 0 |

Prometheus process CPU와 Hikari pending 표본은 각각 121개였고 status는 `ok`였다. 잔액 불일치·음수 잔액·
대여 없는 차감·중복 소장·활성 대여·소장 서재 연결 위반은 모두 0이므로 이 실행은 기본 MVP control로
유효하다.

## 첫 control 실행 무효 판정

처음에는 MySQL과 애플리케이션만 준비하고 Prometheus·MySQL exporter를 기동하지 않았다. Smoke는 exit
code 0, checks 32/32, HTTP 오류 0이었으나 `curl: (7) Failed to connect to 127.0.0.1 port 9090`이
발생했고 `prometheus-summary.json`은 `status=unavailable`, `reason=prometheus-not-ready`를 기록했다.

이어진 Warm-up도 같은 이유로 관측 근거가 없어서 68초 시점에 중단했다. exit code는 130이며 두 실행의
기능 수치는 비교에 사용하지 않았다. 이후 전체 관측 환경을 기동하고 데이터·애플리케이션을 새로 복원해
위 유효 control을 실행했다.

## 초기 개별 실험과 판정 정정

### `page_rental`

최초 probe에서 변경 전은 reader index 실제 500행과 page index 실제 6행을 교차해 2행을 얻고 정렬한 뒤
1행을 반환했다. 변경 후 새 인덱스를 사용해 첫 행에서 멈추고 정렬을 제거했다. probe 시간은
`3.67ms → 0.0058ms`였지만 기본 MVP Average p95가 `17.168ms → 18.204ms`로 6.04% 악화돼 처음에는
폐기했다.

그러나 해당 Average는 probe의 `history-heavy` 데이터를 `reset-mvp.sh`로 지운 뒤 실행됐고, 인덱스와
무관한 공개 조회 p95도 함께 악화됐다. 따라서 이 수치는 인덱스의 악화 근거가 아니며 초기 폐기 판정을
철회한다.

### `ink_ledger`

최초 probe에서 변경 전은 506행을 읽고 정렬해 20행을 반환했고, 변경 후 새 인덱스에서 20행만 읽었다.
probe 시간은 `0.71ms → 0.0196ms`였다. 기본 MVP Average 전체 p95는 2.76%, 인증 조회 p95는 3.23%
개선됐지만 15% 하한에 미달해 처음에는 폐기했다.

이 후보도 실행 계획과 HTTP 측정 데이터가 달랐고, 전체 인증 조회에는 서재·잔액·원장 세 endpoint가
섞였다. 따라서 고정 15% 미달만으로 폐기한 판정을 철회한다.

## 동일 조건 재검증

재검증 상세 수치와 실행 계획 해석은 [history-index-revalidation.md](./history-index-revalidation.md)에
기록했다.

### 실제 제품 SQL

같은 `history-heavy` DB에서 두 인덱스를 만들고 `INVISIBLE`을 control, `VISIBLE`을 candidate로 사용했다.
데이터는 바꾸지 않고 세션의 `use_invisible_indexes`만 교차해 각 제품 SQL을 31회씩 실행했다.

| 제품 SQL | control 실행 계획 | candidate 실행 계획 | root actual time 중앙값 | 개선 배수 |
| --- | --- | --- | ---: | ---: |
| 최신 대여 엔티티 조회 | 500행+6행 index 교차, 2행 정렬, 1행 반환 | 새 index에서 1행 읽고 반환, 정렬 없음 | 0.0745 → 0.00292ms | 25.5배 |
| 실제 서재 전체 SQL | 상관 서브쿼리 loop마다 501행 읽기 | 새 index로 loop마다 3행 읽기 | 0.604 → 0.0143ms | 42.2배 |
| 실제 원장 목록 SQL | 506행 읽기·정렬, 20행 join | 새 index에서 20행만 읽고 join, 정렬 없음 | 0.305 → 0.0623ms | 4.9배 |

서재 SQL은 `library_entry` 전용 index가 아니라 상관 서브쿼리의 `page_rental` index 효과다. 기존
`uk_library_entry_reader_book`는 독자당 실제 1행을 읽으므로 별도 entry index는 추가하지 않았다.
서재 상관 서브쿼리에는 MySQL의 작은 top-N Sort가 남지만 입력이 loop당 501행에서 3행으로 줄었다. 세
계획 모두 임시 테이블은 발생하지 않았다.

### 분리 API 3회

서재와 원장을 각각 10 iteration/s로 2분간 분리하고, 매 실행 새 앱과 버리는 30초 Warm-up을 사용했다.
control/candidate 모두 동일한 `history-heavy` DB이며 인덱스 visible 상태만 다르다.

| endpoint | control p95 3회 | candidate p95 3회 | 중앙값 변화 |
| --- | --- | --- | ---: |
| `/api/library` | 9.058 / 9.160 / 8.167ms | 7.410 / 7.448 / 7.651ms | 9.058 → 7.448ms, 17.8% 개선 |
| `/api/ink/ledger?page=1` | 9.101 / 9.161 / 8.207ms | 7.815 / 7.871 / 7.750ms | 9.101 → 7.815ms, 14.1% 개선 |

p50 중앙값은 서재 `7.416 → 5.862ms`(21.0%), 원장 `7.437 → 6.011ms`(19.2%) 개선됐다. 여섯 실행
모두 checks 100%, HTTP 오류율 0, dropped iteration 0, Prometheus `ok`, Hikari pending 최대 0이었다.

### history-heavy mixed 회귀

두 상태를 각각 결정적으로 `reset-mvp → history-heavy`로 만들고 새 앱·1분 Warm-up 뒤 동일 3분 Average를
실행했다. 이 결과는 채택 하한이 아니라 읽기 외 흐름과 쓰기 경로의 회귀 감시다.

| 지표 | control | candidate | 변화 |
| --- | ---: | ---: | ---: |
| 실제 HTTP RPS | 29.528 | 29.530 | 동일 |
| 전체 p50 | 5.575ms | 5.376ms | 3.57% 개선 |
| 전체 p95 | 23.035ms | 22.264ms | 3.35% 개선 |
| 전체 p99 | 85.264ms | 85.721ms | 0.54% 악화 |
| 인증 조회 p95 | 10.420ms | 10.016ms | 3.88% 개선 |
| 신규 대여 p95 | 22.989ms | 21.610ms | 6.00% 개선 |
| checks | 9,644/9,644 | 9,644/9,644 | 모두 통과 |
| HTTP 오류율 / dropped | 0 / 0 | 0 / 0 | 동일 |
| Hikari pending 최대 | 0 | 0 | 동일 |
| JVM process CPU 평균 / 최대 | 13.69% / 31.11% | 13.63% / 30.10% | 회귀 없음 |

두 실행 뒤 도메인 불변식 6종은 모두 0이었다. 전체 p99의 0.54% 차이는 단일 짧은 mixed 실행의 자연
변동 범위로 보고, endpoint 3회·제품 SQL 31회의 일관된 개선과 신규 대여 p95 비회귀를 함께 근거로
채택한다.

## 정확성·오류·자원 공통 결과

| 실험 | checks | HTTP 오류율 | dropped | Prometheus | Hikari pending 최대 | 도메인 불변식 6종 |
| --- | ---: | ---: | ---: | --- | ---: | ---: |
| 유효 HEAD control Average | 31,940/31,940 | 0 | 0 | `ok` | 0 | 모두 0 |
| 최초 page_rental Average | 31,932/31,932 | 0 | 0 | `ok` | 0 | 모두 0 |
| 최초 ink_ledger Average | 31,945/31,945 | 0 | 0 | `ok` | 0 | 모두 0 |
| 분리 endpoint control/candidate 6회 | 회당 2,461~2,462/동일 | 0 | 0 | 모두 `ok` | 0 | 모두 0 |
| history-heavy mixed control/candidate | 각 9,644/9,644 | 0 | 0 | 모두 `ok` | 0 | 모두 0 |
| 결합 Average 유효 3회 | 각 31,945/31,945 | 0 | 0 | 모두 `ok` | 0 | 모두 0 |
| 결합 Peak 무효 1회 | 79,520/79,521 | 0 | 0 | `ok` | 3 | 모두 0 |
| 결합 Peak 유효 3회 | 79,520~79,525/동일 | 0 | 0 | 모두 `ok` | 0 | 모두 0 |
| 최종 패키징 Smoke | 32/32 | 0 | 0 | `ok` | 0 | 모두 0 |

무효 Peak의 check 1건과 Hikari pending 순간 최대 3은 위에 기록한 Warm-up·본 측정 계정 pool 중복
실행에서만 발생했다. 같은 JAR을 겹치지 않는 pool로 처음부터 다시 실행한 유효 Peak 3회에서는 둘 다
0이었다. 실행 계획 31회 비교와 분리 endpoint는 읽기 전용이며, 각 묶음 전후 SQL 불변식도 모두 0이었다.

## 다른 후보 판정

### BCrypt — 변경 없음

로그인 요청은 `ReaderService.authenticate`에서 `PasswordEncoder.matches`를 한 번 호출하고, k6 인증·소장·
대여 흐름은 같은 독자 세션을 재사용한다. 중복 검증이 없고 세션 재사용이 정상이므로 strength와 코드를
변경하지 않았다.

### 공개 정적 자산 재전송 — 백엔드 범위에서 제외

공개 정적 자산이 warm 탐색에도 329,581 bytes 재전송되는 현상은 확인됐지만 fingerprinting과 브라우저
cache 개선은 프론트엔드 범위라 제외했다. 구현·측정·인계·티켓 제안은 하지 않았다. 보호 콘텐츠 API의
`private, no-store` 계약은 유지됐다.

## 구현

- 새 V4 migration에 두 복합 인덱스만 추가했다.
- MySQL 스키마 회귀 테스트는 migration 전 실패하고 추가 후 정확한 컬럼·ASC/DESC 순서를 확인했다.
- 재현 가능한 분리 측정을 위해 `history-index` k6 scenario와 실행 문서를 추가했다.
- 별도 `library_entry` index, 새 의존성, 캐시, Redis, SQL·도메인 코드 변경은 없다.

## 결합 Average·Peak 각 3회

판정 정정으로 두 인덱스를 채택했으므로 RUNBOOK 3단계 종료 조건을 다시 적용했다. 각 회차를
`mvp 복원 → 새 앱 → Smoke → 3분 Warm-up → 10분 본 측정 → 불변식 검증` 순서로 실행했다. 비교 기준은
2단계의 유효 Average·Peak 각 3회다. 2단계와 최종 후보의 Git SHA·JAR이 다르므로 이 표는 결합 코드의
회귀 비교이고, 인덱스 단독 효과는 같은 DB를 on/off한 제품 SQL·분리 endpoint 결과로 판정한다.

| 부하 | 실행 | 실제 HTTP RPS | p50 | p95 | p99 | checks | 오류 / dropped | Hikari pending 최대 |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Average | r1 | 29.222 | 3.769ms | 14.583ms | 84.499ms | 31,945/31,945 | 0 / 0 | 0 |
| Average | r2 | 29.222 | 3.961ms | 14.913ms | 83.870ms | 31,945/31,945 | 0 / 0 | 0 |
| Average | r3 | 29.318 | 3.722ms | 15.637ms | 84.434ms | 31,945/31,945 | 0 / 0 | 0 |
| Average | 중앙값 | 29.222 | 3.769ms | 14.913ms | 84.434ms | - | 0 / 0 | 0 |
| Average | 2단계 중앙값 | 29.218 | 4.254ms | 18.171ms | 85.706ms | - | 0 / 0 | 0 |
| Peak | r1 | 72.515 | 2.252ms | 10.400ms | 77.994ms | 79,520/79,520 | 0 / 0 | 0 |
| Peak | r2 | 72.515 | 1.934ms | 13.170ms | 80.673ms | 79,520/79,520 | 0 / 0 | 0 |
| Peak | r3 | 72.518 | 1.901ms | 9.329ms | 76.141ms | 79,525/79,525 | 0 / 0 | 0 |
| Peak | 중앙값 | 72.515 | 1.934ms | 10.400ms | 77.994ms | - | 0 / 0 | 0 |
| Peak | 2단계 중앙값 | 72.515 | 2.441ms | 10.388ms | 76.239ms | - | 0 / 0 | 0 |

Average 중앙값은 2단계보다 p50 11.4%, p95 17.9%, p99 1.5% 개선됐다. Peak 중앙값은 p50 20.8%
개선됐고 p95 0.1%, p99 2.3% 높아 사실상 비회귀 범위다. 모든 유효 실행은 Prometheus `ok`, HTTP 오류·
dropped iteration 0, Hikari pending 최대 0이며 실행 뒤 도메인 불변식 6종도 모두 0이었다.

이 단계에서 별도로 고정한 현재 HEAD control 단일 Average p95 `17.168ms`와 결합 3회 중앙값
`14.913ms`를 보조 비교하면 13.1% 개선이다. control이 1회뿐이므로 이 수치는 3회 대 3회 비교로
과장하지 않고, 위 2단계 반복 기준선과 같은 DB on/off 재검증을 주 근거로 사용한다.

첫 Peak 시도 `20260812T045006Z-stage3-combined-peak-r1`은 checks 79,520/79,521로 무효다. 앞선 Warm-up과
본 측정이 별도 k6 process여서 VU iteration이 다시 0부터 시작했고, 같은 신규 독자·페이지 1건을 재사용해
정상적인 `deductedInk=0`을 시나리오가 신규 대여의 `1`로 기대했다. HTTP 오류와 DB 불변식은 0이었지만
좋은 지연 수치를 채택하지 않았다. 이후 Smoke, Warm-up, 본 측정의 신규 독자 offset을 서로 겹치지 않게
분리하고 Peak 3회를 처음부터 유효하게 다시 실행했다.

인덱스 채택은 고정 15% 전체 p95 하한이 아니라 읽은 행 수·정렬·제품 SQL 개선 배수와 endpoint 비회귀를
근거로 한다. RUNBOOK의 4단계와 최종 5단계 시나리오는 실행하지 않았다.

## 최종 검증

- migration 전 `HistoryLookupIndexMigrationTest`를 실행해 두 index 부재로 실패하는 것을 확인한 뒤 V4를
  추가했다.
- 대상 MySQL 테스트 6종과 migration 테스트는 `BUILD SUCCESSFUL in 7s`로 통과했다.
- 첫 전체 `./gradlew test --rerun-tasks`는 V4 SQL 주석 수정 전에 적용된 로컬 테스트 DB checksum이 남아
  Flyway `Migration checksum mismatch for migration version 4`로 무효였다. 아직 미커밋인 최초 V4이므로
  개발 DB나 성능 DB는 건드리지 않고 `ilgeobolkka_test`만 재생성했다.
- 전체 테스트 재실행: 679건, 성공 678건, skip 1건, 실패·오류 0건.
- `./gradlew check`: `BUILD SUCCESSFUL in 513ms`.
- `./gradlew build`: `BUILD SUCCESSFUL in 770ms`.
- 최종 실행 JAR: `build/libs/Ilgeobolkka-0.0.1-SNAPSHOT.jar`, 87,823,059 bytes,
  SHA-256 `98b375a4bc14a290f445d5ef4c8ae81be7a2a19f9d13b8f943dc9c25b20ec843`.
- 패키징 애플리케이션 Smoke: checks 32/32, HTTP 오류 0, dropped iteration 0, Prometheus `ok`, Hikari
  pending 최대 0, 도메인 불변식 6종 0. 보호 콘텐츠 `private, no-store` check도 3/3 통과했다.
- 테스트 DB와 새로 복원한 성능 DB 모두 Flyway V1~V4가 success이며, 두 복합 index는 정확한 열 순서로
  `VISIBLE`이다.
- 폐기 migration·테스트·DB 상태는 남지 않았다. 채택 V4와 그 회귀 테스트·재현 scenario만 남겼다.
- `docs/implementation/performance/RUNBOOK.md`는 수정하지 않았다.

원시 결과 위치와 SHA-256은 [artifact-manifest.md](./artifact-manifest.md)에 기록했다.
