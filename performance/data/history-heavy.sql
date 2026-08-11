CREATE TEMPORARY TABLE perf_history_sequence (
    sequence_number INT PRIMARY KEY
);

INSERT INTO perf_history_sequence (sequence_number)
SELECT ones.number + tens.number * 10 + hundreds.number * 100 + 1
FROM (
    SELECT 0 number UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) ones
CROSS JOIN (
    SELECT 0 number UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) tens
CROSS JOIN (
    SELECT 0 number UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
) hundreds
WHERE ones.number + tens.number * 10 + hundreds.number * 100 < 500;

INSERT INTO ink_purchase
    (id, reader_id, payment_id, status, amount_won, granted_ink, created_at, paid_at)
SELECT 10000000 + (reader.id - 100000) * 10 + cycle.sequence_number,
       reader.id,
       CONCAT(
           '30000000-0000-4000-8000-',
           LPAD((reader.id - 100000) * 10 + cycle.sequence_number, 12, '0')
       ),
       'PAID',
       1000,
       100,
       TIMESTAMP('2024-01-01 00:00:00')
           + INTERVAL ((cycle.sequence_number - 1) * 60) DAY,
       TIMESTAMP('2024-01-01 00:00:00')
           + INTERVAL ((cycle.sequence_number - 1) * 60) DAY
FROM reader
JOIN perf_history_sequence cycle ON cycle.sequence_number <= 5;

INSERT INTO page_rental (id, reader_id, book_page_id, rented_at, expires_at)
SELECT 20000000 + (reader.id - 100000) * 1000 + sequence.sequence_number,
       reader.id,
       MOD(sequence.sequence_number - 1, 400) + 1,
       TIMESTAMP('2024-01-01 00:00:00')
           + INTERVAL ((CEIL(sequence.sequence_number / 100) - 1) * 60) DAY
           + INTERVAL MOD(sequence.sequence_number - 1, 100) MINUTE,
       TIMESTAMP('2024-01-31 00:00:00')
           + INTERVAL ((CEIL(sequence.sequence_number / 100) - 1) * 60) DAY
           + INTERVAL MOD(sequence.sequence_number - 1, 100) MINUTE
FROM reader
CROSS JOIN perf_history_sequence sequence;

INSERT INTO ink_ledger
    (id, reader_id, type, amount, balance_after, ink_purchase_id, page_rental_id, occurred_at)
SELECT 30000000 + (reader.id - 100000) * 10 + cycle.sequence_number,
       reader.id,
       'GRANT',
       100,
       100,
       10000000 + (reader.id - 100000) * 10 + cycle.sequence_number,
       NULL,
       TIMESTAMP('2024-01-01 00:00:00')
           + INTERVAL ((cycle.sequence_number - 1) * 60) DAY
FROM reader
JOIN perf_history_sequence cycle ON cycle.sequence_number <= 5;

INSERT INTO ink_ledger
    (id, reader_id, type, amount, balance_after, ink_purchase_id, page_rental_id, occurred_at)
SELECT 40000000 + (reader.id - 100000) * 1000 + sequence.sequence_number,
       reader.id,
       'DEDUCTION',
       1,
       99 - MOD(sequence.sequence_number - 1, 100),
       NULL,
       20000000 + (reader.id - 100000) * 1000 + sequence.sequence_number,
       TIMESTAMP('2024-01-01 00:01:00')
           + INTERVAL ((CEIL(sequence.sequence_number / 100) - 1) * 60) DAY
           + INTERVAL MOD(sequence.sequence_number - 1, 100) MINUTE
FROM reader
CROSS JOIN perf_history_sequence sequence;

DROP TEMPORARY TABLE perf_history_sequence;
