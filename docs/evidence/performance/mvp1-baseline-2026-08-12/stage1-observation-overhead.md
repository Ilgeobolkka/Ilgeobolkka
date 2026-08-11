# 1단계 관측 off/on 오버헤드

## 판정

유효한 off/on Average 각 1회가 동일 JAR, 새 성능 volume, 결정적 `mvp` 데이터, 새 JVM, 3분 Warm-up 뒤
실행됐다. 두 실행 모두 checks 31,945건, HTTP 오류 0건, dropped iteration 0건이다. Prometheus scrape를
켰을 때 실제 HTTP RPS는 사실상 같았고 p50은 0.788ms, p95는 2.988ms 증가했다. 상대 비율은 각각
22.47%, 20.99%지만 절대 지연이 작고 처리율 손실이 -0.0018%여서 2단계 기준선은 관측 on으로 실행한다.

이 비교는 로컬 진단용이며 제품 SLO나 운영 Prometheus 용량 근거가 아니다.

## 유효 결과

| 지표 | 관측 off | 관측 on | on - off |
| --- | ---: | ---: | ---: |
| 시작 UTC | 2026-08-11 07:56:50 | 2026-08-11 08:10:18 | - |
| 종료 UTC | 2026-08-11 08:06:51 | 2026-08-11 08:20:20 | - |
| HTTP 요청 | 17,536 | 17,536 | 0 |
| 실제 HTTP RPS | 29.2226 | 29.2220 | -0.0018% |
| 전체 p50 | 3.508ms | 4.296ms | +0.788ms |
| 전체 p95 | 14.234ms | 17.223ms | +2.988ms |
| 전체 max | 152.359ms | 284.119ms | +131.760ms |
| 공개 조회 p95 | 5.485ms | 7.329ms | +1.844ms |
| 인증 조회 p95 | 6.130ms | 8.208ms | +2.078ms |
| 신규 대여 p95 | 13.968ms | 17.088ms | +3.120ms |
| HTTP 실패율 | 0 | 0 | 0 |
| checks 실패 | 0 | 0 | 0 |
| dropped iteration | 0 | 0 | 0 |

유효 실행 당시 k6 기본 summary trend가 p99를 저장하지 않았다. 1단계 진단을 재실행하지 않고
`K6_SUMMARY_TREND_STATS`에 `p(99)`를 추가했으며, 2단계의 모든 정식 결과에서 p99 존재를 확인한다.

## 관측 on 자원 값

- application process CPU 10분 평균: 0.0866
- Hikari pending 10분 최대: 0
- JVM heap 사용 10분 최대: 106,358,576 bytes
- MySQL 연결 10분 최대: 11
- Prometheus target: application·MySQL 모두 `up`
- 부하 뒤 잔액·음수 잔액·차감 연결·중복 소장·서재 불변식: 전부 0

## artifact

모든 경로는 Git 제외 `var/performance/results/` 아래다. 비밀번호·cookie·CSRF token·session ID는 저장하지
않는다.

| 모드 | 파일 | SHA-256 |
| --- | --- | --- |
| off | `20260811T075650Z-observation-off-average/summary.json` | `9e8ece0b238d9543a29b510dcf45d5cef7feac49005db1356d584ccb16727ede` |
| off | `20260811T075650Z-observation-off-average/metadata.json` | `022798dfe973c1f943fa60e277f41626b0792e127e4f7752a91fb4dbf1888a5f` |
| on | `20260811T081018Z-observation-on-average/summary.json` | `c1d60f95f6d496c4ed6b36dc736bcac593d58cea021465fd24ba927b4e3fa93c` |
| on | `20260811T081018Z-observation-on-average/metadata.json` | `07797d6be6778ec83a4cd8f693f6a6d87e4569daa13f0b19645d933f35009b70` |

두 실행의 `exit-code.txt`는 모두 `0`이고 SHA-256은
`9a271f2a916b0b6ee6cecb2426f0b3206ef074578be55d9bc94f6f3fe3ab86aa`다. JAR SHA-256은 두 실행 모두
`ad025a61983f583c94cfc8eb8b8a0e006ed43bac8cdf2c6db8c531c696071f0b`다.

## 무효·진단 실행

- 첫 browser 실행은 비동기 Chromium 오류를 check로 남기지 않아 무효였다. 예외 check와 Docker browser
  설정을 보완한 뒤 `20260811T072804Z-browser`가 checks 4/4로 통과했다.
- `20260811T072928Z-observation-off-warm-up`은 k6 기본 cookie reset 때문에 인증이 풀려 무효였다.
- `20260811T073949Z-observation-off-average`는 6개 신규 계정의 100잉크가 600회 뒤 소진돼 1,201회가
  실패했다. 신규 대여 계정을 80회마다 결정적으로 순환하도록 수정했다.
- `debug-cookie`, `debug-average`는 각각 15초·30초 수정 확인용이며 정식 결과가 아니다.
