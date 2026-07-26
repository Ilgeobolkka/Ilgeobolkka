package com.example.ilgeobolkka.support.schema;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Transactional
class InkRentalOwnershipSchemaMigrationTest {

    private static final long READER_ID = 11_000L;
    private static final long SECOND_READER_ID = 11_001L;
    private static final long BOOK_ID = 12_000L;
    private static final long BOOK_PAGE_ID = 13_000L;

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    InkRentalOwnershipSchemaMigrationTest(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 잉크_계정은_독자별_한_행이며_잔액이_음수일_수_없다() {
        독자를_생성한다(READER_ID);
        독자를_생성한다(SECOND_READER_ID);
        잉크_계정을_생성한다(14_000L, READER_ID, 0);

        assertAll(
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () -> 잉크_계정을_생성한다(14_001L, READER_ID, 100)),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () -> 잉크_계정을_생성한다(14_002L, SECOND_READER_ID, -1)));
    }

    @Test
    void 페이지_이용권은_천원에_100잉크이며_결제_식별자가_고유하다() {
        독자를_생성한다(READER_ID);
        독자를_생성한다(SECOND_READER_ID);
        이용권_결제를_생성한다(15_000L, READER_ID, "ink-payment-1", 1_000, 100);

        assertAll(
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        이용권_결제를_생성한다(
                                                15_001L,
                                                SECOND_READER_ID,
                                                "ink-payment-1",
                                                1_000,
                                                100)),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        이용권_결제를_생성한다(
                                                15_002L,
                                                SECOND_READER_ID,
                                                "ink-payment-2",
                                                999,
                                                100)),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        이용권_결제를_생성한다(
                                                15_003L,
                                                SECOND_READER_ID,
                                                "ink-payment-3",
                                                1_000,
                                                99)));
    }

    @Test
    void 결제_시도는_PENDING_PAID_FAILED만_허용하고_PAID에만_완료_시각을_둔다() {
        독자를_생성한다(READER_ID);
        도서를_생성한다(BOOK_ID, 10_000);

        assertAll(
                () ->
                        assertDoesNotThrow(
                                () ->
                                        이용권_결제_시도를_생성한다(
                                                15_000L,
                                                READER_ID,
                                                "ink-pending",
                                                "PENDING",
                                                null)),
                () ->
                        assertDoesNotThrow(
                                () ->
                                        소장_결제_시도를_생성한다(
                                                18_000L,
                                                READER_ID,
                                                BOOK_ID,
                                                "ownership-failed",
                                                "FAILED",
                                                null)),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        이용권_결제_시도를_생성한다(
                                                15_001L,
                                                READER_ID,
                                                "ink-pending-paid-at",
                                                "PENDING",
                                                "2026-07-26 00:00:00.000000")),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        소장_결제_시도를_생성한다(
                                                18_001L,
                                                READER_ID,
                                                BOOK_ID,
                                                "ownership-paid-no-time",
                                                "PAID",
                                                null)),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        이용권_결제_시도를_생성한다(
                                                15_002L,
                                                READER_ID,
                                                "ink-unknown",
                                                "CANCELLED",
                                                null)));
    }

    @Test
    void 같은_페이지의_과거_대여_기간을_여러_건_보존한다() {
        대여_기본_데이터를_생성한다();

        assertAll(
                () ->
                        assertDoesNotThrow(
                                () ->
                                        페이지_대여를_생성한다(
                                                16_000L,
                                                READER_ID,
                                                "2026-07-26 00:00:00.000000",
                                                "2026-08-25 00:00:00.000000")),
                () ->
                        assertDoesNotThrow(
                                () ->
                                        페이지_대여를_생성한다(
                                                16_001L,
                                                READER_ID,
                                                "2026-08-25 00:00:00.000000",
                                                "2026-09-24 00:00:00.000000")));

        Integer rentalCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM page_rental WHERE reader_id = ? AND book_page_id = ?",
                        Integer.class,
                        READER_ID,
                        BOOK_PAGE_ID);
        assertEquals(2, rentalCount);
    }

    @Test
    void 페이지_대여_만료_시각은_시작_시각보다_뒤여야_한다() {
        대여_기본_데이터를_생성한다();

        assertAll(
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        페이지_대여를_생성한다(
                                                16_000L,
                                                READER_ID,
                                                "2026-07-26 00:00:00.000000",
                                                "2026-07-26 00:00:00.000000")),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        페이지_대여를_생성한다(
                                                16_001L,
                                                READER_ID,
                                                "2026-07-26 00:00:00.000000",
                                                "2026-07-25 23:59:59.999999")));
    }

    @Test
    void 잉크_원장은_100잉크_지급과_1잉크_대여_차감을_각각_저장한다() {
        대여_기본_데이터를_생성한다();
        이용권_결제를_생성한다(15_000L, READER_ID, "ink-payment-1", 1_000, 100);
        페이지_대여를_생성한다(
                16_000L,
                READER_ID,
                "2026-07-26 00:00:00.000000",
                "2026-08-25 00:00:00.000000");

        assertAll(
                () ->
                        assertDoesNotThrow(
                                () -> 지급_원장을_생성한다(17_000L, READER_ID, 100, 100, 15_000L)),
                () ->
                        assertDoesNotThrow(
                                () -> 차감_원장을_생성한다(17_001L, READER_ID, 1, 99, 16_000L)));
    }

    @Test
    void 잉크_원장은_지급과_대여_차감_중_정확히_한_원인을_가져야_한다() {
        대여_기본_데이터를_생성한다();
        이용권_결제를_생성한다(15_000L, READER_ID, "ink-payment-1", 1_000, 100);
        페이지_대여를_생성한다(
                16_000L,
                READER_ID,
                "2026-07-26 00:00:00.000000",
                "2026-08-25 00:00:00.000000");

        assertAll(
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () -> 지급_원장을_생성한다(17_000L, READER_ID, 99, 99, 15_000L)),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () -> 차감_원장을_생성한다(17_001L, READER_ID, 2, 98, 16_000L)),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        jdbcTemplate.update(
                                                """
                                                INSERT INTO ink_ledger
                                                    (id, reader_id, type, amount, balance_after,
                                                     ink_purchase_id, page_rental_id, occurred_at)
                                                VALUES (?, ?, 'GRANT', 100, 100, ?, ?,
                                                        '2026-07-26 00:00:00.000000')
                                                """,
                                                17_002L,
                                                READER_ID,
                                                15_000L,
                                                16_000L)),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        jdbcTemplate.update(
                                                """
                                                INSERT INTO ink_ledger
                                                    (id, reader_id, type, amount, balance_after,
                                                     occurred_at)
                                                VALUES (?, ?, 'DEDUCTION', 1, 99,
                                                        '2026-07-26 00:00:00.000000')
                                                """,
                                                17_003L,
                                                READER_ID)),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () -> 지급_원장을_생성한다(17_004L, READER_ID, 100, -1, 15_000L)));
    }

    @Test
    void 하나의_결제나_대여에_잉크_원장을_두_번_연결할_수_없다() {
        대여_기본_데이터를_생성한다();
        독자를_생성한다(SECOND_READER_ID);
        이용권_결제를_생성한다(15_000L, READER_ID, "ink-payment-1", 1_000, 100);
        페이지_대여를_생성한다(
                16_000L,
                READER_ID,
                "2026-07-26 00:00:00.000000",
                "2026-08-25 00:00:00.000000");
        지급_원장을_생성한다(17_000L, READER_ID, 100, 100, 15_000L);
        차감_원장을_생성한다(17_001L, READER_ID, 1, 99, 16_000L);

        assertAll(
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () -> 지급_원장을_생성한다(17_002L, READER_ID, 100, 200, 15_000L)),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () -> 차감_원장을_생성한다(17_003L, READER_ID, 1, 98, 16_000L)),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        지급_원장을_생성한다(
                                                17_004L,
                                                SECOND_READER_ID,
                                                100,
                                                100,
                                                15_000L)));
    }

    @Test
    void 소장_결제는_도서_원가와_일치하고_결제_식별자가_고유해야_한다() {
        독자를_생성한다(READER_ID);
        독자를_생성한다(SECOND_READER_ID);
        도서를_생성한다(BOOK_ID, 10_000);
        소장_결제를_생성한다(18_000L, READER_ID, BOOK_ID, "ownership-payment-1", 10_000);

        assertAll(
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        소장_결제를_생성한다(
                                                18_001L,
                                                SECOND_READER_ID,
                                                BOOK_ID,
                                                "ownership-payment-2",
                                                9_999)),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        소장_결제를_생성한다(
                                                18_002L,
                                                SECOND_READER_ID,
                                                BOOK_ID,
                                                "ownership-payment-1",
                                                10_000)));
    }

    @Test
    void 온라인_소장은_독자와_도서별_한_건이며_같은_결제의_소유자에게만_부여한다() {
        독자를_생성한다(READER_ID);
        독자를_생성한다(SECOND_READER_ID);
        도서를_생성한다(BOOK_ID, 10_000);
        소장_결제를_생성한다(18_000L, READER_ID, BOOK_ID, "ownership-payment-1", 10_000);
        소장_결제를_생성한다(18_001L, READER_ID, BOOK_ID, "ownership-payment-2", 10_000);
        소장_결제를_생성한다(
                18_002L, SECOND_READER_ID, BOOK_ID, "ownership-payment-3", 10_000);
        온라인_소장을_생성한다(19_000L, READER_ID, BOOK_ID, 18_000L);

        assertAll(
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        온라인_소장을_생성한다(
                                                19_001L,
                                                READER_ID,
                                                BOOK_ID,
                                                18_001L)),
                () ->
                        assertThrows(
                                DataAccessException.class,
                                () ->
                                        온라인_소장을_생성한다(
                                                19_002L,
                                                SECOND_READER_ID,
                                                BOOK_ID,
                                                18_001L)),
                () ->
                        assertDoesNotThrow(
                                () ->
                                        온라인_소장을_생성한다(
                                                19_003L,
                                                SECOND_READER_ID,
                                                BOOK_ID,
                                                18_002L)));
    }

    private void 대여_기본_데이터를_생성한다() {
        독자를_생성한다(READER_ID);
        도서를_생성한다(BOOK_ID, 10_000);
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content)
                VALUES (?, ?, 1, 'TEXT', '본문')
                """,
                BOOK_PAGE_ID,
                BOOK_ID);
    }

    private void 독자를_생성한다(long readerId) {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, 'hash', '2026-07-26 00:00:00.000000')
                """,
                readerId,
                "reader" + readerId + "@example.com");
    }

    private void 도서를_생성한다(long bookId, int priceWon) {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, total_page_count, price_won)
                VALUES (?, '소설', '제목', '저자', 100, ?)
                """,
                bookId,
                priceWon);
    }

    private void 잉크_계정을_생성한다(long accountId, long readerId, int balance) {
        jdbcTemplate.update(
                "INSERT INTO ink_account (id, reader_id, balance) VALUES (?, ?, ?)",
                accountId,
                readerId,
                balance);
    }

    private void 이용권_결제를_생성한다(
            long purchaseId,
            long readerId,
            String paymentId,
            int amountWon,
            int grantedInk) {
        jdbcTemplate.update(
                """
                INSERT INTO ink_purchase
                    (id, reader_id, payment_id, status, amount_won, granted_ink,
                     created_at, paid_at)
                VALUES (?, ?, ?, 'PAID', ?, ?,
                        '2026-07-26 00:00:00.000000', '2026-07-26 00:00:00.000000')
                """,
                purchaseId,
                readerId,
                paymentId,
                amountWon,
                grantedInk);
    }

    private void 이용권_결제_시도를_생성한다(
            long purchaseId,
            long readerId,
            String paymentId,
            String status,
            String paidAt) {
        jdbcTemplate.update(
                """
                INSERT INTO ink_purchase
                    (id, reader_id, payment_id, status, amount_won, granted_ink,
                     created_at, paid_at)
                VALUES (?, ?, ?, ?, 1000, 100, '2026-07-26 00:00:00.000000', ?)
                """,
                purchaseId,
                readerId,
                paymentId,
                status,
                paidAt);
    }

    private void 페이지_대여를_생성한다(
            long rentalId, long readerId, String rentedAt, String expiresAt) {
        jdbcTemplate.update(
                """
                INSERT INTO page_rental
                    (id, reader_id, book_page_id, rented_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                rentalId,
                readerId,
                BOOK_PAGE_ID,
                rentedAt,
                expiresAt);
    }

    private void 지급_원장을_생성한다(
            long ledgerId,
            long readerId,
            int amount,
            int balanceAfter,
            long inkPurchaseId) {
        jdbcTemplate.update(
                """
                INSERT INTO ink_ledger
                    (id, reader_id, type, amount, balance_after,
                     ink_purchase_id, occurred_at)
                VALUES (?, ?, 'GRANT', ?, ?, ?, '2026-07-26 00:00:00.000000')
                """,
                ledgerId,
                readerId,
                amount,
                balanceAfter,
                inkPurchaseId);
    }

    private void 차감_원장을_생성한다(
            long ledgerId,
            long readerId,
            int amount,
            int balanceAfter,
            long pageRentalId) {
        jdbcTemplate.update(
                """
                INSERT INTO ink_ledger
                    (id, reader_id, type, amount, balance_after,
                     page_rental_id, occurred_at)
                VALUES (?, ?, 'DEDUCTION', ?, ?, ?, '2026-07-26 00:00:00.000000')
                """,
                ledgerId,
                readerId,
                amount,
                balanceAfter,
                pageRentalId);
    }

    private void 소장_결제를_생성한다(
            long ownershipPaymentId,
            long readerId,
            long bookId,
            String paymentId,
            int amountWon) {
        jdbcTemplate.update(
                """
                INSERT INTO ownership_payment
                    (id, reader_id, book_id, payment_id, status, amount_won,
                     created_at, paid_at)
                VALUES (?, ?, ?, ?, 'PAID', ?,
                        '2026-07-26 00:00:00.000000', '2026-07-26 00:00:00.000000')
                """,
                ownershipPaymentId,
                readerId,
                bookId,
                paymentId,
                amountWon);
    }

    private void 소장_결제_시도를_생성한다(
            long ownershipPaymentId,
            long readerId,
            long bookId,
            String paymentId,
            String status,
            String paidAt) {
        jdbcTemplate.update(
                """
                INSERT INTO ownership_payment
                    (id, reader_id, book_id, payment_id, status, amount_won,
                     created_at, paid_at)
                VALUES (?, ?, ?, ?, ?, 10000, '2026-07-26 00:00:00.000000', ?)
                """,
                ownershipPaymentId,
                readerId,
                bookId,
                paymentId,
                status,
                paidAt);
    }

    private void 온라인_소장을_생성한다(
            long ownershipId, long readerId, long bookId, long ownershipPaymentId) {
        jdbcTemplate.update(
                """
                INSERT INTO book_ownership
                    (id, reader_id, book_id, ownership_payment_id, created_at)
                VALUES (?, ?, ?, ?, '2026-07-26 00:00:00.000000')
                """,
                ownershipId,
                readerId,
                bookId,
                ownershipPaymentId);
    }
}
