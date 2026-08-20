SELECT 'ink_purchase', COUNT(*) FROM ink_purchase
UNION ALL SELECT 'page_rental', COUNT(*) FROM page_rental
UNION ALL SELECT 'ink_ledger', COUNT(*) FROM ink_ledger
UNION ALL SELECT 'reader', COUNT(*) FROM reader
UNION ALL SELECT 'book', COUNT(*) FROM book
UNION ALL SELECT 'book_page', COUNT(*) FROM book_page
UNION ALL SELECT 'library_entry', COUNT(*) FROM library_entry;
