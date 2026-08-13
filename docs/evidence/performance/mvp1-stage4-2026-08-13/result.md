# 4단계 조건부 인프라 실험 결과

## 최종 판정

- 단계 결과: **통과**
- 기준 Git SHA: `a027643e9702e342a4298979b8bf7c6afae96c05`
- 최종 JAR SHA-256:
  `0ce68063a964db0b665c4686c7de533ed76337aed4728e0d2a5ba4d4d891d41e`
- 실행 환경: 로컬 Docker Compose `ilgeobolkka-performance`, 전용 DB `ilgeobolkka_perf`
- 조건부 후보: Caffeine·Redis cache·Spring Session Redis·가상 스레드 모두 진입 조건 미충족
- Kafka: 계획에 진입 게이트가 없어 실행 제외
- 최종 구성: 3단계의 단일 애플리케이션·MySQL 구성 유지. 조건부 후보용 새 dependency·container·config·ADR 없음
- 범위: 로컬 Docker 코드 회귀 검증. 운영 SLO·운영 용량 근거가 아니다.

4단계는 새로운 기술을 반드시 넣는 단계가 아니라 관측된 진입 조건을 만족한 후보만 실험하는 단계다. 현재
근거에서는 새 상태 인프라를 정당화할 병목이나 다중 인스턴스 요구가 없었다. 따라서 수치를 만들기 위해
Redis·다중 인스턴스·가상 스레드를 추가하지 않고, 현재 구성의 Spike와 정확성을 검증해 단순 구성을
유지한다. 4단계에서 추가 제품 성능 향상을 주장하지 않으며, 실제 제품 성능 개선은 [3단계 결과](../mvp1-stage3-2026-08-12/result.md)에 기록된 복합 인덱스 효과다.

## 후보별 진입 판정

| 후보 | 판정 | 연결 근거 | 근거 없이 도입할 때의 비용 | 실행 결과 |
| --- | --- | --- | --- | --- |
| Caffeine | 미충족 | 2단계 공개 조회 p95 중앙값은 6.780ms로 품질 게이트를 통과했고, 우선 병목은 이력 조회·공개 정적 자산 재전송·BCrypt였다. 3단계 뒤에도 반복 공개 DB 읽기가 병목이라는 근거는 없다. 정적 자산 재전송은 데이터 캐시와 다른 프론트엔드 범위다. | 캐시 무효화·메모리·정합성 관리만 추가되고 확인된 병목을 해소하지 못한다. | dependency·코드 추가 없음 |
| Redis cache | 미충족 | 선행 후보인 Caffeine의 진입 조건부터 미충족해 효과가 입증되지 않았고, 여러 인스턴스 사이에서 공개 캐시를 공유해야 할 요구도 없다. | 네트워크 왕복과 Redis 장애·fallback·namespace 운영 경계가 추가된다. | image·container·dependency 추가 없음 |
| Spring Session Redis | 미충족 | 2단계 Stress는 계획 상한 200 flow iteration/s까지 오류·dropped·Hikari pending 없이 끝났고 앱 CPU 평균 16.55%였다. 실제 다중 인스턴스 배포 요구도 없다. | 세션 저장소 가용성과 로그인·CSRF·로그아웃 장애 경계를 별도로 운영해야 한다. | 단일 앱 유지 |
| 가상 스레드 | 미충족 | JFR 실행 표본은 BCrypt CPU가 71.66%였고 큰 I/O 대기 근거가 없다. Average·Peak·Stress와 3단계 유효 실행의 Hikari pending 최대는 0이었다. | DB 병목으로 부하를 전이할 수 있고 pinning·keep-alive 검증 경계가 추가된다. | on/off 실험 없음 |
| Kafka | 제외 | 이번 성능 계획에 진입 게이트가 없다. | 동기 원자 경로에 순서·재시도·멱등성·consumer 운영 복잡도를 추가한다. | 실행하지 않음 근거만 기록 |

판정 근거는 [2단계 결과](../mvp1-baseline-2026-08-12/result.md),
[2단계 자원 지표](../mvp1-baseline-2026-08-12/dashboard.md),
[2단계 진단](../mvp1-baseline-2026-08-12/diagnostics.md), [3단계 결과](../mvp1-stage3-2026-08-12/result.md)다.

## 실행 blocker와 최소 수정

### 일회성 seed 프로세스 종료 불가

첫 `reset-mvp.sh`는 데이터 생성과 불변식 확인을 마친 뒤에도 종료되지 않았다. 3단계 뒤 추가된 전역
`@EnableScheduling`이 `performance-seed` 비웹 프로필에도 scheduler thread를 남긴 것이 원인이었다.
동일한 일회성 비웹 프로필인 `content-import`도 같은 경계에 있었다.

- 재현 테스트: 두 프로필 모두 scheduled annotation processor가 등록돼 9건 중 새 테스트 2건 실패
- 수정: `SchedulingConfig`를 `!performance-seed & !content-import`에서만 활성화
- 수정 뒤 대상 테스트: 9건 모두 통과
- 실제 검증: `reset-mvp.sh`가 고정 행 수와 불변식 6종을 만든 뒤 스스로 종료
- 영향 경계: 일반 서버와 테스트 프로필의 유지보수 스케줄러 등록 계약은 유지

### Spike 신규 대여 계정 pool 부족

첫 Spike는 Average 계열 기본 pool `offset=0`, `vuStride=64`, `cycles=4`를 사용했다. 신규 대여 VU
31~36이 VU당 320회 뒤 pool을 소진했고 k6는 exit code 110으로 실패했다. checks와 HTTP 오류가 정상이더라도
`exec.test.fail`이 발생했으므로 이 실행의 지연 수치는 사용하지 않았다. 실행 뒤 도메인 불변식 6종은 0이었다.

Spike 전용 기본값을 `offset=0`, `vuStride=20`, `cycles=16`으로 분리했다. 신규 독자 334명 중 최대 320명을
사용하며 VU당 1,280회를 지원한다. 데이터를 다시 복원하고 explicit override 없이 Spike 전체를 처음부터
재실행해 기본값 자체를 검증했다.

## 유효 Spike

- 원시 결과: `var/performance/results/20260813T022438Z-stage4-final-spike-rerun`
- 실행 UTC: 2026-08-13 02:24:38~02:33:40
- k6 exit code: 0
- checks: 73,348 / 73,348
- HTTP 오류율 / dropped iteration: 0 / 0
- 실제 HTTP 요청: 40,231건, 74.500 req/s
- 전체 p50 / p95 / p99 / 최대: 2.571ms / 15.017ms / 99.924ms / 484.105ms
- 공개 / 인증 조회 / 신규 대여 p95: 5.286ms / 5.289ms / 13.204ms
- generator CPU 평균 / 최대: 2.963% / 14.68%
- 앱 process CPU 평균 / 최대: 21.61% / 82.50%
- heap 최대: 117,948,544 bytes
- Tomcat busy 최대: 4
- Hikari active / pending 최대: 4 / 0
- MySQL QPS 평균 / 최대: 694.74 / 1,328.07
- MySQL slow query 증가: 0
- Prometheus 상태: `ok`
- pool metadata: `offset=0`, `vuStride=20`, `cycles=16`
- PortOne·OpenAI: 비활성화
- 부하 뒤 잔액 불일치·음수 잔액·대여 없는 차감·중복 소장·활성 대여·소장 서재 연결 위반: 모두 0

유효 실행은 품질 게이트를 통과했고 첫 포화 자원을 확인하지 못했다. 앱 CPU 최대 82.5%는 단일 표본이며
평균은 21.61%, Hikari pending은 전 구간 0이다. 이 결과는 다중 인스턴스나 가상 스레드 진입 근거가 아니다.

## 최종 검증

- 재현 테스트: `AiRouteGenerationMaintenanceSchedulerTest` 9건 중 새 프로필 경계 2건 실패 확인
- 수정 뒤 대상 테스트: 9건 모두 통과
- 전체 `./gradlew test --rerun-tasks`: 813건, 성공 812건, skip 1건, 실패·오류 0건
- `./gradlew check`: `BUILD SUCCESSFUL`
- `./gradlew build`: `BUILD SUCCESSFUL`
- `sh -n performance/scripts/run-k6.sh`, evidence JSON `jq`, `git diff --check`: 통과
- 패키징 애플리케이션: seed 정상 종료, dataset·management·Smoke 통과
- 유효 Spike와 부하 뒤 SQL 불변식: 통과

## 종료 조건 검증

- 모든 조건부 후보에 진입 조건 판정과 기존 측정 근거가 있다.
- 진입 조건을 충족한 후보가 없어 cold·warm 또는 장애 실험을 꾸며내지 않았다.
- 유지하지 않을 dependency·container·config를 추가하지 않았다.
- 운영 구조를 채택하지 않아 ADR을 작성하지 않았다.
- 현재 후보 조합의 Smoke·Spike·Prometheus 관측·부하 뒤 불변식이 통과했다.
- 무효 Spike와 seed 종료 실패를 숨기지 않고 원인·수정·재검증을 보존했다.
- 5단계는 실행하지 않았다.

환경은 [environment.md](./environment.md), 원시 artifact 위치와 SHA-256은
[artifact-manifest.md](./artifact-manifest.md), 기계 판독 요약은 [summary-spike.json](./summary-spike.json)에
기록했다.
