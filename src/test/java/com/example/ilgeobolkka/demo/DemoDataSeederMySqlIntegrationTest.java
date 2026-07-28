package com.example.ilgeobolkka.demo;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Transactional
class DemoDataSeederMySqlIntegrationTest {

    private static final String DEMO_PASSWORD = "Demo-password1!";
    private static final String INK_PAYMENT_ID = "00000000-0000-0000-0000-000000000101";
    private static final String ADDITIONAL_INK_PAYMENT_ID =
            "00000000-0000-0000-0000-000000000102";
    private static final String OWNERSHIP_PAYMENT_ID =
            "00000000-0000-0000-0000-000000000201";

    private final DemoDataSeeder demoDataSeeder;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    DemoDataSeederMySqlIntegrationTest(
            DemoDataSeeder demoDataSeeder,
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder) {
        this.demoDataSeeder = demoDataSeeder;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Test
    void 시드하면_100권과_상태별_계정_3개가_생성된다() {
        demoDataSeeder.seed(DEMO_PASSWORD);

        assertAll(
                this::도서_시드가_계약과_일치한다,
                this::페이지_시드가_계약과_일치한다,
                this::검색_경계_시드가_존재한다,
                this::대여_검증_계정이_100잉크와_지급_원장을_가진다,
                this::잉크_부족_검증_계정이_0잉크와_빈_원장을_가진다,
                this::소장_검증_계정이_0잉크와_소장_도서를_가진다,
                this::세_계정의_비밀번호는_해시로_저장된다);
    }

    @Test
    void 시드를_다시_실행해도_행이_중복되지_않는다() {
        demoDataSeeder.seed(DEMO_PASSWORD);
        Map<String, Integer> firstCounts = 주요_시드_행_개수();
        List<String> firstPasswordHashes = 시연_계정_비밀번호_해시();

        demoDataSeeder.seed(DEMO_PASSWORD);

        assertAll(
                () -> assertEquals(firstCounts, 주요_시드_행_개수()),
                () -> assertEquals(firstPasswordHashes, 시연_계정_비밀번호_해시()));
    }

    @Test
    void 시드_시각은_MySQL_DATETIME에_UTC_원시값으로_저장된다() {
        demoDataSeeder.seed(DEMO_PASSWORD);

        List<String> rawTimes =
                jdbcTemplate.queryForList(
                        """
                        SELECT DATE_FORMAT(r.created_at, '%Y-%m-%d %H:%i:%s.%f')
                        FROM reader r
                        WHERE r.email IN (?, ?, ?)
                        UNION ALL
                        SELECT DATE_FORMAT(ip.created_at, '%Y-%m-%d %H:%i:%s.%f')
                        FROM ink_purchase ip
                        JOIN reader r ON r.id = ip.reader_id
                        WHERE r.email = ?
                        UNION ALL
                        SELECT DATE_FORMAT(ip.paid_at, '%Y-%m-%d %H:%i:%s.%f')
                        FROM ink_purchase ip
                        JOIN reader r ON r.id = ip.reader_id
                        WHERE r.email = ?
                        UNION ALL
                        SELECT DATE_FORMAT(il.occurred_at, '%Y-%m-%d %H:%i:%s.%f')
                        FROM ink_ledger il
                        JOIN reader r ON r.id = il.reader_id
                        WHERE r.email = ?
                        UNION ALL
                        SELECT DATE_FORMAT(op.created_at, '%Y-%m-%d %H:%i:%s.%f')
                        FROM ownership_payment op
                        JOIN reader r ON r.id = op.reader_id
                        WHERE r.email = ?
                        UNION ALL
                        SELECT DATE_FORMAT(op.paid_at, '%Y-%m-%d %H:%i:%s.%f')
                        FROM ownership_payment op
                        JOIN reader r ON r.id = op.reader_id
                        WHERE r.email = ?
                        UNION ALL
                        SELECT DATE_FORMAT(bo.created_at, '%Y-%m-%d %H:%i:%s.%f')
                        FROM book_ownership bo
                        JOIN reader r ON r.id = bo.reader_id
                        WHERE r.email = ?
                        UNION ALL
                        SELECT DATE_FORMAT(le.updated_at, '%Y-%m-%d %H:%i:%s.%f')
                        FROM library_entry le
                        JOIN reader r ON r.id = le.reader_id
                        WHERE r.email = ?
                        """,
                        String.class,
                        DemoDataSeeder.RENTAL_READER_EMAIL,
                        DemoDataSeeder.EMPTY_READER_EMAIL,
                        DemoDataSeeder.OWNERSHIP_READER_EMAIL,
                        DemoDataSeeder.RENTAL_READER_EMAIL,
                        DemoDataSeeder.RENTAL_READER_EMAIL,
                        DemoDataSeeder.RENTAL_READER_EMAIL,
                        DemoDataSeeder.OWNERSHIP_READER_EMAIL,
                        DemoDataSeeder.OWNERSHIP_READER_EMAIL,
                        DemoDataSeeder.OWNERSHIP_READER_EMAIL,
                        DemoDataSeeder.OWNERSHIP_READER_EMAIL);

        assertAll(
                () -> assertEquals(10, rawTimes.size()),
                () ->
                        assertTrue(
                                rawTimes.stream()
                                        .allMatch(
                                                "2026-07-28 00:00:00.000000"
                                                        ::equals)));
    }

    @Test
    void 주입한_비밀번호가_바뀌면_기존_시연_계정의_해시만_갱신한다() {
        demoDataSeeder.seed(DEMO_PASSWORD);
        Map<String, Integer> firstCounts = 주요_시드_행_개수();
        String changedPassword = "Changed-demo2@";

        demoDataSeeder.seed(changedPassword);

        assertAll(
                () -> assertEquals(firstCounts, 주요_시드_행_개수()),
                () ->
                        assertTrue(
                                시연_계정_비밀번호_해시().stream()
                                        .allMatch(
                                                hash ->
                                                        passwordEncoder.matches(
                                                                changedPassword, hash))));
    }

    @ParameterizedTest
    @MethodSource("제품_정책에_맞지_않는_비밀번호")
    void 제품_정책에_맞지_않는_비밀번호는_DB_작업_전에_거부한다(String rawPassword) {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, total_page_count, price_won)
                VALUES (1, '충돌', '기존 도서', '기존 작가', 1, 1000)
                """);

        assertThrows(IllegalArgumentException.class, () -> demoDataSeeder.seed(rawPassword));
    }

    @Test
    void 제품_정책의_비밀번호_경계값을_허용한다() {
        String minimumLengthPassword = "Aa1!aaaa";
        String maximumBytePassword = "Aa1!" + "가".repeat(20);

        assertAll(
                () -> assertEquals(8, minimumLengthPassword.codePointCount(0, 8)),
                () ->
                        assertEquals(
                                64,
                                maximumBytePassword.getBytes(StandardCharsets.UTF_8).length));

        demoDataSeeder.seed(minimumLengthPassword);
        demoDataSeeder.seed(maximumBytePassword);

        assertTrue(
                시연_계정_비밀번호_해시().stream()
                        .allMatch(hash -> passwordEncoder.matches(maximumBytePassword, hash)));
    }

    @Test
    void 기존_잉크_지급_결제가_PAID가_아니면_재시드를_거부한다() {
        demoDataSeeder.seed(DEMO_PASSWORD);
        jdbcTemplate.update(
                """
                UPDATE ink_purchase
                SET status = 'FAILED', paid_at = NULL
                WHERE payment_id = ?
                """,
                INK_PAYMENT_ID);

        assertThrows(IllegalStateException.class, () -> demoDataSeeder.seed(DEMO_PASSWORD));
    }

    @Test
    void 기존_잉크_지급이_Reader_A가_아니면_재시드를_거부한다() {
        demoDataSeeder.seed(DEMO_PASSWORD);
        잉크_지급을_다른_독자에게_옮긴다();

        assertThrows(IllegalStateException.class, () -> demoDataSeeder.seed(DEMO_PASSWORD));
    }

    @Test
    void 기존_잉크_잔액과_원장_합계가_다르면_재시드를_거부한다() {
        demoDataSeeder.seed(DEMO_PASSWORD);
        long emptyReaderId = 독자_ID(DemoDataSeeder.EMPTY_READER_EMAIL);
        jdbcTemplate.update(
                "UPDATE ink_account SET balance = 1 WHERE reader_id = ?", emptyReaderId);

        assertThrows(IllegalStateException.class, () -> demoDataSeeder.seed(DEMO_PASSWORD));
    }

    @Test
    void 정상적인_추가_잉크_구매와_원장은_재시드해도_초기화하지_않는다() {
        demoDataSeeder.seed(DEMO_PASSWORD);
        long emptyReaderId = 독자_ID(DemoDataSeeder.EMPTY_READER_EMAIL);
        jdbcTemplate.update(
                """
                INSERT INTO ink_purchase
                    (reader_id, payment_id, status, amount_won, granted_ink, created_at, paid_at)
                VALUES (?, ?, 'PAID', 1000, 100, '2026-07-28 01:00:00', '2026-07-28 01:00:00')
                """,
                emptyReaderId,
                ADDITIONAL_INK_PAYMENT_ID);
        long additionalPurchaseId =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM ink_purchase WHERE payment_id = ?",
                        Long.class,
                        ADDITIONAL_INK_PAYMENT_ID);
        jdbcTemplate.update(
                """
                INSERT INTO ink_ledger
                    (reader_id, type, amount, balance_after, ink_purchase_id, occurred_at)
                VALUES (?, 'GRANT', 100, 100, ?, '2026-07-28 01:00:00')
                """,
                emptyReaderId,
                additionalPurchaseId);
        jdbcTemplate.update(
                "UPDATE ink_account SET balance = 100 WHERE reader_id = ?", emptyReaderId);

        demoDataSeeder.seed(DEMO_PASSWORD);

        Integer balance =
                jdbcTemplate.queryForObject(
                        "SELECT balance FROM ink_account WHERE reader_id = ?",
                        Integer.class,
                        emptyReaderId);
        Integer additionalGrantCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM ink_ledger
                        WHERE reader_id = ? AND ink_purchase_id = ?
                        """,
                        Integer.class,
                        emptyReaderId,
                        additionalPurchaseId);
        assertAll(
                () -> assertEquals(100, balance),
                () -> assertEquals(1, additionalGrantCount));
    }

    @Test
    void 기존_소장_결제가_PAID가_아니면_재시드를_거부한다() {
        demoDataSeeder.seed(DEMO_PASSWORD);
        jdbcTemplate.update(
                """
                UPDATE ownership_payment
                SET status = 'FAILED', paid_at = NULL
                WHERE payment_id = ?
                """,
                OWNERSHIP_PAYMENT_ID);

        assertThrows(IllegalStateException.class, () -> demoDataSeeder.seed(DEMO_PASSWORD));
    }

    @Test
    void 기존_소장과_서재의_독자가_다르면_재시드를_거부한다() {
        demoDataSeeder.seed(DEMO_PASSWORD);
        소장을_Reader_A에게_옮긴다();

        assertThrows(IllegalStateException.class, () -> demoDataSeeder.seed(DEMO_PASSWORD));
    }

    @Test
    void 고정_도서_ID에_다른_데이터가_있으면_덮어쓰지_않는다() {
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, total_page_count, price_won)
                VALUES (1, '충돌', '기존 도서', '기존 작가', 1, 1000)
                """);

        assertThrows(IllegalStateException.class, () -> demoDataSeeder.seed(DEMO_PASSWORD));
    }

    private void 도서_시드가_계약과_일치한다() {
        Integer bookCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM book WHERE id BETWEEN 1 AND 100", Integer.class);
        Integer invalidPriceCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM book WHERE id BETWEEN 1 AND 100 AND price_won <= 0",
                        Integer.class);
        Integer missingCoverCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM book
                        WHERE id BETWEEN 1 AND 100
                          AND (cover_image_path IS NULL OR cover_image_path = '')
                        """,
                        Integer.class);
        List<String> coverPaths =
                jdbcTemplate.queryForList(
                        """
                        SELECT DISTINCT cover_image_path
                        FROM book
                        WHERE id BETWEEN 1 AND 100
                        ORDER BY cover_image_path
                        """,
                        String.class);

        assertAll(
                () -> assertEquals(100, bookCount),
                () -> assertEquals(0, invalidPriceCount),
                () -> assertEquals(0, missingCoverCount),
                () -> assertEquals(10, coverPaths.size()),
                () ->
                        assertTrue(
                                coverPaths.stream()
                                        .allMatch(
                                                path ->
                                                        new ClassPathResource(
                                                                        "static" + path)
                                                                .exists())));
    }

    private void 페이지_시드가_계약과_일치한다() {
        Integer mismatchedPageCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM book b
                        LEFT JOIN (
                            SELECT book_id, COUNT(*) AS page_count
                            FROM book_page
                            GROUP BY book_id
                        ) bp ON bp.book_id = b.id
                        WHERE b.id BETWEEN 1 AND 100
                          AND b.total_page_count <> COALESCE(bp.page_count, 0)
                        """,
                        Integer.class);
        List<String> contentTypes =
                jdbcTemplate.queryForList(
                        """
                        SELECT DISTINCT content_type
                        FROM book_page
                        WHERE book_id BETWEEN 1 AND 100
                        ORDER BY content_type
                        """,
                        String.class);
        List<String> imagePaths =
                jdbcTemplate.queryForList(
                        """
                        SELECT DISTINCT image_path
                        FROM book_page
                        WHERE book_id BETWEEN 1 AND 100 AND content_type = 'IMAGE'
                        ORDER BY image_path
                        """,
                        String.class);

        assertAll(
                () -> assertEquals(0, mismatchedPageCount),
                () -> assertEquals(List.of("IMAGE", "TEXT"), contentTypes),
                () -> assertEquals(10, imagePaths.size()),
                () ->
                        assertTrue(
                                imagePaths.stream()
                                        .allMatch(this::정규화된_이미지_리소스가_존재한다)));
    }

    private boolean 정규화된_이미지_리소스가_존재한다(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        try (var inputStream = resource.getInputStream()) {
            var image = ImageIO.read(inputStream);
            return image != null && image.getWidth() == 800 && image.getHeight() == 600;
        } catch (IOException exception) {
            return false;
        }
    }

    private void 검색_경계_시드가_존재한다() {
        Integer percentCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM book WHERE id BETWEEN 1 AND 100 AND title LIKE '%\\\\%%'",
                        Integer.class);
        Integer underscoreCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM book
                        WHERE id BETWEEN 1 AND 100
                          AND author LIKE '%\\\\_%'
                        """,
                        Integer.class);
        Integer doubleSpaceCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM book WHERE id BETWEEN 1 AND 100 AND title LIKE '%  %'",
                        Integer.class);
        Integer tiedTitleCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM book
                        WHERE category = '소설' AND title = '같은 제목의 경계'
                        """,
                        Integer.class);

        assertAll(
                () -> assertTrue(percentCount > 0),
                () -> assertTrue(underscoreCount > 0),
                () -> assertTrue(doubleSpaceCount > 0),
                () -> assertEquals(2, tiedTitleCount));
    }

    private void 대여_검증_계정이_100잉크와_지급_원장을_가진다() {
        long readerId = 독자_ID(DemoDataSeeder.RENTAL_READER_EMAIL);

        Integer balance =
                jdbcTemplate.queryForObject(
                        "SELECT balance FROM ink_account WHERE reader_id = ?",
                        Integer.class,
                        readerId);
        Integer grantCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM ink_ledger
                        WHERE reader_id = ?
                          AND type = 'GRANT'
                          AND amount = 100
                          AND balance_after = 100
                        """,
                        Integer.class,
                        readerId);

        assertAll(() -> assertEquals(100, balance), () -> assertEquals(1, grantCount));
    }

    private void 잉크_부족_검증_계정이_0잉크와_빈_원장을_가진다() {
        long readerId = 독자_ID(DemoDataSeeder.EMPTY_READER_EMAIL);

        Integer balance =
                jdbcTemplate.queryForObject(
                        "SELECT balance FROM ink_account WHERE reader_id = ?",
                        Integer.class,
                        readerId);
        Integer ledgerCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ink_ledger WHERE reader_id = ?",
                        Integer.class,
                        readerId);

        assertAll(() -> assertEquals(0, balance), () -> assertEquals(0, ledgerCount));
    }

    private void 소장_검증_계정이_0잉크와_소장_도서를_가진다() {
        long readerId = 독자_ID(DemoDataSeeder.OWNERSHIP_READER_EMAIL);

        Integer balance =
                jdbcTemplate.queryForObject(
                        "SELECT balance FROM ink_account WHERE reader_id = ?",
                        Integer.class,
                        readerId);
        Integer ownershipCount =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM book_ownership bo
                        JOIN ownership_payment op ON op.id = bo.ownership_payment_id
                        WHERE bo.reader_id = ?
                          AND bo.book_id = 1
                          AND op.status = 'PAID'
                        """,
                        Integer.class,
                        readerId);
        Integer lastPageNumber =
                jdbcTemplate.queryForObject(
                        """
                        SELECT last_page_number
                        FROM library_entry
                        WHERE reader_id = ? AND book_id = 1
                        """,
                        Integer.class,
                        readerId);

        assertAll(
                () -> assertEquals(0, balance),
                () -> assertEquals(1, ownershipCount),
                () -> assertEquals(1, lastPageNumber));
    }

    private void 세_계정의_비밀번호는_해시로_저장된다() {
        List<String> passwordHashes = 시연_계정_비밀번호_해시();

        assertEquals(3, passwordHashes.size());
        for (String passwordHash : passwordHashes) {
            assertNotEquals(DEMO_PASSWORD, passwordHash);
            assertTrue(passwordEncoder.matches(DEMO_PASSWORD, passwordHash));
        }
    }

    private List<String> 시연_계정_비밀번호_해시() {
        return jdbcTemplate.queryForList(
                """
                SELECT password_hash
                FROM reader
                WHERE email IN (?, ?, ?)
                ORDER BY email
                """,
                String.class,
                DemoDataSeeder.RENTAL_READER_EMAIL,
                DemoDataSeeder.EMPTY_READER_EMAIL,
                DemoDataSeeder.OWNERSHIP_READER_EMAIL);
    }

    private void 잉크_지급을_다른_독자에게_옮긴다() {
        long purchaseId =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM ink_purchase WHERE payment_id = ?",
                        Long.class,
                        INK_PAYMENT_ID);
        jdbcTemplate.update("DELETE FROM ink_ledger WHERE ink_purchase_id = ?", purchaseId);
        jdbcTemplate.update("DELETE FROM ink_purchase WHERE id = ?", purchaseId);

        long otherReaderId = 독자_ID(DemoDataSeeder.EMPTY_READER_EMAIL);
        jdbcTemplate.update(
                """
                INSERT INTO ink_purchase
                    (reader_id, payment_id, status, amount_won, granted_ink, created_at, paid_at)
                VALUES (?, ?, 'PAID', 1000, 100, '2026-07-28 00:00:00', '2026-07-28 00:00:00')
                """,
                otherReaderId,
                INK_PAYMENT_ID);
        long otherPurchaseId =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM ink_purchase WHERE payment_id = ?",
                        Long.class,
                        INK_PAYMENT_ID);
        jdbcTemplate.update(
                """
                INSERT INTO ink_ledger
                    (reader_id, type, amount, balance_after, ink_purchase_id, occurred_at)
                VALUES (?, 'GRANT', 100, 100, ?, '2026-07-28 00:00:00')
                """,
                otherReaderId,
                otherPurchaseId);
    }

    private void 소장을_Reader_A에게_옮긴다() {
        long paymentId =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM ownership_payment WHERE payment_id = ?",
                        Long.class,
                        OWNERSHIP_PAYMENT_ID);
        jdbcTemplate.update(
                "DELETE FROM book_ownership WHERE ownership_payment_id = ?", paymentId);
        jdbcTemplate.update("DELETE FROM ownership_payment WHERE id = ?", paymentId);

        long rentalReaderId = 독자_ID(DemoDataSeeder.RENTAL_READER_EMAIL);
        int priceWon =
                jdbcTemplate.queryForObject(
                        "SELECT price_won FROM book WHERE id = 1", Integer.class);
        jdbcTemplate.update(
                """
                INSERT INTO ownership_payment
                    (reader_id, book_id, payment_id, status, amount_won, created_at, paid_at)
                VALUES (?, 1, ?, 'PAID', ?, '2026-07-28 00:00:00', '2026-07-28 00:00:00')
                """,
                rentalReaderId,
                OWNERSHIP_PAYMENT_ID,
                priceWon);
        long otherPaymentId =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM ownership_payment WHERE payment_id = ?",
                        Long.class,
                        OWNERSHIP_PAYMENT_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_ownership
                    (reader_id, book_id, ownership_payment_id, created_at)
                VALUES (?, 1, ?, '2026-07-28 00:00:00')
                """,
                rentalReaderId,
                otherPaymentId);
    }

    private static Stream<String> 제품_정책에_맞지_않는_비밀번호() {
        return Stream.of(
                null,
                " ",
                "Aa1!aaa",
                "1234567!",
                "Abcdefg!",
                "Abcdefg1",
                "Aa1!" + "가".repeat(20) + "a");
    }

    private Map<String, Integer> 주요_시드_행_개수() {
        return Map.of(
                "book", 시드_도서_관련_행_개수("book", "id"),
                "book_page", 시드_도서_관련_행_개수("book_page", "book_id"),
                "reader",
                        jdbcTemplate.queryForObject(
                                """
                                SELECT COUNT(*)
                                FROM reader
                                WHERE email IN (?, ?, ?)
                                """,
                                Integer.class,
                                DemoDataSeeder.RENTAL_READER_EMAIL,
                                DemoDataSeeder.EMPTY_READER_EMAIL,
                                DemoDataSeeder.OWNERSHIP_READER_EMAIL),
                "ink_account",
                        jdbcTemplate.queryForObject(
                                """
                                SELECT COUNT(*)
                                FROM ink_account ia
                                JOIN reader r ON r.id = ia.reader_id
                                WHERE r.email IN (?, ?, ?)
                                """,
                                Integer.class,
                                DemoDataSeeder.RENTAL_READER_EMAIL,
                                DemoDataSeeder.EMPTY_READER_EMAIL,
                                DemoDataSeeder.OWNERSHIP_READER_EMAIL),
                "ink_ledger",
                        jdbcTemplate.queryForObject(
                                """
                                SELECT COUNT(*)
                                FROM ink_ledger il
                                JOIN reader r ON r.id = il.reader_id
                                WHERE r.email IN (?, ?, ?)
                                """,
                                Integer.class,
                                DemoDataSeeder.RENTAL_READER_EMAIL,
                                DemoDataSeeder.EMPTY_READER_EMAIL,
                                DemoDataSeeder.OWNERSHIP_READER_EMAIL),
                "book_ownership",
                        jdbcTemplate.queryForObject(
                                """
                                SELECT COUNT(*)
                                FROM book_ownership bo
                                JOIN reader r ON r.id = bo.reader_id
                                WHERE r.email IN (?, ?, ?)
                                """,
                                Integer.class,
                                DemoDataSeeder.RENTAL_READER_EMAIL,
                                DemoDataSeeder.EMPTY_READER_EMAIL,
                                DemoDataSeeder.OWNERSHIP_READER_EMAIL),
                "library_entry",
                        jdbcTemplate.queryForObject(
                                """
                                SELECT COUNT(*)
                                FROM library_entry le
                                JOIN reader r ON r.id = le.reader_id
                                WHERE r.email IN (?, ?, ?)
                                """,
                                Integer.class,
                                DemoDataSeeder.RENTAL_READER_EMAIL,
                                DemoDataSeeder.EMPTY_READER_EMAIL,
                                DemoDataSeeder.OWNERSHIP_READER_EMAIL));
    }

    private int 시드_도서_관련_행_개수(String tableName, String bookIdColumn) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE " + bookIdColumn + " BETWEEN 1 AND 100",
                Integer.class);
    }

    private long 독자_ID(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM reader WHERE email = ?", Long.class, email);
    }
}
