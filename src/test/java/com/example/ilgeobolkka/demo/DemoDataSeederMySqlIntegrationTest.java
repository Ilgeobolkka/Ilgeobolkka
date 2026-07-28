package com.example.ilgeobolkka.demo;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
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

    @Test
    void 비밀번호가_비어_있으면_시드를_거부한다() {
        assertThrows(IllegalArgumentException.class, () -> demoDataSeeder.seed(" "));
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
