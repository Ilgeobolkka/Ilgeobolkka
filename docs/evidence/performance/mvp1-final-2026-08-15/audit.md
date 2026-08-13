# 6단계 완료 감사와 로컬 인계

## 최종 판정

- Runbook 실행: **완료**
- 5단계: **조건부 통과**
- 정확성·품질 게이트: **조건부 통과** — 첫 Spike 기능 check 1건 실패의 원인 미확인을 잔여 위험으로 유지
- 성능 목표: **기준선 충족으로 단순 구성 유지**
- 감사 시각: 2026-08-13 19:10 KST
- 감사 브랜치·HEAD: `codex/mvp1-performance-final-audit`,
  `c7e6ffbe114cd6058a4095f6ffae0504e809502e`
- 비교 기준: 로컬 `develop`의 `b5b4c589b5bf2b4aac94adb480f7f68a0e1c3905`
- 범위: 로컬 Docker 코드 회귀 근거와 저장소 변경 감사. 운영 SLO·운영 최대 RPS·AWS 용량 근거가 아니다.

6단계는 5단계의 원시 결과를 다시 생성하지 않고 보존된 evidence와 로컬 artifact를 감사했다. Average·Peak·
Stress·Spike·Soak·동시성·브라우저 부하는 재실행하지 않았다. 첫 Spike 실패, 같은 계획의 재실행 통과,
후속 진단 통과를 서로 다른 사실로 유지하며, 재실행과 후속 진단을 과거 실패의 원인 규명으로 해석하지
않는다.

## 잔여 위험과 재실행·후속 진단

- 첫 Spike `20260813T051801Z-final-spike`는 HTTP 오류·dropped 0이었지만 `잉크 차감 일치` check가
  73,353건 중 1건 실패해 exit code 99였다. 당시 로그로 실패 flow의 기대값과 실제값을 복원할 수 없어
  원인은 확인하지 못했다. 무효 실행으로 바꾸거나 삭제하지 않았다.
- 같은 JAR·계획에서 데이터를 복원한 `20260813T052817Z-final-spike-rerun`은 checks
  73,356/73,356, HTTP 오류·dropped 0으로 통과했다. 이 재실행은 현재 경로의 통과 근거일 뿐 첫 실패를
  지우지 않는다.
- 계약 불일치 로그를 보강한 후속 통합 JAR의 `20260813T075738Z-diagnostic-spike-contract`와 Red/Green
  smoke는 현재 경로 통과와 재발 시 진단 필드 기록을 확인했다. 정식 성능 수치나 과거 원인 규명에는
  사용하지 않는다.
- 첫 Soak 실패는 최대 VU 임시 확장 구간에서 신규 독자 slot이 충돌할 수 있는 `vuStride=6` 하네스
  조건으로 분류해 무효로 보존했다. 독립 slot으로 고친 뒤 30분 전체를 재실행했고 checks
  95,689/95,689, HTTP 오류·dropped 0으로 통과했다. 이 항목은 원인·수정·재검증이 연결돼 첫 Spike의
  원인 미확인 잔여 위험과 구분한다.

세부 수치와 판정은 [5단계 결과](./result.md), [최종 요약](./summary.md), 원시 위치와 해시는
[artifact manifest](./artifact-manifest.md)에 보존했다.

## 계획 완료 기준 1~10 감사

| 번호 | 판정 | 근거 |
| ---: | --- | --- |
| 1 | 충족 | [2단계 기준선 요약](../mvp1-baseline-2026-08-12/summary.md)에 고정 Git·JAR·환경의 Average·Peak 각 3회와 중앙값·범위가 있다. |
| 2 | 충족 | 기준선과 [최종 요약](./summary.md)에 공개 탐색·인증 조회·신규 대여·활성 재열람·소장 콘텐츠·보호 콘텐츠·동시성 4종이 연결돼 있다. 활성 재열람·소장 콘텐츠의 독립 지연은 사후 분리할 수 없다는 한계도 명시했다. |
| 3 | 충족 | [2단계 자원 지표](../mvp1-baseline-2026-08-12/dashboard.md), [JFR·SQL 진단](../mvp1-baseline-2026-08-12/diagnostics.md), [최종 자원 지표](./dashboard.md)가 실행 시간대와 연결된다. 최종 측정에서는 새 포화가 없어 JFR·실행 계획을 추가하지 않은 이유를 기록했다. |
| 4 | 충족 | 이력 성장 조회, 공개 자산 재전송, BCrypt CPU를 [2단계 결과](../mvp1-baseline-2026-08-12/result.md)와 진단에서 확인한 뒤 후보로 올렸다. |
| 5 | 충족 | 채택한 V4 복합 인덱스 2개는 [3단계 동일 DB 재검증](../mvp1-stage3-2026-08-12/history-index-revalidation.md), [3단계 결과](../mvp1-stage3-2026-08-12/result.md), [스키마 회귀 테스트](../../../../src/test/java/com/example/ilgeobolkka/support/schema/HistoryLookupIndexMigrationTest.java)로 변경 전후를 확인했다. |
| 6 | 조건부 충족 | 유효 부하와 동시성 4종 뒤 잔액·원장·대여·소장·마지막 위치·세션 불변식은 통과했다. 첫 Spike 기능 실패 원인 미확인을 위 잔여 위험으로 유지한다. |
| 7 | 조건부 충족 | 깨끗한 환경의 Average·Peak 각 3회, Stress, Spike 실패·재실행, Soak 무효·재실행, 동시성·브라우저 결과가 [최종 요약](./summary.md)에 있다. 실패와 무효를 숨기지 않는 조건으로 충족한다. |
| 8 | 충족 | [5단계 결과](./result.md)에 `test --rerun-tasks` 813건과 통합 뒤 878건, `check`, `build`, 패키징 JAR smoke 통과를 구분해 기록했다. 6단계는 제품 코드와 Java 테스트를 바꾸지 않아 재실행하지 않았다. |
| 9 | 충족 | [4단계 결과](../mvp1-stage4-2026-08-13/result.md)에 Caffeine·Redis cache·Spring Session Redis·다중 인스턴스·가상 스레드의 진입 조건 미충족과 Kafka 제외 이유를 기록했다. 새 dependency·container·config·ADR은 추가하지 않았다. |
| 10 | 충족 | [최종 비교](./comparison.md)가 기준선, 변경별 효과, 최종 결과, 남은 공개 자산 병목, 200 flow/s 초과 미측정 용량 경계를 분리한다. |

정확성·품질 게이트는 6번과 7번의 첫 Spike 잔여 위험 때문에 조건부 통과다. 이 조건을 숨긴 채 전체
게이트를 무조건 통과로 승격하지 않는다.

## 저장소 변경·민감정보 감사

- 감사 시작 시 작업 트리는 clean이었다. 브랜치에는 로컬 upstream이 설정돼 있지 않아 push·PR 상태를
  로컬 완료와 분리했다.
- `develop...HEAD`는 28개 파일, 6,276행 추가·4행 삭제다. 5단계 evidence 26개, 실행 계획 상태 문서 1개,
  k6 계약 불일치 진단 보강 1개로 구성되며 제품 Java·도메인·DB schema 변경은 없다.
- 코드 diff인 `performance/k6/lib/flows.js`는 실패 시 합성 flow·독자·도서·페이지·기대값·실제값과 k6
  iteration을 기록한다. 비밀번호·cookie·CSRF token·HTTP session ID는 기록하지 않는다.
- 변경 파일과 Git 대상에서 credential, 개인 이메일, private key, JWT, cookie/session header 패턴을
  검사해 일치 항목이 없었다. `.env`는 변수 존재 여부만 확인했고 값은 읽거나 출력하지 않았다.
- `.env`, `build/`, `var/performance/`는 Git 제외 상태다. 변경 파일에 1MiB를 넘는 새 raw artifact가 없고,
  원시 k6·Prometheus 결과와 JAR은 Git 대상에 포함되지 않았다.
- 6단계 감사 완료 시점의 미커밋 변경은 이 감사 문서와 상태·보존 경계 문서뿐이었다. 이 문서는 커밋 전
  상태를 기록하며, 감사 범위에서는 commit·push·PR·merge를 실행하지 않았다.

## 증거·해시·구문 감사

- 원시 결과 38개 디렉터리의 파일 합계 크기와 디렉터리 집계 SHA-256이
  [artifact manifest](./artifact-manifest.md)와 모두 일치했다.
- Git 보존 summary·JSONL·history-heavy 파일 20개의 SHA-256이 manifest와 모두 일치했다.
- 현재 패키징 JAR은 87,929,805 bytes,
  SHA-256 `d9adc84a30f9bf42c0a4f9f6b664c18f2fdc19f005d49589f293152bf6583c26`으로 manifest의 후속
  `develop` 통합 검증 JAR과 일치한다. 정식 성능 수치는 계속 측정 JAR
  `5c4e146b2a98d85cd4465b204a64660911dd72b3965b807791e868d66a2b9eb0`에만 귀속한다.
- Compose의 Java·MySQL·exporter·Prometheus·Grafana·k6 image 6개는 기록한 digest로 로컬에서 식별됐고
  `docker compose ... config --quiet`가 통과했다.
- 보존 JSON 17개와 JSONL 1개의 구문이 유효하다. Average·Peak 각 3회의 중앙값·범위, Stress 1회,
  Spike 실패·재실행, Soak 무효·재실행, 동시성 4종, 브라우저 3쌍의 수치가 원본과 문서에 일치했다.
- 로컬에 Node 실행 파일이 없어 `node --check`는 실행하지 못했다. 처음 두 k6 `inspect` 호출은 존재하지
  않는 script 경로를 지정해 실패했고, 실제 `/scripts/average-load.js`를 고정 k6 image로 다시 검사해
  통과했다. 이 검사는 부하를 실행하지 않았고 일회성 container는 자동 제거됐다.
- `git diff --check`, Markdown code fence, 로컬 상대 링크, Runbook이 가리키는 저장소 명령 경로를
  검사해 오류가 없었다.

## 최종 성능·구조 요약

- Average 중앙값: 실제 HTTP RPS 29.222004, p95 17.375ms, p99 85.170ms. 기준선 대비 p95 4.4%,
  p99 0.6% 개선이며 고정 arrival rate라 처리량 상한 개선 근거가 아니다.
- Peak 중앙값: 실제 HTTP RPS 72.514469, p95 10.735ms, p99 81.960ms. 기준선 대비 p95 3.3%,
  p99 7.5% 높지만 잠정 절대 게이트와 정확성 검증을 통과했다.
- Stress는 200 flow/s 계획 상한까지 오류·dropped·Hikari pending 없이 완료했지만 첫 포화 자원은
  확인하지 못했다. 다음 용량 경계는 특정 자원이 아니라 200 flow/s 초과 미측정 구간이다.
- Redis·다중 인스턴스·Spring Session Redis·가상 스레드는 진입 근거가 없고 Kafka는 채택 게이트가 없어
  제외했다. 근거 없는 상태 인프라를 추가하지 않고 단일 애플리케이션·MySQL 구성을 유지한다.
- 다음 1순위는 보호 콘텐츠 `private, no-store`를 유지하면서 fingerprinted 공개 정적 자산에만 장기 cache를
  실험하는 일이다. 현재 warm 탐색도 329,581 bytes를 재전송한다.

성능 목표의 30% 지연 단축 또는 1.5배 지속 처리량 증가는 달성하지 못했다. 현재 절대 지연·오류·자원
게이트를 만족하고 추가 상태 인프라의 진입 근거가 없으므로 Runbook의 대체 완료 판정인 **기준선 충족으로
단순 구성 유지**로 닫는다.

## 로컬 인계 상태

- 성능 project의 app·MySQL·exporter·Prometheus·Grafana container와 network를 중지·제거했다.
- `ilgeobolkka-performance_mysql-perf-data`, `ilgeobolkka-performance_prometheus-data` volume은
  보존했다.
- 개발 volume `ilgeobolkka_mysql-data`와 `var/performance/results/` 원시 artifact를 보존했다.
- 재개 시 [환경](./environment.md), [결과](./result.md), [요약](./summary.md),
  [artifact manifest](./artifact-manifest.md)를 먼저 읽고, 원시 경로의 크기·SHA-256을 대조한다.
- volume·원시 artifact 삭제, commit, push, PR 수정, merge는 각각 별도 승인 전에는 실행하지 않는다.
