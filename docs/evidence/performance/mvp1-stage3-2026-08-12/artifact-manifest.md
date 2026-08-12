# 3단계 원시 artifact manifest

## 해시 방법과 보존 경계

원시 k6·Prometheus 결과는 Git 제외 경로 `var/performance/results/`에 보존한다. 디렉터리 집계 SHA-256은
다음 규칙으로 계산했다.

1. 디렉터리 아래 일반 파일을 상대 경로 기준 byte 순서로 정렬한다.
2. 각 파일의 `SHA-256  상대경로` 행을 만든다.
3. 행 전체를 `shasum -a 256`에 입력한다.

Git에는 판정에 직접 사용한 작은 k6 summary를 이 evidence 폴더의 `summary-*.json`으로 보존한다. 큰
Prometheus 시계열·전체 로그는 복사하지 않고 위치·전체 크기·집계 SHA-256과 주요 파일의 SHA-256만
기록한다. `var/performance/`가 정리되면 큰 원시 파일은 아래 해시만으로 복구할 수 없으므로 로컬 보존이
필요한 동안 해당 경로를 유지한다.

Git 보존 summary는 control과 초기 후보 Average, 이력 endpoint control/candidate 각 3회, history-heavy
mixed control/candidate, 결합 Average 3회, 무효 Peak 1회와 유효 Peak 3회다. 파일명은 실행 목적과 회차를
그대로 반영한다.

## 실행별 위치와 집계 SHA-256

| 실행 | 저장소 상대 위치 | 크기 | 디렉터리 집계 SHA-256 |
| --- | --- | ---: | --- |
| Prometheus 미기동 Smoke | `var/performance/results/20260812T014312Z-stage3-control-smoke` | 9,265 bytes | `51c23b176f4bddac4fe2f74d9ad41d40cfa8ce22363d9ce1a42bded92cd28979` |
| Prometheus 미기동 중단 Warm-up | `var/performance/results/20260812T014324Z-stage3-control-warm-up` | 10,287 bytes | `e308c3e0097f12667105a92527a48d440142485e90ecc48c30eba6adc89feec8` |
| 유효 control Average | `var/performance/results/20260812T014850Z-stage3-control-valid-average` | 37,415 bytes | `7a04c30cb714fdd2e6f7563467565a2e9b3123ff294fc56f3667e071050bd745` |
| page_rental 전 history-heavy | `var/performance/results/20260812T020056Z-history-heavy` | 600 bytes | `87368fa25641e439d8a229aaae9ad5998116406f859b9cf653da2cf1912b7bf8` |
| page_rental 후보 Average | `var/performance/results/20260812T020743Z-stage3-page-rental-index-average` | 37,499 bytes | `ab996305c53bca48ce3c28e7ffbfbecaa5f2e17d828dbec5ecb05bc06bd034fc` |
| ink_ledger 후보 history-heavy | `var/performance/results/20260812T022102Z-history-heavy` | 599 bytes | `3a6f799871f9c81b8cb5ad9503ab5350af32c216a57ccaed8b1789a19ea62c47` |
| ink_ledger 후보 Average | `var/performance/results/20260812T022503Z-stage3-ink-ledger-index-average` | 37,100 bytes | `b303399703c6fda8b838dd46724f4a7f1a4c6eb29128fca3c77954e555ebe99b` |
| 최초 폐기 판정 패키징 JAR smoke | `var/performance/results/20260812T025327Z-stage3-final-packaged-smoke` | 11,549 bytes | `fd69c1c323e4f230642fe89b27c395e9dcb90335769cd40b6cd144ed0b024f64` |
| 재검증 history-heavy | `var/performance/results/20260812T031923Z-history-heavy` | 599 bytes | `b9bf3a0f979949ce38604b67aa9e48e9d06419f1ec04286becfa09e24df98861` |
| endpoint control Warm-up 1 | `var/performance/results/20260812T032324Z-stage3-history-index-control-warmup-1` | 12,655 bytes | `bfb4f2c95a68daae102c950573c890963dd8d2909ffeb3003be9c1930221e9ca` |
| endpoint control 1 | `var/performance/results/20260812T032403Z-stage3-history-index-control-r1` | 16,262 bytes | `0f653b3f50078de3bf19d7a64820bb9d949be7c26924071f7b75612a8c17146c` |
| endpoint candidate Warm-up 1 | `var/performance/results/20260812T032638Z-stage3-history-index-candidate-warmup-1` | 12,633 bytes | `9f09682201b396177c5088581f695241b59fb6f126c142fe814338dc4e522c6f` |
| endpoint candidate 1 | `var/performance/results/20260812T032718Z-stage3-history-index-candidate-r1` | 16,160 bytes | `32bfc9c0022b6acea973bd002f53e9c3bd44cae00870077e55536cd6be186c0b` |
| endpoint control Warm-up 2 | `var/performance/results/20260812T032956Z-stage3-history-index-control-warmup-2` | 12,660 bytes | `b4968b317a2305131a8145ebc942df694697f3648dd7b726bdabfc840a4b873f` |
| endpoint control 2 | `var/performance/results/20260812T033035Z-stage3-history-index-control-r2` | 16,330 bytes | `6f767aff09ac4174ece0aa3b88773cb5f89aeb86aaeec981ef7821287f830d83` |
| endpoint candidate Warm-up 2 | `var/performance/results/20260812T033306Z-stage3-history-index-candidate-warmup-2` | 12,712 bytes | `4d961dd78408ae1897dc9865340669c52af1be3831daaafdb895b57efbadc20d` |
| endpoint candidate 2 | `var/performance/results/20260812T033345Z-stage3-history-index-candidate-r2` | 16,267 bytes | `2d81f7f3efdcc9bcb85c191c3759d8f6cf9125040a9607b014c2b9a5e556280b` |
| endpoint control Warm-up 3 | `var/performance/results/20260812T033609Z-stage3-history-index-control-warmup-3` | 12,670 bytes | `0ec452c90974cf8918a57f1a2ea1c6e074c4eabb6cb2793c4434216adb9c150a` |
| endpoint control 3 | `var/performance/results/20260812T033648Z-stage3-history-index-control-r3` | 16,266 bytes | `92803baa8bc246aa87883d48a374a7120ab48fe64a22f4c2682c3ed0bf5c345f` |
| endpoint candidate Warm-up 3 | `var/performance/results/20260812T033916Z-stage3-history-index-candidate-warmup-3` | 12,526 bytes | `413ac20fdd5037e1d7f1ce78fff6fa1d0e4dabba42f67da0909fe782785ec80a` |
| endpoint candidate 3 | `var/performance/results/20260812T033955Z-stage3-history-index-candidate-r3` | 16,182 bytes | `8c2290b02d8ca05dabd872e30b0a4aa8f96d561414849a3aa8ab227039933a66` |
| mixed control history-heavy | `var/performance/results/20260812T034351Z-history-heavy` | 599 bytes | `e30c213d7d63527051de58c76a6de0f80b9a4fd19b921b9c41fe957bb5c1b853` |
| mixed control Warm-up | `var/performance/results/20260812T034405Z-stage3-history-mixed-control-warmup` | 14,518 bytes | `041f618d2ba9d4aed7d69989c278067375e8ede9b80fb5c4448cfacd4e0a23d5` |
| mixed control Average | `var/performance/results/20260812T034514Z-stage3-history-mixed-control-average` | 20,615 bytes | `59c02afc902ffa178de89df855b38a58c80b71beaaf6ac7501ed0922d9ce67da` |
| mixed candidate history-heavy | `var/performance/results/20260812T034848Z-history-heavy` | 599 bytes | `c0aae39c18d83f15f9eab9317c162fc7b53c541598cda7d3f30507216d40e4f1` |
| mixed candidate Warm-up | `var/performance/results/20260812T034901Z-stage3-history-mixed-candidate-warmup` | 14,387 bytes | `116f31eebd653bb61c4194bdb00b672bb4f46025087871db331f85db4b78a2a5` |
| mixed candidate Average | `var/performance/results/20260812T035012Z-stage3-history-mixed-candidate-average` | 20,853 bytes | `da5ead6b8c84d9161321a6b6a8c4b5c345d19b69348925e463a37e88f4299118` |
| 최종 채택 패키징 JAR smoke | `var/performance/results/20260812T040250Z-stage3-history-index-final-packaged-smoke` | 11,708 bytes | `791e8aa2b0faa8bc8c9e2f7dca153fd19c9d56ea3e65df9a084c95516ab449fe` |
| 결합 Average r1 Smoke | `var/performance/results/20260812T040531Z-stage3-combined-average-r1-smoke` | 11,556 bytes | `34eb01c682aca454cb8889f52cadeb7349e947d445abb276f3833684c5004c22` |
| 결합 Average r1 Warm-up | `var/performance/results/20260812T040542Z-stage3-combined-average-r1-warmup` | 19,383 bytes | `bc98976a0e394ea229205bf75e32363cec6cdd2b6cf40f56672ac357a6660975` |
| 결합 Average r1 | `var/performance/results/20260812T040852Z-stage3-combined-average-r1` | 37,362 bytes | `8103988ea4dec5c1433978858e83af5e1403f040e0d564b63c241f3502d63af0` |
| 결합 Average r2 Smoke | `var/performance/results/20260812T041916Z-stage3-combined-average-r2-smoke` | 11,692 bytes | `2b7622f97ba6498ae36fa0ae7d9ca9cf0f084900537ab5000a4b002e50af3e4f` |
| 결합 Average r2 Warm-up | `var/performance/results/20260812T041928Z-stage3-combined-average-r2-warmup` | 19,220 bytes | `e1e94a43ca673ac5489d48684e957f3b725f86bec2d58c19cdcf3a2bc1ebc06b` |
| 결합 Average r2 | `var/performance/results/20260812T042238Z-stage3-combined-average-r2` | 37,594 bytes | `e331193d6cbc5571b351c2ef1f677b841dda7baa26320dfeb6a2188ef364ce34` |
| 결합 Average r3 Smoke | `var/performance/results/20260812T043303Z-stage3-combined-average-r3-smoke` | 11,579 bytes | `d14c3c4099de50fb1b25608f3c3ef98f60ba67aed172bdd26b1f6dc14fe74586` |
| 결합 Average r3 Warm-up | `var/performance/results/20260812T043314Z-stage3-combined-average-r3-warmup` | 19,355 bytes | `8dc959ff9db86673ed6c3a1e528297f321b5014f86fcbefb70732ace41c2b271` |
| 결합 Average r3 | `var/performance/results/20260812T043623Z-stage3-combined-average-r3` | 37,563 bytes | `37ce29144cc5ca96b77e3cadf84ce52590e5033f4875b965a5556324a7d861c4` |
| 무효 Peak Smoke | `var/performance/results/20260812T044645Z-stage3-combined-peak-r1-smoke` | 11,627 bytes | `e8122b3b3f82b428f97fd302a9b1fe5301c971d1f554dab2111df16d472c041f` |
| 무효 Peak Warm-up | `var/performance/results/20260812T044656Z-stage3-combined-peak-r1-warmup` | 19,391 bytes | `d47a641c29142589d5b2a248804cd1265543a1d55ae46e58713e34f6dd0fdf01` |
| 무효 Peak 본 측정 | `var/performance/results/20260812T045006Z-stage3-combined-peak-r1` | 37,751 bytes | `4950eb653971931ba35e9921b0dbb0243c3d4896639b36d826732e152a79619e` |
| 유효 Peak r1 Smoke | `var/performance/results/20260812T050255Z-stage3-combined-peak-r1-rerun-smoke` | 11,612 bytes | `3979e49d92e699e3ab87ad79f7327136b9b2034fc988537bef0aed8291a5442f` |
| 유효 Peak r1 Warm-up | `var/performance/results/20260812T050306Z-stage3-combined-peak-r1-rerun-warmup` | 19,376 bytes | `3b5bb57da71dd90a14bcdd28fa20028b4769685d064a92c7c93a619c85e934a1` |
| 유효 Peak r1 | `var/performance/results/20260812T050616Z-stage3-combined-peak-r1-rerun` | 37,557 bytes | `8a56b4a3ae1c5adb49a95815faf10af5ed222cbfa8914c0971979f79e70dd2a7` |
| 유효 Peak r2 Smoke | `var/performance/results/20260812T051641Z-stage3-combined-peak-r2-rerun-smoke` | 11,608 bytes | `7c5e869f8acc0e10c42a42fa658bd95a86585880c9cb2678c61174a2a73a4bdb` |
| 유효 Peak r2 Warm-up | `var/performance/results/20260812T051652Z-stage3-combined-peak-r2-rerun-warmup` | 19,366 bytes | `457bb586a5aa2b20e0153212f1391e8d2fc5d57d1c116d6199d6983a203d8b7b` |
| 유효 Peak r2 | `var/performance/results/20260812T052002Z-stage3-combined-peak-r2-rerun` | 37,698 bytes | `134c1b819ecf483ec9feef4b5728d4202fe70e87090bb633993a34c6b9c16071` |
| 유효 Peak r3 Smoke | `var/performance/results/20260812T053028Z-stage3-combined-peak-r3-rerun-smoke` | 11,281 bytes | `09c35f9db97bc1149b8acc4597fdc2a02081a4543d3bdd65cc7e355f064a4ec7` |
| 유효 Peak r3 Warm-up | `var/performance/results/20260812T053039Z-stage3-combined-peak-r3-rerun-warmup` | 18,966 bytes | `77497abac7579dcb070e3ca907f27985e39c3e582b1f212a45d49ebd3e0be52d` |
| 유효 Peak r3 | `var/performance/results/20260812T053348Z-stage3-combined-peak-r3-rerun` | 37,524 bytes | `16cadfdcf7a5713a6ce0556f76d65e7454c43e3860fbf7973a52e130942f723e` |

## 주요 파일 SHA-256

| 실행 | 파일 | SHA-256 |
| --- | --- | --- |
| 무효 Smoke | `summary.json` | `307ee1e0628991e2897c2cb5ff13ec7c8150f98eb0aa2bca2118ebf90545f62d` |
| 무효 Smoke | `metadata.json` | `d4b51871a8392ec6dac5c4f4b31035e068d2e0aec8f2bd5dcabe2a68198675c4` |
| 무효 Smoke | `prometheus-summary.json` | `0cebc005aceb83148f71fa60ac1fc4d42ec22382e2aa25c7509d03f0c1f4a520` |
| 중단 Warm-up | `summary.json` | `08f949cb09b40a1c1cb1c571353676d014edbd808993e8b8aba629df45fc538e` |
| 중단 Warm-up | `metadata.json` | `63b6d1f0f5057d668774dc65786c309b69ae3e90aab17540f82da9c7f7a8941c` |
| control Average | `summary.json` | `fc5aedbd6ac8247d9396adb218a1557302991cc7fd98d6053fe9635a671b9334` |
| control Average | `metadata.json` | `caa439644c2244b578bd62c6744fb85a09d39384afc848b49f557b3448de24fb` |
| control Average | `prometheus-summary.json` | `78628317a5c2bba7cbd86c865b614816b9fbedc5bb363142847cb93b7e569c36` |
| page_rental Average | `summary.json` | `0837aeffaac964bdd491ea9065c5aa44ecd262478a0766e122d333ea7c0df861` |
| page_rental Average | `metadata.json` | `7d58a102d54fefed4ba3b3b1f39707be836cf38c40ff660a2d5c892b0d704a87` |
| page_rental Average | `prometheus-summary.json` | `cfbb8bc284d2daa7b4ab3f64493e5c13abdaefbff61489a7d3c4627b2e0ec9af` |
| ink_ledger Average | `summary.json` | `63fb117cec8699959e645d7dd0ec30a29fef65f34ad348114bfd7f212556b714` |
| ink_ledger Average | `metadata.json` | `217a5713ca727a995b8b9023255d10437a9c1ce5247b2d21f569bb542366aba8` |
| ink_ledger Average | `prometheus-summary.json` | `c096eed0cf04a0730d8f2ba5a1250a2bd60e247d8e65a6834b4a7ccb783973f1` |
| 최초 폐기 판정 smoke | `summary.json` | `abb24b1537cbd29df7d88a0df8780165fcca49a4c5f2d40c50807bb3b0490dfe` |
| 최초 폐기 판정 smoke | `metadata.json` | `9a6113398b99137451d45e1314298c4697fe74a9bae13f577f79217017c56575` |
| 최초 폐기 판정 smoke | `prometheus-summary.json` | `4ac3127ea11e2fa2ec0f9ea3ab8e9f1d5e3c6e955aa0bc046406bad5f7094ed2` |
| 최초 폐기 판정 smoke | `exit-code.txt` | `9a271f2a916b0b6ee6cecb2426f0b3206ef074578be55d9bc94f6f3fe3ab86aa` |
| 최종 채택 smoke | `summary.json` | `f16fb39c90fe3ad4c61106782fd80b108524d4eae275fd4b70e7e64351c14727` |
| 최종 채택 smoke | `metadata.json` | `dad22c6948423d5495152604460041e3f9198658b54e4fb8aa2899707b59e575` |
| 최종 채택 smoke | `prometheus-summary.json` | `72ee45f0876d403c261b43aa239c6ca8232ff21a409ac13d02ed28533398287d` |
| 최종 채택 smoke | `exit-code.txt` | `9a271f2a916b0b6ee6cecb2426f0b3206ef074578be55d9bc94f6f3fe3ab86aa` |

## 재검증 주요 파일 SHA-256

| 실행 | `summary.json` | `metadata.json` | `prometheus-summary.json` |
| --- | --- | --- | --- |
| endpoint control 1 | `0fcf9e617cf6579181ff21d2e81748db5fb346cee54e0bc4a0786942ad76c7bd` | `30d552c82d822403c34aad277cfcb4e0965d1b36981457c6529c62736961d949` | `78406204b0cc589718fba22e3de0985858c7a45fe7185010bc3ab6729762abf3` |
| endpoint candidate 1 | `13c570f9e5052d558d51c8f56a6642b52279135b63da951e02cd331dbb1ab0a2` | `40490e0a275481688c40c9b2b52564ff7752ac9d5d94cc2b449ec10c59073291` | `164cc473c0e5b072d1570c5e16f8b1981ed58b3be9f6dae0f0611fc604852a24` |
| endpoint control 2 | `303b6a631462405f27a8a632c1b3767a190d0f8066600ea6e4e5c59beb0b27b1` | `b097cab4c770a12f311903f2418f601bc44e8c7530a873657bddaf73cc6cbc9b` | `6cdd59c1fff261939b923510a01de0d3507e55d221e257fbbac9c44f93736e34` |
| endpoint candidate 2 | `be1a0c6b64444be72c512108965821ec4b5bfa7eae433bad2b8140cb96511178` | `785a3007c8c07d7449db256d906eb2950d8d4dd97bd9fb2081e91d76a724e5e8` | `af6ff6889d34c2adee26e2c8eef005a50205195632440c93c1efcb6cf8340e71` |
| endpoint control 3 | `c7275ee31b1d420ba851fe8bb8f3cc1a4bb67df2a84175ba0cfaade73190d4e2` | `b370640b77aa00a4397b2bb19f6276c2707ad2453be8dcf55447f5c79bab05fe` | `3772049945795ed1ea3d045af122b6efed590f1c07e1e1a1b5e1c9833a68e8c0` |
| endpoint candidate 3 | `33be709ea2fe1a33427c303f53be7342042b1293468e3ea635d369d422822898` | `4b0fd7ead17fec172008358c5ae992a4a4133786adf922a6479f54fdb1eab3ff` | `2c86282a10a0cab18a32ad66f0d1cbab2315fb857c00133c7eea0a15114f94d0` |
| mixed control Average | `b77d053b73922df4ef46dd74cc00f721bf272fa07aa7e94a43fcefaff17789ec` | `0147880fbb28d3d364de06bf46d6b8db2a2ba2047ca815cd78e5b3962b2fa16a` | `b29f3dd367043fde9693831afcd496f095631a26b452f1599bc33dab862d1961` |
| mixed candidate Average | `4b29de78d5bd9b3d3792795d5144d00fc6c01f5cdff432d766802c4bf38d5649` | `6016ca1af6b9ca29aa7ae6d25bb9d24f5fedc3b435b77d9b9c8582f8f1f11113` | `7d82c3d94d38b50da12a7a78a378fb6298d0bae9b41ca7b2d1704fbc657215c2` |

## 결합 정식 실행 주요 파일 SHA-256

| 실행 | `summary.json` | `metadata.json` | `prometheus-summary.json` | `exit-code.txt` |
| --- | --- | --- | --- | --- |
| Average r1 | `6f8971cbb6ea8b04e9852471909e5fe1c8561c11917b01d3f6648c6dbf595c07` | `3efa493c14445f05f0e400a07d98c88c6bdc04560dec27b2edb85eed1f358c28` | `8ee871812fc6269d555766afe920e2f173242ae130b20a69725b207ee765837e` | `9a271f2a916b0b6ee6cecb2426f0b3206ef074578be55d9bc94f6f3fe3ab86aa` |
| Average r2 | `acb68fcca695ab3a36d1c1e4b1299520b50f092b9c7f1b142352882a27d23d4a` | `fc5aaad258ab2b55d91b5999e9400a5d067fc984f9618b331af29e1eb465e135` | `a845e00de1ef77a93e154551fd1c1ae96222afd8401bd378529bf1023f6f54bc` | `9a271f2a916b0b6ee6cecb2426f0b3206ef074578be55d9bc94f6f3fe3ab86aa` |
| Average r3 | `7985072d6ab89e3ecdf0b696cbfd628de296b66026e8ed2764f9469245eee748` | `d55511fe7f9628ff7b8d5d1ba7d0934b32ef76c062a8e9b30ceb3ecd5989e8f1` | `1e04c80441521d65056201412d891064a4acc3c2e570b36b85b88a8ed67a9920` | `9a271f2a916b0b6ee6cecb2426f0b3206ef074578be55d9bc94f6f3fe3ab86aa` |
| 무효 Peak | `11194406d13a4abe49c162d81ad96edf52f621afe6f064f91ee4ee6fe7d1d0be` | `8bd63b0a75ca54b8f751c83bf96d6764b58df63261be7834152fe9aead3924df` | `341d3c9ce3d39e1fe6c4e01e12f2e6ac1c715299282faf33a765de75e20b1bfb` | `7e332bcee418f7d700927c946d36341f0651d6d90997b58d3d5441dec96b2e74` |
| 유효 Peak r1 | `f2f8c4f52c31a4cb20c0590fa523961722cc7c95758c218fb1d7a56229d681e7` | `053645d241df4a5468c6fbf910da132e8a068246e5f0b91673d36812cff298a7` | `989f44d72d3f34d2da547466dc590f2ccb52493d408d6e6ba16adb47cf9cc9d0` | `9a271f2a916b0b6ee6cecb2426f0b3206ef074578be55d9bc94f6f3fe3ab86aa` |
| 유효 Peak r2 | `5a7ae42e87cabfc3fb24feeeb3e146ef6f18436f2d4fc4f9a19edd317cd5bcf6` | `c9d35c39a28ac8e3981fa34218eb29856241b776c83c34b798f8568623d41b5c` | `dff3db7480f62af31a957a4dee7b2a2b0c3282d18926a8a12ecafe77abd56783` | `9a271f2a916b0b6ee6cecb2426f0b3206ef074578be55d9bc94f6f3fe3ab86aa` |
| 유효 Peak r3 | `abee1dc86da3c8f63ea81fddeea42175a2437fa85f644043aad0a1553a3942b7` | `dc3e75b7ea9a9b332caaedd77df3a4b9fb7e371185954d037f92553436927181` | `c1f0f6d30b8ddba973f84ce699b13cf21344ec4fbcb1a3283efe7e3d46624f0b` | `9a271f2a916b0b6ee6cecb2426f0b3206ef074578be55d9bc94f6f3fe3ab86aa` |

최종 패키징 JAR은 `build/libs/Ilgeobolkka-0.0.1-SNAPSHOT.jar`에 있으며 크기는 87,823,059 bytes,
SHA-256은 `98b375a4bc14a290f445d5ef4c8ae81be7a2a19f9d13b8f943dc9c25b20ec843`이다.
