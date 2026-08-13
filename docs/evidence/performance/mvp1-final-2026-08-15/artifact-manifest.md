# 5단계 원시 artifact manifest

## 해시 방법과 보존 경계

원시 k6·Prometheus 결과는 Git 제외 경로 `var/performance/results/`에 보존한다. 디렉터리 집계 SHA-256은
다음 규칙으로 계산했다.

1. 디렉터리 아래 일반 파일을 상대 경로 기준 byte 순서로 정렬한다.
2. 각 파일의 `SHA-256  상대경로` 행을 만든다.
3. 행 전체의 SHA-256을 계산한다.

Git에는 판정에 직접 사용한 작은 k6 summary, 브라우저 JSONL과 history-heavy metadata·counts·invariants를
이 evidence 폴더에 복사했다. 큰 Prometheus 시계열·generator 표본·전체 로그는 복사하지 않고 위치·전체
크기·집계 SHA-256으로 연결한다. `var/performance/results/`를 정리하면 아래 해시만으로 원본을 복구할 수
없으므로 6단계 보존 여부 결정 전까지 삭제하지 않는다.

## 실행별 위치와 집계 SHA-256

| 실행 | 저장소 상대 위치 | 크기 | 디렉터리 집계 SHA-256 |
| --- | --- | ---: | --- |
| history-heavy 입력 | `var/performance/results/20260813T033644Z-history-heavy` | 618 | `4d60019a31975c48379e62f0980bd5b93a5639ded65edc556cff717246ccf583` |
| 초기 패키징 Smoke | `var/performance/results/20260813T033715Z-final-preflight-smoke` | 11,783 | `84eb2524e703d04ef4af14a954b3edd0f1eb056e10e04dc4f871ba266f3c21b7` |
| Average r1 Smoke | `var/performance/results/20260813T033811Z-final-average-r1-smoke` | 11,532 | `7c51a634c6da93bbf3b611fc5f52eaa9c564eb0a1f46c75f1edec9c9feea38a5` |
| Average r1 Warm-up | `var/performance/results/20260813T033821Z-final-average-r1-warm-up` | 19,540 | `5d6198bbaeb77d80d2d613f9f01c3964882ee275da6b73ece317b05a2ed2b811` |
| Average r1 | `var/performance/results/20260813T034130Z-final-average-r1` | 37,913 | `b07a5e6b5ecca934396b2a09380be482c5c88d0d6f2bbcbddb448a43af1d5659` |
| Average r2 Smoke | `var/performance/results/20260813T035210Z-final-average-r2-smoke` | 11,879 | `385e7cba14f28b4e4099870bbd96b3341d5555d90b702ddc2757fb2c21a9beb8` |
| Average r2 Warm-up | `var/performance/results/20260813T035220Z-final-average-r2-warm-up` | 19,373 | `3044bababd06d74e1ed07aec66bb59ca79ed0b43d6de9a92363fd2707e1da3bd` |
| Average r2 | `var/performance/results/20260813T035529Z-final-average-r2` | 37,896 | `673c3c2a54356bacc2b9bb40f30860537cdf7dc9db913231c184c7eb8a6556c6` |
| Average r3 Smoke | `var/performance/results/20260813T040606Z-final-average-r3-smoke` | 11,595 | `6f8e9ac6382d802e62fed723f44db2461466af631d5474bf24cd636766c32ed0` |
| Average r3 Warm-up | `var/performance/results/20260813T040617Z-final-average-r3-warm-up` | 19,708 | `70c2d2009d9cbabe9121879e14d444b111c32de8115eba822b84c11a403bca05` |
| Average r3 | `var/performance/results/20260813T040925Z-final-average-r3` | 37,650 | `899cc6c22d6e190933435b5c2f5d9bd0fdf721cb55ca843db8026225dbd22cd8` |
| Peak r1 Smoke | `var/performance/results/20260813T042005Z-final-peak-r1-smoke` | 12,049 | `9f7515ed998e845bf328bfc7e1f7d5fc6053935fd5bdf0cebebe06f52c784f04` |
| Peak r1 Warm-up | `var/performance/results/20260813T042016Z-final-peak-r1-warm-up` | 19,666 | `c78755fc5588ba07fa92af21eab724ec23bd88f55bd8d86bcea0096ba0167217` |
| Peak r1 | `var/performance/results/20260813T042325Z-final-peak-r1` | 37,869 | `4c7a0b6243e23911178d76b1e723651edfb8eebe9554f67ccf485aa4f73163f1` |
| Peak r2 Smoke | `var/performance/results/20260813T043404Z-final-peak-r2-smoke` | 12,060 | `840e8f58c552cc90ac0746ccc35951f0c0e6b4bde82a151fc9273d254a7dbbe0` |
| Peak r2 Warm-up | `var/performance/results/20260813T043415Z-final-peak-r2-warm-up` | 19,437 | `1b6939779a0e1095aefc3491640c75cbf8c21750cf7b31db624b9a5043b5a536` |
| Peak r2 | `var/performance/results/20260813T043725Z-final-peak-r2` | 37,896 | `8193ee01c8d2573ac45d3076b348b577bdcaf43e1cce5cdd3ca7c0e8913d350e` |
| Peak r3 Smoke | `var/performance/results/20260813T044802Z-final-peak-r3-smoke` | 11,584 | `da1874a79d0ce20131ec997f3e1d74003da1ac60992ca3b1566defac3aa53a9c` |
| Peak r3 Warm-up | `var/performance/results/20260813T044813Z-final-peak-r3-warm-up` | 19,777 | `3ff7682156a12c9c61c134125c8733d0a9b6e8fd56179e57b83e2a40125f6ebc` |
| Peak r3 | `var/performance/results/20260813T045122Z-final-peak-r3` | 38,416 | `836c2256f5910ee03148ceb122af269ec3f715cee957880be6c0cb6447dda03d` |
| Stress Smoke | `var/performance/results/20260813T050205Z-final-stress-smoke` | 11,875 | `47ea8f40f3b8258fb58cdba0f3315078f652dc5d9bd69ed6b210bddf10a3b434` |
| Stress | `var/performance/results/20260813T050215Z-final-stress` | 49,039 | `f46c06fecaaf38f940410f6fd7483c8a3f4454d83b691e68dedca931c1ea526d` |
| 실패 Spike Smoke | `var/performance/results/20260813T051750Z-final-spike-smoke` | 12,043 | `fbc88df7ed3529f8c920e01278248e9aa4e2faabf6912ef5338c2c53746f5bc9` |
| 실패 Spike | `var/performance/results/20260813T051801Z-final-spike` | 35,552 | `38a19360e25c4c23f44a3cb4c1f3ad1d7d3e487b2ea4a934c9c8c92cdd6c8e72` |
| Spike 재실행 Smoke | `var/performance/results/20260813T052807Z-final-spike-rerun-smoke` | 11,492 | `af75ca6e740eb1ba2de9c4a1dc6dea2296d302f0c7036a0863e9e906f30ebab6` |
| Spike 재실행 | `var/performance/results/20260813T052817Z-final-spike-rerun` | 35,563 | `5c634be2fb8ce28e3d082b166bc3f3bcb52882798afa680efb0a52478a41dede` |
| 무효 Soak Smoke | `var/performance/results/20260813T053835Z-final-soak-smoke` | 11,850 | `bb913d018e7411f57f54fe599538146644d087dfc108c7116d7d624aeba17bac` |
| 무효 Soak | `var/performance/results/20260813T053846Z-final-soak` | 86,010 | `18571dadd05d463ecd5fc043de6ee417e70d745fb473c221eb650e7196777656` |
| Soak 재실행 Smoke | `var/performance/results/20260813T061123Z-final-soak-rerun-smoke` | 11,780 | `a3e3616d83964f365aa19a834fd52f48e37645abc046de4d42c596568a0cf1b5` |
| 유효 Soak | `var/performance/results/20260813T061134Z-final-soak-rerun` | 85,686 | `3eab84b120fa68a65105cd74fe583c238b6a4a3a8ec4cc9a07f1e03758f5dffd` |
| 경합 같은 페이지 | `var/performance/results/20260813T064235Z-final-contention-same-page` | 10,905 | `8d3c4b42913aacb06942560010f49280c653583c90299b66961ae9b27eb49e5c` |
| 경합 다른 페이지 | `var/performance/results/20260813T064308Z-final-contention-different-pages` | 10,863 | `1544e8b5945a2b5c31c9f56dd5d2f44e41f066af72df3802d8add8a324a3e12f` |
| 경합 다른 독자 | `var/performance/results/20260813T064342Z-final-contention-different-readers` | 10,792 | `1de673cde2acb93531ec497cf0c6d97e30a14c45231dcbe3514c8ef92446a738` |
| 경합 세션 | `var/performance/results/20260813T064418Z-final-contention-session` | 11,721 | `e9e82a91bcb0a104c27db2e89332e957b33395d65eae47df5cae5236ccde7a5c` |
| 브라우저 cache | `var/performance/results/20260813T064503Z-final-browser-cache` | 47,426 | `3148cf79660d1a0d6b80777bb051c3b4c69649e8de7ff5fdc83e16e25c1f737a` |
| 후속 진단 Spike | `var/performance/results/20260813T075738Z-diagnostic-spike-contract` | 35,704 | `553b6d46f262834303b45fa015161a674cf8a2e19caefe0fb932644de81cb0d2` |
| 진단 로그 Red | `var/performance/results/20260813T081619Z-diagnostic-contract-log-red` | 12,296 | `4b75192c539f934e2a6e067677463005b7d57b440024a0f0d37324731d425114` |
| 진단 로그 Green | `var/performance/results/20260813T081701Z-diagnostic-contract-log-green` | 11,627 | `58b2fdcd7baaadc28992de93b010c14394cf626f91e2ea65363c1d52bb9f4448` |

후속 진단 Spike는 리뷰 수정 중 계약 불일치 로그를 보강한 dirty 하네스와 통합 뒤 JAR SHA-256
`d9adc84a30f9bf42c0a4f9f6b664c18f2fdc19f005d49589f293152bf6583c26`으로 실행했다. 과거 첫 Spike의
원인 규명이나 정식 성능 수치에는 사용하지 않고, 현재 경로의 통과와 재발 진단 가능성만 확인한다.
진단 로그 Red/Green도 같은 dirty 하네스와 JAR을 사용한 smoke이며, 실패 로그 필드와 정상 경로를 확인한
테스트 근거로만 사용한다.

## Git 보존 파일 SHA-256

| 파일 | SHA-256 |
| --- | --- |
| `summary-average-1.json` | `0b747daf5ed4cf592d1fc600a091437c595706a2815a3c24494aa5493f7b6bbd` |
| `summary-average-2.json` | `9f040b9218d6a2b8e03284634eaae81068383c8989aadac5f6ffccdd6ecd7aa1` |
| `summary-average-3.json` | `56e81c86df6f0065f7a8ae649e3f01795b2c4fcda54b67945c3cf9e4ecab8268` |
| `summary-peak-1.json` | `cba0ed1d1de118b18338f2d1c7f57581f6b567d69020ae55cc16f93806c48e82` |
| `summary-peak-2.json` | `8694f8c779b6941848c595e022b9f098ef20272df586aacf9c89ead4abdd7193` |
| `summary-peak-3.json` | `38f3027f26e3fcbe2ad131649f312ffe36257c407daacc0e4c1981b744245614` |
| `summary-stress.json` | `574a6daa5b3755a89a806d58b1e722f04bf06750da5fb510f2043725bc1dca16` |
| `summary-spike-failed.json` | `5d7ec313f4ae6e2d0bf13d67475ccfd5ab544ddd5acfc21317e3dfc69580f0d9` |
| `summary-spike.json` | `f5fcf4fd042284a43129788c1b5b519caadd0917e5e5d7acf0e60344d60e7dd7` |
| `summary-soak-invalid.json` | `51381c1cab4a14a63e495be7adb8abd8090b2d95cbf1e0668b09c8cbe2f51d55` |
| `summary-soak.json` | `a14d9fc4bcf1fb28b524bc82cd4b50572f32f9a847019c0f3480c3c92ddb6a9d` |
| `summary-contention-same-page.json` | `a2b6526bb85d84eb28c767eb29689bee8e9c7c7fcae2709ca1826962b18a7dea` |
| `summary-contention-different-pages.json` | `e01bec26174ba5fd4286aeedcf994e4bb791c827942379b303479b7a8d0f7849` |
| `summary-contention-different-readers.json` | `46075600d6c48d8c8b322d08728d04c8e06a2a7821d33f85bc1cda5dcfa9d7a6` |
| `summary-contention-session.json` | `7d044ed17cdea052caf4282634397a7d1d693764347d0dd038b741ff56868619` |
| `summary-browser-cache.json` | `3717b41ca9451886c6ded0c8bd26825f82c814c0b4b8b989dce8d8b3b862a915` |
| `browser-cache.jsonl` | `1e1c05361d1d66dd076a7775b863b0d0c19ee3dd1e1a89970bad827a529fcb69` |
| `history-heavy-metadata.json` | `0dd2a324474e149e36d3cd7184ba3765b760b7b2eeb03731d2aa7042c5641b8c` |
| `history-heavy-counts.tsv` | `d4fc210d6d4518e6d51cdf1c51f560f39652f4cc5d2b72352480ef044c3ff6a6` |
| `history-heavy-invariants.tsv` | `c62f97ce9c8990e5d8ca10c8f8db1ad11f4be91f61117985d9049afb9a31369c` |

## 정식 측정 JAR과 후속 통합 JAR

정식 성능 측정 뒤 `develop`을 fast-forward하고 전체 build를 다시 실행하면서 같은 로컬
`build/libs/Ilgeobolkka-0.0.1-SNAPSHOT.jar` 경로가 최신 코드의 JAR로 교체됐다. 정식 측정 JAR의 해시는
각 원시 `metadata.json`과 아래 첫 행이 증거이며, Git에는 JAR 자체를 저장하지 않는다.

| 구분 | Git SHA | bytes | SHA-256 | 성능 수치 사용 |
| --- | --- | ---: | --- | --- |
| 정식 측정 JAR | `f3a62809099ba660ce980b006d71c833bbd849fa` | 87,887,367 | `5c4e146b2a98d85cd4465b204a64660911dd72b3965b807791e868d66a2b9eb0` | 사용 |
| 후속 `develop` 통합 검증 JAR | `b5b4c589b5bf2b4aac94adb480f7f68a0e1c3905` | 87,929,805 | `d9adc84a30f9bf42c0a4f9f6b664c18f2fdc19f005d49589f293152bf6583c26` | 미측정, 사용하지 않음 |
