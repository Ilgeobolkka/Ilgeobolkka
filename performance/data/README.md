# 결정적 mvp 데이터

`performance` + `performance-seed` 프로필이 Flyway 적용 뒤 기존 성능 데이터를 지우고 다음 상태를 만든다.

- 도서 100권, TEXT 페이지 400개
- 독자 1,000명: 신규 334명, 활성 대여 333명, 소장 333명
- 모든 독자에게 100잉크 지급 원장, 활성 대여 독자에게 1잉크 차감·대여·서재 기록
- 소장 독자에게 로컬 PAID 결제·소장·서재 기록

초기화 전 live JDBC catalog가 정확히 `ilgeobolkka_perf`인지 검사한다. 실행 뒤
`dataset-counts.sql`과 `verify-invariants.sql`로 행 수와 잔액·원장 불변식을 검증한다. 동시성 실행은
`verify-contention-case.sql`을 함께 적용해 케이스별 예상 잔액·대여·차감·서재·현재 세션 수를 대조한다.
