SELECT 'contention_same_page_balance_mismatch',
       IF((
           SELECT COUNT(*)
           FROM ink_account
           WHERE reader_id = 100001
             AND balance = 99
       ) = 1, 0, 1)
WHERE @contention_case = 'same-page'
UNION ALL
SELECT 'contention_same_page_rental_mismatch',
       IF((
           SELECT COUNT(*)
           FROM page_rental
           WHERE reader_id = 100001
             AND book_page_id = 400
       ) = 1
       AND (
           SELECT COUNT(*)
           FROM page_rental
           WHERE reader_id = 100001
       ) = 1, 0, 1)
WHERE @contention_case = 'same-page'
UNION ALL
SELECT 'contention_same_page_deduction_mismatch',
       IF((
           SELECT COUNT(*)
           FROM ink_ledger ledger
           JOIN page_rental rental
             ON rental.reader_id = ledger.reader_id
            AND rental.id = ledger.page_rental_id
           WHERE ledger.reader_id = 100001
             AND ledger.type = 'DEDUCTION'
             AND rental.book_page_id = 400
       ) = 1
       AND (
           SELECT COUNT(*)
           FROM ink_ledger
           WHERE reader_id = 100001
             AND type = 'DEDUCTION'
       ) = 1, 0, 1)
WHERE @contention_case = 'same-page'
UNION ALL
SELECT 'contention_same_page_library_mismatch',
       IF((
           SELECT COUNT(*)
           FROM library_entry
           WHERE reader_id = 100001
             AND book_id = 100
             AND last_page_number = 4
       ) = 1
       AND (
           SELECT COUNT(*)
           FROM library_entry
           WHERE reader_id = 100001
       ) = 1, 0, 1)
WHERE @contention_case = 'same-page'
UNION ALL
SELECT 'contention_same_page_session_mismatch',
       IF((
           SELECT COUNT(*)
           FROM reading_session
           WHERE reader_id = 100001
             AND book_id = 100
             AND current_page_number = 4
       ) = 1, 0, 1)
WHERE @contention_case = 'same-page'

UNION ALL
SELECT 'contention_different_pages_balance_mismatch',
       IF((
           SELECT COUNT(*)
           FROM ink_account
           WHERE reader_id = 100004
             AND balance = 80
       ) = 1, 0, 1)
WHERE @contention_case = 'different-pages'
UNION ALL
SELECT 'contention_different_pages_rental_mismatch',
       IF((
           SELECT COUNT(*)
           FROM page_rental
           WHERE reader_id = 100004
       ) = 20
       AND (
           SELECT COUNT(DISTINCT book_page_id)
           FROM page_rental
           WHERE reader_id = 100004
             AND book_page_id BETWEEN 1 AND 20
       ) = 20, 0, 1)
WHERE @contention_case = 'different-pages'
UNION ALL
SELECT 'contention_different_pages_deduction_mismatch',
       IF((
           SELECT COUNT(*)
           FROM ink_ledger ledger
           JOIN page_rental rental
             ON rental.reader_id = ledger.reader_id
            AND rental.id = ledger.page_rental_id
           WHERE ledger.reader_id = 100004
             AND ledger.type = 'DEDUCTION'
             AND rental.book_page_id BETWEEN 1 AND 20
       ) = 20
       AND (
           SELECT COUNT(DISTINCT ledger.page_rental_id)
           FROM ink_ledger ledger
           WHERE ledger.reader_id = 100004
             AND ledger.type = 'DEDUCTION'
       ) = 20, 0, 1)
WHERE @contention_case = 'different-pages'
UNION ALL
SELECT 'contention_different_pages_library_mismatch',
       IF((
           SELECT COUNT(*)
           FROM library_entry
           WHERE reader_id = 100004
             AND book_id BETWEEN 1 AND 5
       ) = 5
       AND (
           SELECT COUNT(*)
           FROM library_entry
           WHERE reader_id = 100004
       ) = 5, 0, 1)
WHERE @contention_case = 'different-pages'
UNION ALL
SELECT 'contention_different_pages_session_mismatch',
       IF((
           SELECT COUNT(*)
           FROM reading_session
           WHERE reader_id = 100004
             AND book_id BETWEEN 1 AND 5
             AND current_page_number BETWEEN 1 AND 4
       ) = 1, 0, 1)
WHERE @contention_case = 'different-pages'

UNION ALL
SELECT 'contention_different_readers_balance_mismatch',
       IF((
           SELECT COUNT(*)
           FROM ink_account
           WHERE reader_id BETWEEN 100001 AND 100298
             AND MOD(reader_id - 100001, 3) = 0
             AND balance = 99
       ) = 100, 0, 1)
WHERE @contention_case = 'different-readers'
UNION ALL
SELECT 'contention_different_readers_rental_mismatch',
       IF((
           SELECT COUNT(*)
           FROM page_rental
           WHERE reader_id BETWEEN 100001 AND 100298
             AND MOD(reader_id - 100001, 3) = 0
       ) = 100
       AND (
           SELECT COUNT(DISTINCT reader_id)
           FROM page_rental
           WHERE reader_id BETWEEN 100001 AND 100298
             AND MOD(reader_id - 100001, 3) = 0
       ) = 100
       AND (
           SELECT COUNT(DISTINCT book_page_id)
           FROM page_rental
           WHERE reader_id BETWEEN 100001 AND 100298
             AND MOD(reader_id - 100001, 3) = 0
       ) = 100
       AND (
           SELECT COUNT(*)
           FROM page_rental
           WHERE reader_id BETWEEN 100001 AND 100298
             AND MOD(reader_id - 100001, 3) = 0
             AND book_page_id = FLOOR((reader_id - 100001) / 3) + 1
       ) = 100, 0, 1)
WHERE @contention_case = 'different-readers'
UNION ALL
SELECT 'contention_different_readers_deduction_mismatch',
       IF((
           SELECT COUNT(*)
           FROM ink_ledger ledger
           JOIN page_rental rental
             ON rental.reader_id = ledger.reader_id
            AND rental.id = ledger.page_rental_id
           WHERE ledger.reader_id BETWEEN 100001 AND 100298
             AND MOD(ledger.reader_id - 100001, 3) = 0
             AND ledger.type = 'DEDUCTION'
       ) = 100
       AND (
           SELECT COUNT(DISTINCT ledger.reader_id)
           FROM ink_ledger ledger
           WHERE ledger.reader_id BETWEEN 100001 AND 100298
             AND MOD(ledger.reader_id - 100001, 3) = 0
             AND ledger.type = 'DEDUCTION'
       ) = 100
       AND (
           SELECT COUNT(DISTINCT ledger.page_rental_id)
           FROM ink_ledger ledger
           WHERE ledger.reader_id BETWEEN 100001 AND 100298
             AND MOD(ledger.reader_id - 100001, 3) = 0
             AND ledger.type = 'DEDUCTION'
       ) = 100, 0, 1)
WHERE @contention_case = 'different-readers'
UNION ALL
SELECT 'contention_different_readers_library_mismatch',
       IF((
           SELECT COUNT(*)
           FROM library_entry
           WHERE reader_id BETWEEN 100001 AND 100298
             AND MOD(reader_id - 100001, 3) = 0
       ) = 100
       AND (
           SELECT COUNT(DISTINCT reader_id)
           FROM library_entry
           WHERE reader_id BETWEEN 100001 AND 100298
             AND MOD(reader_id - 100001, 3) = 0
       ) = 100
       AND (
           SELECT COUNT(*)
           FROM library_entry
           WHERE reader_id BETWEEN 100001 AND 100298
             AND MOD(reader_id - 100001, 3) = 0
             AND book_id = FLOOR((reader_id - 100001) / 12) + 1
             AND last_page_number = MOD(FLOOR((reader_id - 100001) / 3), 4) + 1
       ) = 100, 0, 1)
WHERE @contention_case = 'different-readers'
UNION ALL
SELECT 'contention_different_readers_session_mismatch',
       IF((
           SELECT COUNT(*)
           FROM reading_session
           WHERE reader_id BETWEEN 100001 AND 100298
             AND MOD(reader_id - 100001, 3) = 0
       ) = 100
       AND (
           SELECT COUNT(*)
           FROM reading_session
           WHERE reader_id BETWEEN 100001 AND 100298
             AND MOD(reader_id - 100001, 3) = 0
             AND book_id = FLOOR((reader_id - 100001) / 12) + 1
             AND current_page_number = MOD(FLOOR((reader_id - 100001) / 3), 4) + 1
       ) = 100, 0, 1)
WHERE @contention_case = 'different-readers'

UNION ALL
SELECT 'contention_session_balance_mismatch',
       IF((
           SELECT COUNT(*)
           FROM ink_account
           WHERE reader_id = 100007
             AND balance = 100
       ) = 1, 0, 1)
WHERE @contention_case = 'session'
UNION ALL
SELECT 'contention_session_rental_mismatch',
       IF((
           SELECT COUNT(*)
           FROM page_rental
           WHERE reader_id = 100007
       ) = 0, 0, 1)
WHERE @contention_case = 'session'
UNION ALL
SELECT 'contention_session_deduction_mismatch',
       IF((
           SELECT COUNT(*)
           FROM ink_ledger
           WHERE reader_id = 100007
             AND type = 'DEDUCTION'
       ) = 0, 0, 1)
WHERE @contention_case = 'session'
UNION ALL
SELECT 'contention_session_library_mismatch',
       IF((
           SELECT COUNT(*)
           FROM library_entry
           WHERE reader_id = 100007
       ) = 0, 0, 1)
WHERE @contention_case = 'session'
UNION ALL
SELECT 'contention_session_current_session_mismatch',
       IF((
           SELECT COUNT(*)
           FROM reading_session
           WHERE reader_id = 100007
       ) = 0, 0, 1)
WHERE @contention_case = 'session';
