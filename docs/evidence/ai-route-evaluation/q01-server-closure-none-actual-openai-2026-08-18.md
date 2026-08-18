# Q01 서버 선수 폐쇄·`reasoning=none` actual OpenAI 재평가

2026-08-18 KST에 모델은 검색 후보만 고르고 서버가 검증된 선수 DAG의 전이 폐쇄와 순서를 완성하도록
계약을 바꾼 뒤, 로컬 후보 DB와 실제 OpenAI로 대표 목적 90건 전체를 재평가했습니다. 결론은 **Q01
성공**입니다. 90건이 모두 `ROUTE`였고 p95는 5.506초, 20초 초과는 0건입니다.

Q01 성공은 Q02 최종 통과와 지원 활성화를 뜻하지 않습니다. 자동 지표 사전 계산은 기준을 통과했지만
지정 검수자의 90건 유용성 판정이 없어 Q02 report 작성과 support 활성화는 실행하지 않았습니다.

## 실행 경계

| 항목 | 값 |
| --- | --- |
| 브랜치 / 실행 HEAD | `codex/ai-route-local-release-readiness` / `9be28cf9cdb64bdc88eb9ba8ca0eb60843364dec` |
| 실행 계약 커밋 | `2dab76d` 서버 선수 폐쇄 구현 / `9be28cf` 정본 반영 |
| manifest SHA-256 | `24e52d288f78abd811d80ed25776b6c78da685154266c99329e5e7ee25da759c` |
| manifest / evaluation Git revision | `a580a420c0326f5256073ad48acb132a271fd816` / `9be28cf9cdb64bdc88eb9ba8ca0eb60843364dec` |
| content / evaluation | `ai-route-v2` / 90 cases |
| embedding / route model | `text-embedding-3-small` 1536차원 / `gpt-5.6-terra` |
| 후보 정책 | `air-candidate-v2`, 최소 similarity `0.15`, 최대 40개 |
| prompt | `air-route-prompt-v4:sha256:5eb50b97e6bfcc010864a901dd3b5ce380836a1463ea3e310ef94c5ce1711b28` |
| schema | `air-route-schema-v2:sha256:53b28a3201a56859487cfb57c17b8df0aad729b6e1f3066b2f467b57eb6779d9` |
| Responses | `reasoning=none`, `store=false`, 모델 입력에서 선수 graph 제외 |
| 후보 DB | 로컬 전용 `ilgeobolkka_ai_candidate_20260818`; 실행 후 support true 0/100권 |

실제 입력 파일인 `manifest.json`과 `evaluation.json`은 manifest 리비전 이후 변경이 없음을 실행 전에
확인했습니다. 같은 fixture 디렉터리의 `README.md`만 후속 문서 커밋에서 바뀌었으므로 입력 동일성
판정에는 포함하지 않았습니다. `.env`의 project·API key·data policy 값은 명령·로그·artifact·문서에
출력하지 않았습니다.

## 로컬 기술 검증

- 출력 검증·Responses adapter·생성 facade·평가 MySQL 집중 범위: 79 tests 통과
- `./gradlew test`: 1,108 tests, failures/errors 0, 기존 opt-in 2건 skipped
- `./gradlew check`: 통과
- `./gradlew build`: 통과, boot JAR 생성
- 패키징 JAR + 후보 DB + `AI_ROUTE_ENABLED=true`: 데모 시더를 끈 실행에서 3.97초 기동
- `GET /api/smoke`: 204, `GET /books`: 200
- `git diff --check`: 통과

패키징 JAR의 첫 로컬 profile 실행은 `.env`의 데모 검증 비밀번호를 읽어 후보 DB에 데모 고정 ID를
시드하려다 `시연 도서 ID가 다른 데이터와 충돌합니다: 11`로 종료됐습니다. 후보 DB smoke에서는 데모
시드가 대상이 아니므로 `DEMO_VALIDATION_PASSWORD`를 빈 값으로 명시해 같은 JAR를 다시 실행했고, DB나
코드는 변경하지 않았습니다.

최초 전체 suite의 skipped 2건은 기존 opt-in 실제 PDF 100권 Poppler 변환·MySQL 적재 테스트였습니다.
후속 로컬 완결 작업에서 두 테스트를 명시적으로 켜고 `--rerun-tasks`로 다시 실행했습니다.

| opt-in 검증 | 결과 |
| --- | --- |
| `ContentBatchConverterTest` | 17 tests, skipped/failures/errors 0; 실제 PDF 100권·4,584페이지 Poppler 변환 |
| `ContentImportFullMySqlIntegrationTest` | 1 test, skipped/failures/errors 0; 초기 100권·400페이지 Poppler 변환 후 MySQL 전체 적재 |

따라서 로컬에서 실행 가능한 콘텐츠 변환·전체 적재 opt-in 경계도 모두 통과했습니다. 이 실행은 기존
Q01 후보 DB의 support 상태를 바꾸지 않았습니다.

## 실제 Q01 결과

artifact는 Git 제외 경로인
`var/evaluation/ai-route-evaluation-20260818-server-closure-none.json`에만 두었고 SHA-256은
`12e66b112e609ade821289e25f7743f2dc8d6ea0cfa9863b7827a84817924e53`입니다. purpose·분석
text·provider 원문은 포함하지 않습니다.

| 항목 | 관찰값 |
| --- | ---: |
| 완료 case | 90 / 90 |
| runner 전체 벽시계 시간 | 4분 23초 |
| case 전체 시간 최소 / 평균 / p95 / 최대 | 1.410초 / 2.886초 / 5.506초 / 16.234초 |
| 10초 초과 / 20초 초과 | 2건 / 0건 |
| Responses 최소 / 평균 / p95 / 최대 | 1.017초 / 2.377초 / 3.840초 / 15.772초 |
| 목적 Embedding 최소 / 평균 / p95 / 최대 | 0.127초 / 0.338초 / 0.425초 / 2.128초 |

p95는 정본대로 90개 유효 시간을 오름차순 정렬한 86번째 값입니다. 이전 `reasoning=low` 실행에서
12건 완료 뒤 발생한 `case-book-023 / TIMEOUT`은 재현되지 않았습니다. 다만 10초 초과 2건이 있으므로
모든 요청이 10초 안이라는 뜻은 아니며, 출시 기준인 p95 10초 이하와 20초 초과 0건만 충족했습니다.

## Q02 자동 지표 사전 계산

Q01 artifact에 대해 `AiRouteEvaluationMetrics`와 같은 분자·분모·합집합·순서 규칙으로 계산한 값입니다.
지정 검수자 판정이 없는 사전 계산이므로 정식 Q02 report가 아닙니다.

| 자동 지표 | 관찰값 | 기준 | 사전 판정 |
| --- | ---: | ---: | --- |
| 필수 개념 포함률 | 402 / 482 = 83.40% | 80% 이상 | 통과 |
| 무관 또는 중복 추천 | 13 / 769 = 1.69% | 20% 이하 | 통과 |
| 필수 선수 관계 위반 | 0 / 730 = 0% | 5% 이하 | 통과 |
| 전체 처리 p95 | 5.506초 | 10초 이하 | 통과 |
| 20초 초과 | 0 / 90 | 0건 | 통과 |

후보 임계값 review는 `0.20`이 443/482(91.91%), `0.25`가 370/482(76.76%), `0.30`이
275/482(57.05%)로 모두 95%에 미달했습니다. 따라서 artifact의 선택값은 `0.15`이며
`air-candidate-v2`를 유지합니다.

## Q01 실행 직후 출시 게이트 판정

- Q01 실제 OpenAI 90건: **성공**
- Q02 자동 지표 사전 계산: **기준 통과**
- Q02 사람 유용성 90건과 최종 report: **미실행** — 지정 검수자 판정 파일 없음
- 지원 활성화: **미실행** — 후보 DB 100권 모두 `ai_route_supported=false` 유지
- 실제 OpenAI 인증 브라우저 여정: **미실행** — 지원 활성화 선행 조건 미충족

## 사람 검토 준비와 프로젝트 설정 재확인

Q01 artifact와 공개 guide metadata를 결합한 사람 검토 패킷을 Git 제외 경로에 만들었습니다. 두 파일은
case 90개가 모두 고유하고, Q01의 표시 경로와 정확히 일치하며, 지정 검수자의 판정을 대신하지 않도록
`useful`을 전부 미입력 상태로 유지합니다.

| 파일 | SHA-256 | 상태 |
| --- | --- | --- |
| `var/evaluation/ai-route-human-review-20260818.json` | `50223771915aa4a93f51f5e860902bf192780c8ecd4c0ee2a26f267a016ef512` | 90 cases, 판정 0 |
| `var/evaluation/ai-route-human-review-20260818.tsv` | `f9216902d4863b4ee04bb56d0fa53d389764281f7ad507a32cf68e0d7b8bfc43` | header 포함 91행, 판정 열 공란 |

로그인된 OpenAI Platform도 읽기 전용으로 확인했습니다. 전용 프로젝트의 허용 모델은
`gpt-5.6-terra`·`text-embedding-3-small`로 계약과 일치하고, API call logging은 호출별 설정이므로
애플리케이션의 `store=false`가 유효한 차단 경계입니다. 다만 월 지출액 `$10.00`은 설정되어 있어도
`Enforce a hard limit`가 꺼져 있어 강제 상한은 아닙니다. 상세 근거는
[2026-08-18 데이터 정책·프로젝트 설정 재확인](../openai-data-policy/2026-08-18.md)에 남겼습니다.

이 문서의 Q01 실행 직후에는 90개 경로의 지정 검수자 `useful` 판정이 다음 사용자 경계였습니다. 이후
사용자가 90건 전부를 `useful=true`로 제공했고, Q02 `finalize`와 후보 DB 지원 도서 90권 활성화까지
완료했습니다. 후속 결과는
[Q02 사람 유용성 판정·최종 report·후보 DB 활성화](./q02-server-closure-none-activation-2026-08-18.md)에
기록합니다.
