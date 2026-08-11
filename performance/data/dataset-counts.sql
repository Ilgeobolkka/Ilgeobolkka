SELECT 'book', COUNT(*) FROM book
UNION ALL SELECT 'book_page', COUNT(*) FROM book_page
UNION ALL SELECT 'reader', COUNT(*) FROM reader
UNION ALL SELECT 'ink_account', COUNT(*) FROM ink_account
UNION ALL SELECT 'ink_purchase', COUNT(*) FROM ink_purchase
UNION ALL SELECT 'ink_ledger', COUNT(*) FROM ink_ledger
UNION ALL SELECT 'page_rental', COUNT(*) FROM page_rental
UNION ALL SELECT 'ownership_payment', COUNT(*) FROM ownership_payment
UNION ALL SELECT 'book_ownership', COUNT(*) FROM book_ownership
UNION ALL SELECT 'library_entry', COUNT(*) FROM library_entry
UNION ALL SELECT 'reading_session', COUNT(*) FROM reading_session;
