package com.example.ilgeobolkka.book;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
@Transactional
class BookCatalogApiMySqlIntegrationTest {

    private static final long BOOK_ID_BASE = 402_000L;
    private static final long READER_ID = 402_000L;
    private static final long OWNERSHIP_PAYMENT_ID = 402_000L;

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    BookCatalogApiMySqlIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void setUp() {
        도서_100권을_생성한다();
        독자를_생성한다();
    }

    @Test
    void T_CAT_001_첫_목록은_고정_정렬의_첫_10권과_페이지_정보를_반환한다() throws Exception {
        mockMvc.perform(get("/api/books").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books.length()").value(10))
                .andExpect(
                        jsonPath("$.books[*].bookId")
                                .value(
                                        contains(
                                                402001,
                                                402002,
                                                402003,
                                                402004,
                                                402005,
                                                402006,
                                                402007,
                                                402008,
                                                402009,
                                                402010)))
                .andExpect(jsonPath("$.books[0].category").value("A"))
                .andExpect(
                        jsonPath("$.books[0].coverImagePath")
                                .value("/assets/covers/demo/category-01.svg"))
                .andExpect(jsonPath("$.books[0].title").value("Book 001"))
                .andExpect(jsonPath("$.books[0].author").value("Shared Search Author"))
                .andExpect(jsonPath("$.books[0].bookPrice").value(9001))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(10))
                .andExpect(jsonPath("$.totalCount").value(100));
    }

    @Test
    void T_CAT_002_다음_목록은_카테고리_경계에서도_중복과_누락이_없다() throws Exception {
        mockMvc.perform(get("/api/books").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.books[*].bookId")
                                .value(
                                        contains(
                                                402011,
                                                402012,
                                                402013,
                                                402014,
                                                402015,
                                                402016,
                                                402017,
                                                402018,
                                                402019,
                                                402020)))
                .andExpect(jsonPath("$.books[4].category").value("A"))
                .andExpect(jsonPath("$.books[5].category").value("B"));
    }

    @Test
    void T_CAT_003_검색은_앞뒤_공백만_제거하고_내부_공백을_보존하며_영문_대소문자를_구분하지_않는다()
            throws Exception {
        mockMvc.perform(
                        get("/api/books")
                                .param("page", "1")
                                .param("keyword", "  sPaCe  bEtWeEn  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books.length()").value(1))
                .andExpect(jsonPath("$.books[0].bookId").value(402097))
                .andExpect(jsonPath("$.totalCount").value(1));

        mockMvc.perform(
                        get("/api/books")
                                .param("page", "1")
                                .param("keyword", "  wRITER  mIXED  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books.length()").value(1))
                .andExpect(jsonPath("$.books[0].bookId").value(402098))
                .andExpect(jsonPath("$.totalCount").value(1));

        mockMvc.perform(
                        get("/api/books")
                                .param("page", "1")
                                .param("keyword", "  sHaReD sEaRcH  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books.length()").value(4))
                .andExpect(
                        jsonPath("$.books[*].bookId")
                                .value(contains(402001, 402002, 402003, 402020)))
                .andExpect(
                        jsonPath("$.books[*].category")
                                .value(contains("A", "A", "A", "B")))
                .andExpect(
                        jsonPath("$.books[*].title")
                                .value(
                                        contains(
                                                "Book 001",
                                                "Book 001",
                                                "Book 003",
                                                "Book 020")))
                .andExpect(jsonPath("$.totalCount").value(4));

        mockMvc.perform(get("/api/books").param("page", "1").param("keyword", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books.length()").value(10))
                .andExpect(jsonPath("$.totalCount").value(100));

        mockMvc.perform(
                        get("/api/books")
                                .param("page", "1")
                                .param("keyword", "검색 결과가 없는 문장"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books.length()").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    void T_CAT_004_퍼센트와_밑줄은_SQL_와일드카드가_아닌_일반_문자로_검색한다() throws Exception {
        mockMvc.perform(get("/api/books").param("page", "1").param("keyword", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books.length()").value(1))
                .andExpect(jsonPath("$.books[0].bookId").value(402096));

        mockMvc.perform(get("/api/books").param("page", "1").param("keyword", "_"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books.length()").value(1))
                .andExpect(jsonPath("$.books[0].bookId").value(402099));
    }

    @Test
    void T_CAT_005_목록과_상세는_계약_필드와_로그인_상태별_소장_여부를_반환한다() throws Exception {
        mockMvc.perform(get("/api/books").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books[0].bookId").value(402001))
                .andExpect(jsonPath("$.books[0].category").value("A"))
                .andExpect(
                        jsonPath("$.books[0].coverImagePath")
                                .value("/assets/covers/demo/category-01.svg"))
                .andExpect(jsonPath("$.books[0].title").value("Book 001"))
                .andExpect(jsonPath("$.books[0].author").value("Shared Search Author"))
                .andExpect(jsonPath("$.books[0].bookPrice").value(9001));

        mockMvc.perform(get("/api/books/{bookId}", BOOK_ID_BASE + 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId").value(402001))
                .andExpect(jsonPath("$.category").value("A"))
                .andExpect(
                        jsonPath("$.coverImagePath")
                                .value("/assets/covers/demo/category-01.svg"))
                .andExpect(jsonPath("$.title").value("Book 001"))
                .andExpect(jsonPath("$.author").value("Shared Search Author"))
                .andExpect(jsonPath("$.description").value("Description 001"))
                .andExpect(jsonPath("$.totalPageCount").value(4))
                .andExpect(jsonPath("$.bookPrice").value(9001))
                .andExpect(jsonPath("$.owned").value(nullValue()));

        mockMvc.perform(
                        get("/api/books/{bookId}", BOOK_ID_BASE + 1)
                                .with(authentication(인증된_독자())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owned").value(false));

        소장_기록을_생성한다(BOOK_ID_BASE + 1, 9001);

        mockMvc.perform(
                        get("/api/books/{bookId}", BOOK_ID_BASE + 1)
                                .with(authentication(인증된_독자())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owned").value(true));
    }

    @Test
    void T_CAT_006_잘못된_페이지는_400이고_범위_초과_양수는_빈_목록이다() throws Exception {
        mockMvc.perform(get("/api/books"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/books").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/books").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/books").param("page", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(get("/api/books").param("page", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.books.length()").value(0))
                .andExpect(jsonPath("$.page").value(11))
                .andExpect(jsonPath("$.totalPages").value(10))
                .andExpect(jsonPath("$.totalCount").value(100));
    }

    @Test
    void 존재하지_않는_도서_상세는_404_RESOURCE_NOT_FOUND를_반환한다() throws Exception {
        mockMvc.perform(get("/api/books/{bookId}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."));
    }

    @Test
    void 유효하지_않은_도서_ID는_400_INVALID_INPUT을_반환한다() throws Exception {
        mockMvc.perform(get("/api/books/{bookId}", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/books/{bookId}", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    private void 도서_100권을_생성한다() {
        List<Integer> bookNumbers = IntStream.rangeClosed(1, 100).boxed().toList();

        jdbcTemplate.batchUpdate(
                """
                INSERT INTO book
                    (id, category, title, author, description, cover_image_path,
                     total_page_count, price_won)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                bookNumbers,
                100,
                (statement, bookNumber) -> {
                    statement.setLong(1, BOOK_ID_BASE + bookNumber);
                    statement.setString(2, 카테고리(bookNumber));
                    statement.setString(3, 제목(bookNumber));
                    statement.setString(4, 저자(bookNumber));
                    statement.setString(5, "Description %03d".formatted(bookNumber));
                    statement.setString(6, "/assets/covers/demo/category-01.svg");
                    statement.setInt(7, 3 + bookNumber % 3);
                    statement.setInt(8, 9000 + bookNumber);
                });
    }

    private String 카테고리(int bookNumber) {
        if (bookNumber <= 15) {
            return "A";
        }
        if (bookNumber <= 95) {
            return "B";
        }
        return "Z";
    }

    private String 제목(int bookNumber) {
        if (bookNumber <= 2) {
            return "Book 001";
        }
        if (bookNumber == 96) {
            return "Aether% Archive";
        }
        if (bookNumber == 97) {
            return "Space  Between Words";
        }
        return "Book %03d".formatted(bookNumber);
    }

    private String 저자(int bookNumber) {
        if (bookNumber <= 3 || bookNumber == 20) {
            return "Shared Search Author";
        }
        if (bookNumber == 98) {
            return "Writer  Mixed Case";
        }
        if (bookNumber == 99) {
            return "writer_under_score";
        }
        return "Author %03d".formatted(bookNumber);
    }

    private void 독자를_생성한다() {
        jdbcTemplate.update(
                """
                INSERT INTO reader (id, email, password_hash, created_at)
                VALUES (?, ?, ?, ?)
                """,
                READER_ID,
                "scrum-402-reader@example.com",
                "test-password-hash",
                "2026-07-28 00:00:00.000000");
    }

    private void 소장_기록을_생성한다(long bookId, int amountWon) {
        jdbcTemplate.update(
                """
                INSERT INTO ownership_payment
                    (id, reader_id, book_id, payment_id, status, amount_won, created_at, paid_at)
                VALUES (?, ?, ?, ?, 'PAID', ?, ?, ?)
                """,
                OWNERSHIP_PAYMENT_ID,
                READER_ID,
                bookId,
                UUID.fromString("00000000-0000-0000-0000-000000402000").toString(),
                amountWon,
                "2026-07-28 00:00:00.000000",
                "2026-07-28 00:00:00.000000");
        jdbcTemplate.update(
                """
                INSERT INTO book_ownership
                    (id, reader_id, book_id, ownership_payment_id, created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                402_000L,
                READER_ID,
                bookId,
                OWNERSHIP_PAYMENT_ID,
                "2026-07-28 00:00:00.000000");
    }

    private TestingAuthenticationToken 인증된_독자() {
        return new TestingAuthenticationToken(
                new AuthenticatedReader(READER_ID), null, "ROLE_USER");
    }
}
