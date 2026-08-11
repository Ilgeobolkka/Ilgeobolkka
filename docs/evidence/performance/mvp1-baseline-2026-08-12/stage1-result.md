# 1단계 측정 장치와 재현 환경 결과

## 판정

- 단계 결과: 통과
- 시작 Git SHA: `46c650e8b6c064e979874537e481b2839c344edd`
- 패키징 JAR SHA-256: `ad025a61983f583c94cfc8eb8b8a0e006ed43bac8cdf2c6db8c531c696071f0b`
- 환경·고정 image·데이터 경계: [environment.md](./environment.md)
- 관측 off/on 근거: [stage1-observation-overhead.md](./stage1-observation-overhead.md)

## 종료 조건 검증

| 종료 조건 | 근거 |
| --- | --- |
| 핵심 흐름 Smoke | 공개 조회, 로그인, CSRF, 세션, 신규·활성 대여, 소장 흐름을 검사했다. 최종 실행은 checks 32/32, HTTP 23건, 실패율 0이다. |
| summary와 지표 연결 | 실행별 `summary.json`과 `metadata.json`에 UTC 범위와 관측 모드를 기록한다. 같은 범위의 HTTP·JVM·Tomcat·HikariCP·MySQL 지표를 Prometheus와 Grafana에서 조회할 수 있다. |
| 성능 DB guard | `ilgeobolkka_perf` 외 DB 이름은 쿼리·초기화 전에 거부하며 대상 테스트 2건과 실제 고정 데이터 복원으로 확인했다. |
| 관측 보안과 off/on | 공개 포트 Actuator 404, management loopback, 허용 endpoint, 금지 label 부재와 Prometheus 두 target `up`을 확인했다. off/on Average 유효 결과가 있다. |
| 변경 완료 게이트 | 성능 대상 테스트 8건, 전체 608건 중 607건 통과·실패 0·조건부 1건 skip, `test`, `check`, `build`, Compose config, 패키징 JAR 부팅이 통과했다. |

## 최종 Smoke artifact

- 경로: Git 제외 `var/performance/results/20260811T082741Z-stage1-final-smoke/`
- 결과: checks 32/32, HTTP 요청 23, HTTP 실패율 0, p95 103.041ms, p99 135.810ms
- 부하 뒤 데이터 불변식: 잔액 불일치, 음수 잔액, 대여 없는 차감, 중복 소장, 서재 연결 위반 모두 0

| 파일 | SHA-256 |
| --- | --- |
| `summary.json` | `50d73b536aa78593d4182b01efda7205bd6487e7d8c80d4eae728f8e76443bd3` |
| `metadata.json` | `02676d4913ea10aafd0af67cc13197c93e619313a85b0bab29ff3a5816d504e3` |
| `exit-code.txt` | `9a271f2a916b0b6ee6cecb2426f0b3206ef074578be55d9bc94f6f3fe3ab86aa` |

## 실행 이력과 실패 처리

- 유효 정식 실행은 Smoke 2회, browser 1회, off/on Warm-up 각 1회, off/on Average 각 1회다.
- 초기 전체 `build` 1회는 `final` seeder의 `@Transactional` CGLIB proxy 생성 실패로 중단됐다. 불필요한 proxy 경계를 제거한 뒤 대상 테스트와 전체 게이트를 다시 통과했다.
- 초기 browser 3회는 비동기 오류 미검출, root Chromium sandbox 설정, 단일 hostname HSTS 충돌을 각각 드러냈다. 오류를 check 실패로 만들고 Docker browser 인자와 전용 hostname을 고정한 뒤 checks 4/4로 통과했다.
- 초기 관측 실행은 k6 cookie reset과 신규 독자 잉크 소진을 드러냈다. VU cookie 유지와 결정적 계정 순환으로 수정한 뒤 새 데이터·새 JVM에서 off/on을 재실행했다.
- 무효·진단 artifact는 유효 결과와 구분해 보존했으며 기준선 계산에 포함하지 않는다.

## 종료 상태

- 성능 Compose container와 network는 정지·제거했다.
- `ilgeobolkka-performance_mysql-perf-data`, `ilgeobolkka-performance_prometheus-data` volume은 다음 단계 재현을 위해 보존했다.
- Redis와 조건부 캐시·세션 구성은 추가하지 않았다.
- 2단계 기준선은 실행하지 않았다.
