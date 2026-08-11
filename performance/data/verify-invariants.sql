SELECT 'balance_mismatch', COUNT(*)
FROM (
    SELECT account.reader_id,
           account.balance,
           SUM(CASE ledger.type
               WHEN 'GRANT' THEN ledger.amount
               WHEN 'DEDUCTION' THEN -ledger.amount
               ELSE 0 END) AS ledger_balance
    FROM ink_account account
    JOIN ink_ledger ledger ON ledger.reader_id = account.reader_id
    GROUP BY account.reader_id, account.balance
) balance_state
WHERE balance <> ledger_balance
UNION ALL
SELECT 'negative_balance', COUNT(*)
FROM ink_account
WHERE balance < 0
UNION ALL
SELECT 'deduction_without_rental', COUNT(*)
FROM ink_ledger ledger
LEFT JOIN page_rental rental
  ON rental.reader_id = ledger.reader_id
 AND rental.id = ledger.page_rental_id
WHERE ledger.type = 'DEDUCTION'
  AND rental.id IS NULL
UNION ALL
SELECT 'duplicate_ownership', COUNT(*)
FROM (
    SELECT reader_id, book_id
    FROM book_ownership
    GROUP BY reader_id, book_id
    HAVING COUNT(*) > 1
) duplicates
UNION ALL
SELECT 'active_rental_without_library', COUNT(*)
FROM page_rental rental
JOIN book_page page ON page.id = rental.book_page_id
LEFT JOIN library_entry entry
  ON entry.reader_id = rental.reader_id
 AND entry.book_id = page.book_id
WHERE rental.expires_at > UTC_TIMESTAMP(6)
  AND entry.id IS NULL
UNION ALL
SELECT 'ownership_without_library', COUNT(*)
FROM book_ownership ownership
LEFT JOIN library_entry entry
  ON entry.reader_id = ownership.reader_id
 AND entry.book_id = ownership.book_id
WHERE entry.id IS NULL;
