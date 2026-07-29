package com.example.ilgeobolkka.support.entity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.ilgeobolkka.book.entity.Book;
import com.example.ilgeobolkka.book.entity.BookPage;
import com.example.ilgeobolkka.book.entity.BookPageContentType;
import com.example.ilgeobolkka.ink.entity.InkAccount;
import com.example.ilgeobolkka.ink.entity.InkLedger;
import com.example.ilgeobolkka.ink.entity.InkLedgerType;
import com.example.ilgeobolkka.ink.entity.InkPurchase;
import com.example.ilgeobolkka.ink.entity.InkPurchaseStatus;
import com.example.ilgeobolkka.library.entity.LibraryEntry;
import com.example.ilgeobolkka.ownership.entity.BookOwnership;
import com.example.ilgeobolkka.ownership.entity.OwnershipPayment;
import com.example.ilgeobolkka.ownership.entity.OwnershipPaymentStatus;
import com.example.ilgeobolkka.reader.entity.Reader;
import com.example.ilgeobolkka.reading.entity.ReadingSession;
import com.example.ilgeobolkka.rental.entity.PageRental;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
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
class CoreEntityMappingMySqlIntegrationTest {

    private static final long READER_ID = 21_000L;
    private static final long BOOK_ID = 22_000L;
    private static final long BOOK_PAGE_ID = 23_000L;
    private static final long READING_SESSION_ID = 24_000L;
    private static final long INK_ACCOUNT_ID = 25_000L;
    private static final long INK_PURCHASE_ID = 26_000L;
    private static final long PAGE_RENTAL_ID = 27_000L;
    private static final long GRANT_LEDGER_ID = 28_000L;
    private static final long DEDUCTION_LEDGER_ID = 28_001L;
    private static final long OWNERSHIP_PAYMENT_ID = 29_000L;
    private static final long BOOK_OWNERSHIP_ID = 30_000L;
    private static final long LIBRARY_ENTRY_ID = 31_000L;
    private static final UUID VIEWER_SESSION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000024000");
    private static final UUID INK_PAYMENT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000026000");
    private static final UUID OWNERSHIP_PROVIDER_PAYMENT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000029000");

    private final EntityManager entityManager;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    CoreEntityMappingMySqlIntegrationTest(EntityManager entityManager, JdbcTemplate jdbcTemplate) {
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void 현재_12개_테이블이_각각_JPA_엔티티로_등록된다() {
        List<String> entityNames =
                entityManager.getMetamodel().getEntities().stream()
                        .map(EntityType::getJavaType)
                        .filter(type -> type.getPackageName().startsWith("com.example.ilgeobolkka"))
                        .map(Class::getSimpleName)
                        .sorted()
                        .toList();

        assertEquals(
                List.of(
                        "Book",
                        "BookOwnership",
                        "BookPage",
                        "InkAccount",
                        "InkLedger",
                        "InkOperationClaim",
                        "InkPurchase",
                        "LibraryEntry",
                        "OwnershipPayment",
                        "PageRental",
                        "Reader",
                        "ReadingSession"),
                entityNames);
    }

    @Test
    void 모든_엔티티는_MySQL_AUTO_INCREMENT와_맞는_IDENTITY_전략을_사용한다() {
        entityManager
                .getMetamodel()
                .getEntities()
                .forEach(
                        entityType -> {
                            try {
                                GeneratedValue generatedValue =
                                        entityType
                                                .getJavaType()
                                                .getDeclaredField("id")
                                                .getAnnotation(GeneratedValue.class);

                                assertNotNull(generatedValue, entityType.getName());
                                assertEquals(
                                        GenerationType.IDENTITY,
                                        generatedValue.strategy(),
                                        entityType.getName());
                            } catch (NoSuchFieldException exception) {
                                throw new AssertionError(entityType.getName() + "에 id 필드가 없습니다.", exception);
                            }
                        });
    }

    @Test
    void 엔티티의_컬럼명과_NULL_허용은_V1_물리_스키마와_일치한다() {
        Set<String> mappedColumns = new TreeSet<>();
        Set<String> mappedNullableColumns = new TreeSet<>();

        entityManager
                .getMetamodel()
                .getEntities()
                .forEach(
                        entityType -> {
                            Class<?> javaType = entityType.getJavaType();
                            String tableName = javaType.getAnnotation(Table.class).name();

                            Arrays.stream(javaType.getDeclaredFields())
                                    .forEach(
                                            field ->
                                                    컬럼_매핑을_추가한다(
                                                            tableName,
                                                            field,
                                                            mappedColumns,
                                                            mappedNullableColumns));
                        });

        List<String> physicalColumns = 물리_컬럼을_조회한다(false);
        List<String> physicalNullableColumns = 물리_컬럼을_조회한다(true);

        assertAll(
                () -> assertEquals(new TreeSet<>(physicalColumns), mappedColumns),
                () ->
                        assertEquals(
                                new TreeSet<>(physicalNullableColumns), mappedNullableColumns));
    }

    @Test
    void V1_데이터를_엔티티로_조회하면_확정_값과_외래_키_연관관계가_복원된다() {
        기준_데이터를_생성한다();
        entityManager.clear();

        Reader reader = entityManager.find(Reader.class, READER_ID);
        Book book = entityManager.find(Book.class, BOOK_ID);
        BookPage bookPage = entityManager.find(BookPage.class, BOOK_PAGE_ID);
        ReadingSession readingSession =
                entityManager.find(ReadingSession.class, READING_SESSION_ID);
        InkAccount inkAccount = entityManager.find(InkAccount.class, INK_ACCOUNT_ID);
        InkPurchase inkPurchase = entityManager.find(InkPurchase.class, INK_PURCHASE_ID);
        PageRental pageRental = entityManager.find(PageRental.class, PAGE_RENTAL_ID);
        InkLedger grantLedger = entityManager.find(InkLedger.class, GRANT_LEDGER_ID);
        InkLedger deductionLedger = entityManager.find(InkLedger.class, DEDUCTION_LEDGER_ID);
        OwnershipPayment ownershipPayment =
                entityManager.find(OwnershipPayment.class, OWNERSHIP_PAYMENT_ID);
        BookOwnership bookOwnership =
                entityManager.find(BookOwnership.class, BOOK_OWNERSHIP_ID);
        LibraryEntry libraryEntry = entityManager.find(LibraryEntry.class, LIBRARY_ENTRY_ID);

        assertAll(
                () -> assertEquals("reader@example.com", reader.getEmail()),
                () -> assertEquals(10_000, book.getPriceWon()),
                () -> assertEquals(BookPageContentType.TEXT, bookPage.getContentType()),
                () -> assertEquals(BOOK_ID, bookPage.getBook().getId()),
                () -> assertEquals(VIEWER_SESSION_ID, readingSession.getViewerSessionId()),
                () -> assertEquals(READER_ID, readingSession.getReader().getId()),
                () -> assertEquals(BOOK_PAGE_ID, readingSession.getCurrentPage().getId()),
                () -> assertEquals(READER_ID, inkAccount.getReader().getId()),
                () -> assertEquals(InkPurchaseStatus.PAID, inkPurchase.getStatus()),
                () -> assertEquals(INK_PAYMENT_ID, inkPurchase.getPaymentId()),
                () -> assertEquals(READER_ID, inkPurchase.getReader().getId()),
                () -> assertEquals(READER_ID, pageRental.getReader().getId()),
                () -> assertEquals(BOOK_PAGE_ID, pageRental.getBookPage().getId()),
                () -> assertEquals(InkLedgerType.GRANT, grantLedger.getType()),
                () -> assertEquals(INK_PURCHASE_ID, grantLedger.getInkPurchase().getId()),
                () -> assertNull(grantLedger.getPageRental()),
                () -> assertEquals(InkLedgerType.DEDUCTION, deductionLedger.getType()),
                () -> assertNull(deductionLedger.getInkPurchase()),
                () -> assertEquals(PAGE_RENTAL_ID, deductionLedger.getPageRental().getId()),
                () -> assertEquals(OwnershipPaymentStatus.PAID, ownershipPayment.getStatus()),
                () -> assertEquals(OWNERSHIP_PROVIDER_PAYMENT_ID, ownershipPayment.getPaymentId()),
                () -> assertEquals(READER_ID, ownershipPayment.getReader().getId()),
                () -> assertEquals(BOOK_ID, ownershipPayment.getBook().getId()),
                () ->
                        assertEquals(
                                OWNERSHIP_PAYMENT_ID,
                                bookOwnership.getOwnershipPayment().getId()),
                () -> assertEquals(READER_ID, libraryEntry.getReader().getId()),
                () -> assertEquals(BOOK_PAGE_ID, libraryEntry.getLastPage().getId()),
                () ->
                        assertEquals(
                                Instant.parse("2026-07-28T00:00:00.123456Z"),
                                libraryEntry.getUpdatedAt()));
    }

    private void 기준_데이터를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, 'reader@example.com', 'encoded-password', '2026-07-28 00:00:00.123456')
                """,
                READER_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book
                    (id, category, title, author, description, cover_image_path,
                     total_page_count, price_won)
                VALUES (?, '소설', '테스트 도서', '테스트 저자', NULL, NULL, 1, 10000)
                """,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (id, book_id, page_number, content_type, text_content, image_path)
                VALUES (?, ?, 1, 'TEXT', '첫 페이지', NULL)
                """,
                BOOK_PAGE_ID,
                BOOK_ID);
        jdbcTemplate.update(
                """
                INSERT INTO reading_session
                    (id, reader_id, book_id, current_page_number, viewer_session_id, updated_at)
                VALUES (?, ?, ?, 1, ?, '2026-07-28 00:00:00.123456')
                """,
                READING_SESSION_ID,
                READER_ID,
                BOOK_ID,
                VIEWER_SESSION_ID.toString());
        jdbcTemplate.update(
                "INSERT INTO ink_account (id, reader_id, balance) VALUES (?, ?, 99)",
                INK_ACCOUNT_ID,
                READER_ID);
        jdbcTemplate.update(
                """
                INSERT INTO ink_purchase
                    (id, reader_id, payment_id, status, amount_won, granted_ink, created_at, paid_at)
                VALUES (?, ?, ?, 'PAID', 1000, 100,
                        '2026-07-28 00:00:00.123456', '2026-07-28 00:01:00.123456')
                """,
                INK_PURCHASE_ID,
                READER_ID,
                INK_PAYMENT_ID.toString());
        jdbcTemplate.update(
                """
                INSERT INTO page_rental
                    (id, reader_id, book_page_id, rented_at, expires_at)
                VALUES (?, ?, ?, '2026-07-28 00:02:00.123456', '2026-08-27 00:02:00.123456')
                """,
                PAGE_RENTAL_ID,
                READER_ID,
                BOOK_PAGE_ID);
        jdbcTemplate.update(
                """
                INSERT INTO ink_ledger
                    (id, reader_id, type, amount, balance_after,
                     ink_purchase_id, page_rental_id, occurred_at)
                VALUES (?, ?, 'GRANT', 100, 100, ?, NULL, '2026-07-28 00:01:00.123456')
                """,
                GRANT_LEDGER_ID,
                READER_ID,
                INK_PURCHASE_ID);
        jdbcTemplate.update(
                """
                INSERT INTO ink_ledger
                    (id, reader_id, type, amount, balance_after,
                     ink_purchase_id, page_rental_id, occurred_at)
                VALUES (?, ?, 'DEDUCTION', 1, 99, NULL, ?, '2026-07-28 00:02:00.123456')
                """,
                DEDUCTION_LEDGER_ID,
                READER_ID,
                PAGE_RENTAL_ID);
        jdbcTemplate.update(
                """
                INSERT INTO ownership_payment
                    (id, reader_id, book_id, payment_id, status,
                     amount_won, created_at, paid_at)
                VALUES (?, ?, ?, ?, 'PAID', 10000,
                        '2026-07-28 00:03:00.123456', '2026-07-28 00:04:00.123456')
                """,
                OWNERSHIP_PAYMENT_ID,
                READER_ID,
                BOOK_ID,
                OWNERSHIP_PROVIDER_PAYMENT_ID.toString());
        jdbcTemplate.update(
                """
                INSERT INTO book_ownership
                    (id, reader_id, book_id, ownership_payment_id, created_at)
                VALUES (?, ?, ?, ?, '2026-07-28 00:04:00.123456')
                """,
                BOOK_OWNERSHIP_ID,
                READER_ID,
                BOOK_ID,
                OWNERSHIP_PAYMENT_ID);
        jdbcTemplate.update(
                """
                INSERT INTO library_entry
                    (id, reader_id, book_id, last_page_number, updated_at)
                VALUES (?, ?, ?, 1, '2026-07-28 00:00:00.123456')
                """,
                LIBRARY_ENTRY_ID,
                READER_ID,
                BOOK_ID);
    }

    private void 컬럼_매핑을_추가한다(
            String tableName,
            Field field,
            Set<String> mappedColumns,
            Set<String> mappedNullableColumns) {
        if (field.isAnnotationPresent(Id.class)) {
            mappedColumns.add(tableName + ".id");
            return;
        }

        Column column = field.getAnnotation(Column.class);
        if (column == null) {
            return;
        }

        String qualifiedColumnName = tableName + "." + column.name();
        mappedColumns.add(qualifiedColumnName);
        if (column.nullable()) {
            mappedNullableColumns.add(qualifiedColumnName);
        }
    }

    private List<String> 물리_컬럼을_조회한다(boolean nullableOnly) {
        String nullableCondition = nullableOnly ? "AND is_nullable = 'YES'" : "";

        return jdbcTemplate.queryForList(
                """
                SELECT CONCAT(table_name, '.', column_name)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name IN (
                      'reader', 'book', 'book_page', 'reading_session',
                      'ink_account', 'ink_purchase', 'page_rental', 'ink_ledger',
                      'ink_operation_claim', 'ownership_payment',
                      'book_ownership', 'library_entry'
                  )
                %s
                ORDER BY table_name, ordinal_position
                """
                        .formatted(nullableCondition),
                String.class);
    }
}
