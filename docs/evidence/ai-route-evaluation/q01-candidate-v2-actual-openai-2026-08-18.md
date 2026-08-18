# Q01 `air-candidate-v2` actual OpenAI 재평가

2026-08-18 KST에 후보 하한과 상한을 `0.15`·40개로 바꾼 `air-candidate-v2`를 로컬 후보 DB와 실제
OpenAI로 검증했습니다. 결론은 **Q01 실패**입니다. v1에서 처음 드러난 `NO_RELEVANT_PAGES`는
`case-book-019`까지 재현되지 않았지만, 12건 완료 뒤 `case-book-023`의 Responses 구간이 전체 20초
제한을 소진했습니다.

## 실행 경계

| 항목 | 값 |
| --- | --- |
| 브랜치 / 기준 HEAD | `codex/ai-route-local-release-readiness` / `a84a75f3757050490e52047955e419e048814785` |
| 실행 코드 | 위 HEAD + 이 브랜치의 미커밋 `air-candidate-v2` 코드·테스트·정본 diff |
| manifest SHA-256 | `24e52d288f78abd811d80ed25776b6c78da685154266c99329e5e7ee25da759c` |
| manifest / evaluation Git revision | `a580a420c0326f5256073ad48acb132a271fd816` / 동일 |
| content / evaluation | `ai-route-v2` / 90 cases |
| embedding / route model | `text-embedding-3-small` 1536차원 / `gpt-5.6-terra` |
| 후보 정책 | `air-candidate-v2`, 최소 similarity `0.15`, 최대 40개 |
| prompt / schema | `air-route-prompt-v3:sha256:fea8058d…` / `air-route-schema-v1:sha256:0515915d…` |
| reasoning | `low` |
| 후보 DB | 로컬 전용 `ilgeobolkka_ai_candidate_20260818`; 실행 후 support true 0권 |

`.env`의 project·API key·data policy 값은 명령·로그·artifact·문서에 출력하지 않았습니다. Responses
요청은 `store=false`입니다. project hard limit의 로그인 dashboard 독립 확인 상태도 이전 실행과 같이
미완료입니다.

## 경계 테스트와 로컬 기술 검증

새 정책을 생산 코드보다 먼저 테스트에 반영한 첫 실행은 60건 중 임계값·버전·상한 경계 7건이 예상대로
실패했습니다. 생산 코드 적용 뒤 후보 선택·출력 검증·평가 서비스·MySQL generation 통합 집중 테스트가
통과했습니다.

- `./gradlew test`: 1,108 tests, failures/errors 0, 기존 opt-in 2건 skipped
- `./gradlew check`: 통과
- `./gradlew build`: 통과, 새 boot JAR 생성
- 패키징 JAR + 후보 DB + `AI_ROUTE_ENABLED=true`: 3.055초 기동
- `GET /api/smoke`: 204, `GET /api/books?page=1`: 200
- `git diff --check`: 통과

skipped 2건은 기존 opt-in 실제 PDF 100권 Poppler 변환·MySQL 적재 테스트입니다. 이번 정책은 임베딩
모델·manifest·저장 vector를 바꾸지 않아 기존 후보 DB를 읽었으며 콘텐츠 재적재를 실행하지 않았습니다.

## 실제 Q01 결과

artifact는 `var/evaluation/ai-route-evaluation-20260818-candidate-v2.json`에만 두었고 SHA-256은
`6ca8120d341b8a5273850782a640571a12b281d4d92d288848491ae78ddc389e`입니다. purpose·분석 text·provider
원문은 포함하지 않습니다.

| 항목 | 관찰값 |
| --- | ---: |
| 완료 case | 12 / 90 |
| 첫 실패 | `case-book-023 / TIMEOUT` |
| 실패 전체 시간 | 20.006초 |
| 실패 Responses 구간 | 19.604초 |
| 실패 콘텐츠 준비 / 목적 Embedding / 후보 선택 | 0.154초 / 0.247초 / 0.215ms |
| 완료 case 시간 범위 | 3.568~14.475초 |
| 완료 12건 중 10초 초과 | 3건 |

후보 선택 시간은 실패 case에서도 1ms보다 짧아 exact cosine이나 40개 절단이 로컬 병목은 아닙니다.
대기 시간은 Responses에 집중됐습니다. 완료한 `case-book-019`는 3.568초에 ROUTE 5페이지를 반환해
v1의 해당 `NO_RELEVANT_PAGES` 경계는 해소됐습니다.

완료 12건뿐인 부분 표본으로 정식 p95를 계산하거나 90건 지표로 대체하지 않습니다. 다만 이미 3건이
10초를 넘고 다음 case가 20초 제한을 초과했으므로, 현재 조합은 Q02의 전체 90건 p95 10초 이하·20초
초과 0건 기준을 충족했다고 볼 수 없습니다. 실패 artifact에는 전체 case의 후보 임계값 review가 없으므로
이전 `0.15 / 40개` 사전검사의 약 95.4% recall도 이번 Q01 성공 지표로 승격하지 않습니다.

## 출시 게이트 판정

- Q01: **실패** — 12건 완료 뒤 Responses timeout, 90건 전체 ROUTE 없음
- Q02 자동 지표·사람 유용성·최종 report: 미실행 — 성공한 Q01 90건 artifact가 없음
- 지원 활성화: 미실행 — 후보 DB 100권 모두 `ai_route_supported=false` 유지
- 실제 OpenAI 인증 브라우저 여정: 미실행 — 활성화 선행 조건 미충족

다음 선택은 40개 후보의 품질 범위를 유지하면서 Responses 지연을 줄이는 별도 prompt/model/검증 계약
변경입니다. 단순 재실행은 공급자 지연의 변동성은 확인할 수 있어도 이미 관찰된 10초 초과와 20초 실패를
해결하지 않으므로 출시 통과 근거가 아닙니다.
