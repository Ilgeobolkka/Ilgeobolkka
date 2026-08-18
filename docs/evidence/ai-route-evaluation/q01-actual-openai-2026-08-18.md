# Q01 actual OpenAI 재평가 — 100권 후보 DB

2026-08-18 KST에 `ai-route-v2` 전체 콘텐츠를 새 로컬 후보 DB에 적재하고 실제 OpenAI로 Q01을 다시
실행했습니다. 결론은 **Q01 실패**입니다. Responses 지연 문제는 분리·완화했지만 현행
`air-candidate-v1`의 cosine 하한 `0.30`에서 관련 후보가 없는 평가 case가 확인됐습니다.

## 실행 경계

| 항목 | 값 |
| --- | --- |
| 브랜치 / 기준 HEAD | `codex/ai-route-local-release-readiness` / `d076ea4a6189f2c45abfe43af1cb525fa7db3217` |
| 실행 코드 | 위 HEAD + 이 브랜치의 미커밋 계측·batch·reasoning diff |
| manifest SHA-256 | `24e52d288f78abd811d80ed25776b6c78da685154266c99329e5e7ee25da759c` |
| content / evaluation | `ai-route-v2` / 90 cases |
| embedding / route model | `text-embedding-3-small` 1536차원 / `gpt-5.6-terra` |
| 후보 정책 | `air-candidate-v1`, 최소 similarity `0.30`, 최대 30개 |
| 후보 DB | 로컬 전용 `ilgeobolkka_ai_candidate_20260818`; 기존 DB 변경 없음 |

`.env`의 실제 project·API key·data policy 값은 명령·로그·artifact·문서에 출력하지 않았습니다. OpenAI
API 데이터는 명시적으로 opt-in하지 않는 한 훈련에 사용되지 않지만 abuse monitoring log에는 기본 최대
30일 보관될 수 있습니다. Responses 요청은 구현에서 `store=false`입니다.
[OpenAI data controls](https://developers.openai.com/api/docs/guides/your-data)

전용 project hard limit은 로그인된 dashboard가 없어 독립 확인하지 못했습니다. 공식 계약상 hard limit
초과는 `429 project_spend_limit_exceeded`이며 집계는 즉시 반영되지 않을 수 있습니다.
[OpenAI project spend limits](https://developers.openai.com/api/docs/guides/spend-limits)

## 후보 DB 적재

기존 단건 Embeddings 요청은 진행 속도로 보아 전체 적재가 한 시간을 넘길 가능성이 있어 DB write 전에
중단했습니다. 이후 순서를 보존하는 다중 입력 요청과 100페이지 단위 batch를 적용했습니다. OpenAI
Embeddings API는 요청 하나에 입력 배열을 허용하고 각 응답의 `index`로 입력을 연결합니다.
[Embeddings create API](https://developers.openai.com/api/reference/resources/embeddings/methods/create)

- 실제 batch 적재: 약 3분 8초, 성공
- 도서: 100권, 외부 전송 허용 후보 90권, `ai_route_supported=true` 0권
- 페이지: 4,584개, 후보 4,454개, embedding 보유 후보 4,454개
- 비후보 페이지의 embedding: 0개

## Q01 실행 결과

| 설정·artifact | 관찰 결과 | 판정 |
| --- | --- | --- |
| 기본 reasoning, `ai-route-evaluation-20260818-diagnostic.json` | 첫 case timeout. 전체 20.144초 중 Responses 19.152초 | 실패 |
| `reasoning=none`, `ai-route-evaluation-20260818-reasoning-none-detail.json` | 첫 case 6.051초, `INVALID_OUTPUT / MISSING_PREREQUISITE`; Responses 5.080초 | 실패 |
| `reasoning=low`, `ai-route-evaluation-20260818-reasoning-low.json` | 8건 완료 뒤 `case-book-019 / NO_RELEVANT_PAGES`; 완료 8건 3.314~9.540초 | 실패 |

artifact SHA-256은 순서대로
`aac9eb76c3e31ee60ed855bdfcd7d8aafb10d7d6209b97e402bf95c34828600d`,
`7fcf1d2370cedfd8e532a0b9a9c898fe07ae00c08a47372d91a23ac8c73446b9`,
`11e787e2294767847c579b77fd7b4d8b1769f2e6e9a7da24ac91718f7de13b09`입니다. artifact는 purpose·분석
text·provider 원문을 포함하지 않고 로컬 `var/evaluation/`에만 있습니다.

`none`은 지연을 줄였지만 선수 페이지 판단을 잃었고, `low`는 초기 semantic validation을 통과했습니다.
현재 구현 후보는 `low`이며 logical prompt 계약은
`air-route-prompt-v3:sha256:fea8058da8eb43054cb71ca4031ef13ce48aa6296d070370b5ea9dcc1a1e4c62`로
분리했습니다. OpenAI 가이드는 GPT-5.6에서 `none`과 `low`를 서로 다른 reasoning effort로 제공합니다.
[Reasoning effort guidance](https://developers.openai.com/api/docs/guides/latest-model#update-api-and-model-parameters)

## 후보 하한 사전검사

첫 실패만 고쳐 다음 실패를 숨기지 않도록 90개 목적을 하나의 Embeddings batch로 계산하고, 같은 후보 DB의
페이지 vector와 exact cosine을 로컬에서 비교했습니다. 출력에는 case ID·페이지 번호·점수만 남겼습니다.

| case | 전체 최고 후보 | 평가가 허용한 최고 후보 | `0.30` 판정 |
| --- | ---: | ---: | --- |
| `case-book-019` | page 14 / 약 `0.2803` | page 14 / 약 `0.2803` | 미달 |
| `case-book-034` | page 4 / 약 `0.2377` | page 4 / 약 `0.2377` | 미달 |
| `case-book-036` | page 42 / 약 `0.2986` | page 5 / 약 `0.2798` | 미달; 전체 최고는 평가 허용 밖 |

나머지 87건은 최고 후보가 `0.30` 이상이었습니다. Embeddings 재호출 사이의 미세한 부동소수점 차이는
있었지만 세 case의 경계 판정은 바뀌지 않았습니다.

현행 분석 text 입력으로 90건 전체의 선수 폐쇄 전 필수 개념 recall을 추가 계산했습니다. `0.30 / 30개`는
약 `54.8%`이고, 하한을 `0.15`까지 낮춰도 30개 상한의 recall ceiling은 약 `85.3%`였습니다. 따라서 하한만
낮추는 안으로는 새 후보 정책 검토 기준 95%를 만족할 수 없습니다.

| 진단 조합 | 필수 개념 recall | NO_ROUTE | 평균 후보 수 |
| --- | ---: | ---: | ---: |
| `0.30 / 30개` 현행 | 약 54.8% | 3 | 16.9 |
| `0.15 / 30개` | 약 85.1% | 0 | 29.8 |
| 하한 없음 / 35개 | 약 90.9% | 0 | 35.0 |
| `0.15 / 40개` | 약 95.4% | 0 | 39.5 |
| `0.15 / 45개` | 약 98.8% | 0 | 44.0 |

검수된 primary/secondary concept를 페이지 분석 text에 붙여 다시 embedding하는 실험은 top-30 ceiling이
약 80.3%로 더 나빠져 폐기했습니다. `text-embedding-3-large` 비교는 project에서 첫 요청부터
`403 / model_not_found`로 거절되어 페이지 batch를 실행하지 못했습니다.

`0.15 / 40개`는 이번 격자에서 95%를 넘는 가장 작은 상한 조합이지만, Responses 입력 후보가 현행보다
커져 p95 10초를 다시 넘을 위험이 있습니다. 이는 자동 채택값이 아니라 새 `air-candidate-v2`와 prompt
계약을 함께 설계·90건 재평가할 때의 출발점입니다.

현행 PRD는 후보 정책 변경 검토값을 `0.35`, `0.40`, `0.45`로 한정하고 모두 미달이면 `0.30`을
유지하도록 고정합니다. 따라서 이 실행에서 하한을 임의로 낮추거나 평가 정답을 약화하지 않았습니다.
세 도서의 페이지 분석 text와 평가 reference/alternative annotation을 함께 검수한 뒤, 콘텐츠를 고칠지 새
후보 정책을 승인할지 결정해야 합니다.

## 로컬 기술 회귀와 패키징 smoke

- `./gradlew test`: 1,107 tests, failures/errors 0, opt-in 2건 skipped
- `./gradlew check`: 통과
- `./gradlew build`: 통과, 새 boot JAR 생성
- 패키징 JAR + 후보 DB + `AI_ROUTE_ENABLED=true`: 기동 3.666초
- `GET /api/smoke`: 204, `GET /api/books?page=1`: 200

첫 패키징 기동에서는 로컬 `.env`의 시연 비밀번호 때문에 demo seeder가 후보 DB의 기존 book ID 11과
충돌해 종료됐습니다. 후보 DB 검증에서는 `DEMO_VALIDATION_PASSWORD`를 빈 값으로 명시해 시더를 실행하지
않았고 같은 JAR이 정상 기동했습니다. 검증 뒤 서버는 정상 종료했습니다.

skipped 2건은 기존 opt-in인 실제 PDF 100권 Poppler 변환·MySQL 적재 테스트입니다. 이번 실제 적재는
동일 manifest를 별도 `content-import` 실행으로 완료했지만, skipped 테스트를 통과했다고 바꾸어 기록하지
않습니다.

## 출시 게이트 판정

- Q01: **실패** — 90건 전체 ROUTE 결과 없음
- Q02 사람 유용성·최종 report: 미실행 — Q01 성공 artifact가 선행 조건
- 90권 지원 / 10권 미지원 활성화: 미실행, 후보 DB 지원 0권 유지
- 패키징 JAR 실제 OpenAI 인증 브라우저 여정: 미실행 — 활성화 선행 조건 미충족
- project hard limit: 공식 계약 확인, dashboard 독립 확인 미완료

실제 서버를 띄워야만 가능한 문제가 아니라 콘텐츠/평가/후보 정책의 출시 판단 문제입니다. 이 경계를
해결하고 Q01 90건을 통과하기 전에는 Q02·활성화·브라우저 결과를 완료로 기록하지 않습니다.
