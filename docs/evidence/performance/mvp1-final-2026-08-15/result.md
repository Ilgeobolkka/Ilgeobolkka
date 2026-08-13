# 5단계 깨끗한 최종 재측정 결과

## 최종 판정

- 단계 결과: **조건부 통과**
- 진행 상태: **5단계 조건부 통과·6단계 감사 완료**
- 정확성·잠정 품질 게이트: **조건부 통과** — 첫 Spike의 기능 check 1건 실패를 잔여 위험으로 유지
- 성능 목표: **기준선 충족으로 단순 구성 유지**
- 기준 Git SHA: `f3a62809099ba660ce980b006d71c833bbd849fa`
- 최종 JAR SHA-256: `5c4e146b2a98d85cd4465b204a64660911dd72b3965b807791e868d66a2b9eb0`
- 채택 구성: V4 이력 조회 복합 인덱스 2개, 단일 애플리케이션·MySQL
- 제외 구성: Caffeine·Redis cache·Spring Session Redis·다중 인스턴스·가상 스레드·Kafka
- 범위: 로컬 Docker 코드 회귀 검증. 운영 SLO·운영 최대 RPS·운영 용량 근거가 아니다.

5단계는 성능 전용 MySQL·Prometheus volume만 재생성한 깨끗한 환경에서 시작했다. 정식 Average·Peak는
매회 동일 JAR로 `mvp 복원 → 새 앱 → Smoke → 3분 Warm-up → 10분 본 측정`을 수행했고, Stress·Spike·
Soak·동시성·브라우저·전체 빌드·패키징 부팅까지 실행했다. 다만 첫 Spike가 기능 check 1건 실패한 뒤
원인이나 설정 변경 없이 같은 계획의 재실행과 후속 진단이 통과했다. 첫 실패를 지우거나 원인을 규명한
것으로 해석하지 않고 잔여 위험으로 보존하는 조건으로 5단계를 닫는다. 새 병목이나 포화가 없어 JFR·
Performance Schema·`EXPLAIN ANALYZE` 진단은 정식 실행에 추가하지 않았다. 이력 인덱스의 분리 진단
근거는 3단계 evidence를 그대로 사용한다.

## 시나리오 결과

- Average 3회: checks 31,944~31,945건 모두 통과, 오류·dropped 0, p95 중앙값 17.375ms,
  p99 중앙값 85.170ms
- Peak 3회: checks 79,520~79,525건 모두 통과, 오류·dropped 0, p95 중앙값 10.735ms,
  p99 중앙값 81.960ms
- Stress: 10→25→50→100→200 flow/s 전 단계 완료, checks 142,452/142,452, 오류·dropped 0,
  p95 12.857ms
- Spike: 첫 실행 check 1건 실패, 재실행 checks 73,356/73,356·오류·dropped 0·p95 15.193ms;
  실패 원인 미확인을 잔여 위험으로 유지
- Soak: 30분 재실행 checks 95,689/95,689, 오류·dropped 0, p95 14.734ms, Hikari pending 0
- 동시성 4종: 모든 k6 check와 잔액·원장·대여·소장·마지막 위치·세션 계약 통과
- 브라우저: feature checks 15/15, 공개 자산 전송량 cold/warm 각각 329,581 bytes, 보호 콘텐츠
  `private, no-store` 유지

세부 수치와 브라우저 한계는 [summary.md](./summary.md), 같은 시간대 자원 지표는
[dashboard.md](./dashboard.md), 기준선·후보·최종 비교는 [comparison.md](./comparison.md)에 기록했다.

## 실패·무효 실행

### 첫 Spike — 실패 실행

첫 Spike `20260813T051801Z-final-spike`는 `잉크 차감 일치` check 1건이 실패해 exit code 99였다. JAR·
데이터·자원·MySQL이 바뀌거나 generator가 포화됐다는 증거가 없고, 상태 누출·외부 쓰기·secret 포함도
확인되지 않아 Runbook의 무효 조건에는 해당하지 않는다. 따라서 이 실행은 **무효가 아니라 기능 실패
실행**이다. 당시 `--quiet` 로그에는 실패 flow·독자·페이지·기대값·실제값이 없어 원인을 사후 복원할 수
없다. 데이터와 앱을 복원한 같은 계획의 재실행이 통과했지만, 변경 없이 통과한 재실행은 첫 실패를 지우는
근거가 아니므로 원인 미확인 상태를 잔여 위험으로 유지했다.

후속 리뷰에서는 `performance/k6/lib/flows.js`가 계약 불일치 시 flow·독자·도서·페이지·기대값·실제값·
scenario/VU iteration을 기록하도록 보강했다. 보강한 dirty 하네스와 후속 통합 JAR의 진단 Spike
`20260813T075738Z-diagnostic-spike-contract`는 checks 73,356/73,356, 오류·dropped 0, SQL 공통 불변식
6종 0으로 통과했다. 실제 신규 대여 VU `31, 32, 33, 34, 35, 37`의 modulo-20 slot도 모두 달랐다. 이
진단은 현재 경로가 통과하고 VU slot 충돌이 없었다는 사실만 증명하며, 정식 측정 JAR과 다른 후속 JAR·
dirty 하네스 실행이므로 과거 실패의 원인 규명이나 정식 성능 수치로 사용하지 않는다.

계약 불일치 진단 분기는 별도 smoke red/green으로 확인했다. Red
`20260813T081619Z-diagnostic-contract-log-red`는 의도적으로 `deductedInk` 기대값을 어긋나게 해 exit code
99와 flow·독자·도서·페이지·기대값·실제값·scenario/VU iteration 로그를 남겼고, 원복한 Green
`20260813T081701Z-diagnostic-contract-log-green`은 checks 32/32와 exit code 0을 확인했다. 두 실행은
진단 코드 검증 전용이며 정식 성능 수치에는 포함하지 않는다.

### 첫 Soak — 무효 실행

첫 Soak는 기능 check 1건이 실패했고 최대 VU보다 작은 신규 독자 `vuStride`가 slot 충돌을 허용하는
harness 조건을 확인했다. `vuStride=24`, `cycles=13`으로 독립 slot을 부여하고 제품 코드·부하 단계는
바꾸지 않은 채 전체 시간을 재실행했다. 원인과 설정 변경이 연결되므로 첫 Soak는 무효 실행으로 분리하고
무효·유효 원본을 모두 보존했다.

### 첫 전체 테스트

첫 `./gradlew test --rerun-tasks`는 개발 MySQL을 기동하지 않은 환경에서 실행해 813건 중 386건이
`FlywaySqlUnableToConnectToDbException`·`CommunicationsException`·`java.net.ConnectException`으로
실패했다. 이는 제품 assertion 실패가 아니라 테스트 선행 조건 누락이므로 결과를 유효 통과로 사용하지
않았다. `docker compose up -d --wait`로 개발 MySQL의 healthy 상태를 확인한 뒤 같은 명령을 전체
재실행했다.

## 최종 검증

| 검증 | 결과 |
| --- | --- |
| `docker compose config -q` | 통과 |
| `docker compose -f compose.performance.yaml config -q` | 통과 |
| 변경 대상 테스트 | 5단계 제품 코드 변경 없음으로 별도 대상 없음 |
| `./gradlew test --rerun-tasks` | 재실행 `BUILD SUCCESSFUL`, 813건 중 성공 812·skip 1·실패 0·오류 0 |
| `./gradlew check` | `BUILD SUCCESSFUL` |
| `./gradlew build` | `BUILD SUCCESSFUL` |
| 패키징 JAR 부팅 | performance 설정의 DB·Flyway·JPA 준비와 management health 200 확인 |
| `/api/smoke` | HTTP 204 확인 |
| 애플리케이션 종료 | 정상 종료, 성능·개발 DB volume과 원시 artifact 보존 |

전체 build 뒤 JAR bytes와 SHA-256은 정식 측정 JAR과 같았다. 패키징 smoke는 단일 endpoint의 HTTP 204
사실이며 브라우저·동시성·외부 서비스 수용 검증을 대신하지 않는다. PortOne·OpenAI는 성능 프로필에서
비활성화했다.

## 재현 명령

비밀값은 Git 제외 `.env`에 두고 아래 명령은 프로젝트 루트의 Bash에서 실행한다. 각 정식 실행은 앱을
중지하고 `mvp` 데이터와 새 앱을 복원해 앞 실행의 대여·세션 상태를 제거한다. Smoke·Warm-up·본 부하는
실제 유효 실행과 같은 신규 독자 pool 값을 사용하고, 본 부하 뒤에는 SQL 불변식을 검증한다.

```bash
set -Eeuo pipefail
trap './performance/scripts/stop-app.sh' EXIT

./gradlew bootJar
final_jar=build/libs/Ilgeobolkka-0.0.1-SNAPSHOT.jar
measurement_jar_sha=$(shasum -a 256 "$final_jar" | awk '{print $1}')
./performance/scripts/recreate-environment.sh
./performance/scripts/reset-mvp.sh
./performance/scripts/generate-history-heavy.sh

run_final_series() {
    local series=$1
    local scenario=$2

    for repetition in 1 2 3; do
        ./performance/scripts/stop-app.sh
        ./performance/scripts/reset-mvp.sh
        ./performance/scripts/start-app.sh
        PERF_NEW_READER_OFFSET=0 PERF_NEW_READER_VU_STRIDE=1 PERF_NEW_READER_CYCLES=1 \
            PERFORMANCE_RUN_LABEL="final-$series-r$repetition-smoke" \
            ./performance/scripts/run-k6.sh smoke
        PERF_NEW_READER_OFFSET=1 PERF_NEW_READER_VU_STRIDE=16 PERF_NEW_READER_CYCLES=4 \
            PERFORMANCE_RUN_LABEL="final-$series-r$repetition-warm-up" \
            ./performance/scripts/run-k6.sh warm-up
        PERF_NEW_READER_OFFSET=65 PERF_NEW_READER_VU_STRIDE=50 PERF_NEW_READER_CYCLES=5 \
            PERFORMANCE_RUN_LABEL="final-$series-r$repetition" \
            ./performance/scripts/run-k6.sh "$scenario"
        ./performance/scripts/verify-invariants.sh
    done
}

run_final_load() {
    local scenario=$1
    local label=$2
    local offset=$3
    local stride=$4
    local cycles=$5

    ./performance/scripts/stop-app.sh
    ./performance/scripts/reset-mvp.sh
    ./performance/scripts/start-app.sh
    PERF_NEW_READER_OFFSET=0 PERF_NEW_READER_VU_STRIDE=1 PERF_NEW_READER_CYCLES=1 \
        PERFORMANCE_RUN_LABEL="$label-smoke" ./performance/scripts/run-k6.sh smoke
    PERF_NEW_READER_OFFSET="$offset" PERF_NEW_READER_VU_STRIDE="$stride" \
        PERF_NEW_READER_CYCLES="$cycles" PERFORMANCE_RUN_LABEL="$label" \
        ./performance/scripts/run-k6.sh "$scenario"
    ./performance/scripts/verify-invariants.sh
}

run_final_series average average-load
run_final_series peak peak-load
run_final_load stress final-stress 65 7 37
run_final_load spike final-spike-rerun 0 20 16
run_final_load soak final-soak-rerun 0 24 13

for contention_case in same-page different-pages different-readers session; do
    ./performance/scripts/stop-app.sh
    ./performance/scripts/reset-mvp.sh
    ./performance/scripts/start-app.sh
    CONTENTION_CASE="$contention_case" \
        PERFORMANCE_RUN_LABEL="final-contention-$contention_case" \
        ./performance/scripts/run-k6.sh contention
    ./performance/scripts/verify-invariants.sh "$contention_case"
done

./performance/scripts/stop-app.sh
./performance/scripts/reset-mvp.sh
./performance/scripts/start-app.sh
PERFORMANCE_RUN_LABEL=final-browser-cache ./performance/scripts/run-k6.sh browser-cache
./performance/scripts/verify-invariants.sh

docker compose up -d --wait
./gradlew test --rerun-tasks
./gradlew check
./gradlew build
test "$(shasum -a 256 "$final_jar" | awk '{print $1}')" = "$measurement_jar_sha"

./performance/scripts/stop-app.sh
./performance/scripts/start-app.sh
curl -fsS http://127.0.0.1:8080/api/smoke >/dev/null
curl -fsS http://127.0.0.1:8081/actuator/health >/dev/null
./performance/scripts/stop-app.sh
trap - EXIT
```

위 명령은 각 하위 명령이 실패하면 즉시 중단하고, 처음 측정한 JAR SHA-256과 전체 build 뒤 JAR을 대조한
다음 그 최종 패키지를 새로 부팅해 smoke·management health·종료까지 확인한다. Spike 재실행과 Soak 유효
실행의 label·고정 pool 값을 사용하며, 첫 Spike 실패와 첫 Soak 무효의 판정은
[실패·무효 실행](#실패무효-실행)에 별도로 기록했다. 시작·종료 UTC, image, JAR, Git·dirty 상태는 각 원시
`metadata.json`에 있고, 원시 위치와 집계 SHA-256은 [artifact-manifest.md](./artifact-manifest.md)에서
찾을 수 있다.

## 5단계 종료 조건

| 종료 조건 | 근거 |
| --- | --- |
| Average·Peak 각 3회 | 모두 같은 출발점·Warm-up, 유효 checks 100%, 중앙값·범위 기록 |
| Stress·Spike·Soak 각 1회 | 충족 — Spike 실패 실행과 같은 계획의 재실행을 모두 보존하고 중단선 결과를 기록 |
| cold·warm 브라우저 분리 | 새 context cold와 같은 context warm 3쌍, 전송량·cache 계약 기록 |
| 동시성 4종·불변식 | k6 check와 SQL 업무 계약·공통 불변식 전부 통과 |
| 전체 test·check·build·JAR smoke | 개발 MySQL 기동 뒤 전체 재실행과 패키징 부팅 통과 |
| 최종 evidence | 환경·summary·dashboard·artifact metadata·comparison 작성 |
| 운영 해석 경계 | 모든 요약과 비교에 로컬 회귀 근거임을 명시 |

5단계는 첫 Spike 기능 실패의 원인 미확인을 잔여 위험으로 남기고 조건부 통과로 종료했다. 이 문서 작성
시점에는 6단계를 실행하지 않았으며, 이후 별도 승인으로 수행한 evidence·diff·민감정보 전수 감사와
container 종료·로컬 인계 결과는 [6단계 감사](./audit.md)에 기록했다.

## 후속 `develop` 통합 경계

5단계 종료 보고 뒤 별도 사용자 요청으로 이 evidence를 커밋하기 전에 현재 브랜치를
`origin/develop`의 `b5b4c589b5bf2b4aac94adb480f7f68a0e1c3905`까지 `--ff-only`로 동기화했다. 충돌은 없었고,
동기화한 HEAD에서 다음을 다시 검증했다.

- `./gradlew test --rerun-tasks`: 878건 중 성공 877·skip 1·실패 0·오류 0
- `./gradlew check`: `BUILD SUCCESSFUL`
- `./gradlew build`: `BUILD SUCCESSFUL`

통합된 13개 커밋은 AI 경로·콘텐츠 적재 코드와 테스트이며 성능 원시 결과를 다시 생성하지 않았다. 따라서
이 문서의 부하 수치와 정확성 판정은 계속 정식 측정 SHA `f3a62809099ba660ce980b006d71c833bbd849fa`의
근거이고, `b5b4c58`의 성능 수치라고 확장 해석하지 않는다. 후속 로컬 커밋은 evidence 보존과 계약 불일치
진단 하네스만 포함한다. 6단계 감사는 이후 별도 승인으로 [실행](./audit.md)했으며, commit·push·PR은 계속
별도 승인 경계다.
