package com.example.ilgeobolkka.ink.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.ilgeobolkka.ink.entity.InkLedger;
import com.example.ilgeobolkka.ink.exception.InvalidInkLedgerException;
import com.example.ilgeobolkka.ink.exception.InkPurchaseNotFoundException;
import com.example.ilgeobolkka.ink.exception.InkPurchaseStateConflictException;
import com.example.ilgeobolkka.ink.exception.InsufficientInkException;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Transactional
class InkServiceMySqlIntegrationTest {

    private static final long READER_ID = 407_200L;
    private static final long OTHER_READER_ID = 407_201L;
    private static final long FIRST_BOOK_ID = 407_200L;
    private static final long SECOND_BOOK_ID = 407_201L;
    private static final long FIRST_PAGE_ID = 407_200L;
    private static final long SECOND_PAGE_ID = 407_201L;
    private static final long PAID_PURCHASE_ID = 407_200L;
    private static final long PENDING_PURCHASE_ID = 407_201L;
    private static final long SECOND_PAID_PURCHASE_ID = 407_202L;
    private static final long FIRST_RENTAL_ID = 407_200L;
    private static final long SECOND_RENTAL_ID = 407_201L;

    private final InkService inkService;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    InkServiceMySqlIntegrationTest(
            InkService inkService,
            EntityManager entityManager,
            JdbcTemplate jdbcTemplate) {
        this.inkService = inkService;
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, 'ink-service-reader@example.com', 'encoded-password',
                        '2026-07-28 00:00:00.000000')
                """,
                READER_ID);
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, 0)",
                READER_ID);
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, 'other-ink-service-reader@example.com', 'encoded-password',
                        '2026-07-28 00:00:00.000000')
                """,
                OTHER_READER_ID);
        jdbcTemplate.update(
                "INSERT INTO ink_account (reader_id, balance) VALUES (?, 0)",
                OTHER_READER_ID);
        도서와_페이지를_생성한다(
                FIRST_BOOK_ID, FIRST_PAGE_ID, "소설", "첫 번째 도서");
        도서와_페이지를_생성한다(
                SECOND_BOOK_ID, SECOND_PAGE_ID, "기술", "두 번째 도서");
        이용권_결제를_생성한다(PAID_PURCHASE_ID, "PAID");
        이용권_결제를_생성한다(PENDING_PURCHASE_ID, "PENDING");
        이용권_결제를_생성한다(SECOND_PAID_PURCHASE_ID, "PAID");
        페이지_대여를_생성한다(FIRST_RENTAL_ID, FIRST_PAGE_ID);
        페이지_대여를_생성한다(SECOND_RENTAL_ID, SECOND_PAGE_ID);
    }

    @Test
    void T_INK_001_서로_다른_카테고리와_도서도_페이지마다_1잉크를_차감한다() {
        inkService.grantInk(READER_ID, PAID_PURCHASE_ID);

        inkService.deductInk(READER_ID, FIRST_RENTAL_ID);
        inkService.deductInk(READER_ID, SECOND_RENTAL_ID);
        entityManager.flush();

        assertEquals(98, 잔액을_조회한다());
        assertEquals(
                2,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM ink_ledger
                        WHERE reader_id = ? AND type = 'DEDUCTION' AND amount = 1
                        """,
                        Integer.class,
                        READER_ID));
    }

    @Test
    void 서로_다른_구매는_각각_한_번씩_잉크를_지급한다() {
        inkService.grantInk(READER_ID, PAID_PURCHASE_ID);
        inkService.grantInk(READER_ID, SECOND_PAID_PURCHASE_ID);
        entityManager.flush();

        assertEquals(200, 잔액을_조회한다());
        assertEquals(
                2,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM ink_ledger
                        WHERE reader_id = ? AND type = 'GRANT'
                        """,
                        Integer.class,
                        READER_ID));
    }

    @Test
    void 이미_지급된_구매라도_다른_독자의_요청은_멱등_성공으로_처리하지_않는다() {
        inkService.grantInk(READER_ID, PAID_PURCHASE_ID);
        entityManager.flush();

        assertThrows(
                InkPurchaseNotFoundException.class,
                () -> inkService.grantInk(OTHER_READER_ID, PAID_PURCHASE_ID));

        assertEquals(100, 잔액을_조회한다());
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT balance FROM ink_account WHERE reader_id = ?",
                        Integer.class,
                        OTHER_READER_ID));
        assertEquals(1, 원장_수를_조회한다());
    }

    @Test
    void 같은_구매와_대여를_재처리해도_지급과_차감은_각각_한_번이고_원장_합계가_잔액과_일치한다() {
        inkService.grantInk(READER_ID, PAID_PURCHASE_ID);
        inkService.grantInk(READER_ID, PAID_PURCHASE_ID);
        inkService.deductInk(READER_ID, FIRST_RENTAL_ID);
        inkService.deductInk(READER_ID, FIRST_RENTAL_ID);
        entityManager.flush();

        int balance = 잔액을_조회한다();
        int signedLedgerSum = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(
                    CASE type
                        WHEN 'GRANT' THEN amount
                        WHEN 'DEDUCTION' THEN -amount
                    END
                ), 0)
                FROM ink_ledger
                WHERE reader_id = ?
                """,
                Integer.class,
                READER_ID);
        int latestBalanceAfter = jdbcTemplate.queryForObject(
                """
                SELECT balance_after
                FROM ink_ledger
                WHERE reader_id = ?
                ORDER BY occurred_at DESC, id DESC
                LIMIT 1
                """,
                Integer.class,
                READER_ID);

        assertEquals(99, balance);
        assertEquals(2, 원장_수를_조회한다());
        assertEquals(balance, signedLedgerSum);
        assertEquals(balance, latestBalanceAfter);
    }

    @Test
    void 다른_독자의_처리되지_않은_대여도_차감하지_않는다() {
        assertThrows(
                InvalidInkLedgerException.class,
                () -> inkService.deductInk(OTHER_READER_ID, FIRST_RENTAL_ID));

        assertEquals(0, 잔액을_조회한다());
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT balance FROM ink_account WHERE reader_id = ?",
                        Integer.class,
                        OTHER_READER_ID));
        assertEquals(0, 원장_수를_조회한다());
    }

    @Test
    void 존재하지_않는_대여는_차감하지_않는다() {
        assertThrows(
                InvalidInkLedgerException.class,
                () -> inkService.deductInk(READER_ID, Long.MAX_VALUE));

        assertEquals(0, 잔액을_조회한다());
        assertEquals(0, 원장_수를_조회한다());
    }

    @Test
    void 다른_독자의_이미_처리된_대여를_멱등_성공으로_처리하지_않는다() {
        inkService.grantInk(READER_ID, PAID_PURCHASE_ID);
        inkService.deductInk(READER_ID, FIRST_RENTAL_ID);
        entityManager.flush();

        assertThrows(
                InvalidInkLedgerException.class,
                () -> inkService.deductInk(OTHER_READER_ID, FIRST_RENTAL_ID));

        assertEquals(99, 잔액을_조회한다());
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT balance FROM ink_account WHERE reader_id = ?",
                        Integer.class,
                        OTHER_READER_ID));
        assertEquals(2, 원장_수를_조회한다());
    }

    @Test
    void 원장_발생_시각은_실제_잔액_반영_순서를_따른다() {
        inkService.grantInk(READER_ID, PAID_PURCHASE_ID);
        inkService.deductInk(READER_ID, FIRST_RENTAL_ID);
        entityManager.flush();

        assertEquals(
                99,
                jdbcTemplate.queryForObject(
                        """
                        SELECT balance_after
                        FROM ink_ledger
                        WHERE reader_id = ?
                        ORDER BY occurred_at DESC, id DESC
                        LIMIT 1
                        """,
                        Integer.class,
                        READER_ID));
    }

    @Test
    void 결제가_PAID가_아니면_잉크를_지급하지_않는다() {
        assertThrows(
                InkPurchaseStateConflictException.class,
                () -> inkService.grantInk(READER_ID, PENDING_PURCHASE_ID));
        entityManager.flush();

        assertEquals(0, 잔액을_조회한다());
        assertEquals(0, 원장_수를_조회한다());
    }

    @Test
    void 잔액이_0이면_원장과_잔액을_변경하지_않는다() {
        assertThrows(
                InsufficientInkException.class,
                () -> inkService.deductInk(READER_ID, FIRST_RENTAL_ID));
        entityManager.flush();

        assertEquals(0, 잔액을_조회한다());
        assertEquals(0, 원장_수를_조회한다());
    }

    @Test
    void INV_005_영속화한_원장은_메모리에서_값을_바꿔도_DB에서_수정되지_않는다()
            throws ReflectiveOperationException {
        inkService.grantInk(READER_ID, PAID_PURCHASE_ID);
        entityManager.flush();
        entityManager.clear();

        long ledgerId = jdbcTemplate.queryForObject(
                "SELECT id FROM ink_ledger WHERE ink_purchase_id = ?",
                Long.class,
                PAID_PURCHASE_ID);
        InkLedger ledger = entityManager.find(InkLedger.class, ledgerId);
        Field amountField = InkLedger.class.getDeclaredField("amount");
        amountField.setAccessible(true);
        amountField.setInt(ledger, 99);
        entityManager.flush();
        entityManager.clear();

        assertEquals(
                100,
                jdbcTemplate.queryForObject(
                        "SELECT amount FROM ink_ledger WHERE id = ?",
                        Integer.class,
                        ledgerId));
    }

    private void 도서와_페이지를_생성한다(
            long bookId,
            long pageId,
            String category,
            String title) {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, description, cover_image_path,
                     total_page_count, price_won)
                VALUES (?, ?, ?, '테스트 저자', NULL, NULL, 1, 10000)
                """,
                bookId,
                category,
                title);
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content, image_path)
                VALUES (?, ?, 1, 'TEXT', '테스트 페이지', NULL)
                """,
                pageId,
                bookId);
    }

    private void 이용권_결제를_생성한다(long purchaseId, String status) {
        String paidAt = "PAID".equals(status)
                ? "'2026-07-28 10:00:00.000000'"
                : "NULL";
        jdbcTemplate.update(
                """
                INSERT INTO ink_purchase
                    (id, reader_id, payment_id, status, amount_won, granted_ink,
                     created_at, paid_at)
                VALUES (?, ?, ?, ?, 1000, 100,
                        '2026-07-28 09:00:00.000000', %s)
                """.formatted(paidAt),
                purchaseId,
                READER_ID,
                UUID.nameUUIDFromBytes(("service-purchase-" + purchaseId).getBytes()).toString(),
                status);
    }

    private void 페이지_대여를_생성한다(long rentalId, long pageId) {
        jdbcTemplate.update(
                """
                INSERT INTO page_rental
                    (id, reader_id, book_page_id, rented_at, expires_at)
                VALUES (?, ?, ?, '2026-07-28 10:00:00.000000',
                        '2026-08-27 10:00:00.000000')
                """,
                rentalId,
                READER_ID,
                pageId);
    }

    private int 잔액을_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM ink_account WHERE reader_id = ?",
                Integer.class,
                READER_ID);
    }

    private int 원장_수를_조회한다() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ink_ledger WHERE reader_id = ?",
                Integer.class,
                READER_ID);
    }
}
