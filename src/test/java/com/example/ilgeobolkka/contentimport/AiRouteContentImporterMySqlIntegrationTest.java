package com.example.ilgeobolkka.contentimport;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.book.entity.BookPageContentType;
import com.example.ilgeobolkka.contentimport.embedding.EmbeddedAiRouteContent;
import com.example.ilgeobolkka.contentimport.validation.ValidatedAiRouteContent;
import com.example.ilgeobolkka.infra.openai.OpenAiEmbeddingGateway;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * `ai-route-v2` 적재 오케스트레이션.
 *
 * <p>여기서 확인하는 것은 순서와 트랜잭션 경계다. 본문 페이지와 AI 메타데이터가 한 트랜잭션에 들어가
 * 뒤쪽이 실패하면 앞쪽도 남지 않아야 한다.
 *
 * <p>Embeddings Gateway는 가짜로 바꾼다. 실제 호출은 키와 비용이 필요하고, 이 테스트가 보는 것은
 * 호출 결과가 아니라 적재 경계다.
 */
@SpringBootTest
@ActiveProfiles({"test", "content-import"})
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
class AiRouteContentImporterMySqlIntegrationTest {

    private static final String VERSION = "ai-route-v2";
    private static final String POLICY = "OPENAI_DEFAULT_RETENTION_V1";
    private static final String MODEL = "text-embedding-3-small";
    private static final int DIMENSIONS = 3;
    private static final long BOOK_ID = 63_000L;

    /**
     * `content-import` profile은 기동과 동시에 적재를 실행하는 러너를 켠다. Spring Boot는
     * `ApplicationRunner` 빈을 전부 실행하므로 `@Primary`로는 막히지 않고 정의 자체를 바꿔야 한다.
     */
    @MockitoBean private ContentImportRunner contentImportRunner;

    private final AiRouteContentImporter importer;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    AiRouteContentImporterMySqlIntegrationTest(
            AiRouteContentImporter importer, JdbcTemplate jdbcTemplate) {
        this.importer = importer;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 준비 단계가 Embeddings를 부르기 전에 멈추는지 보려면 호출 여부를 세어야 한다. */
    static final AtomicInteger 페이지_embedding_호출 = new AtomicInteger();

    @TestConfiguration
    static class FakeGatewayConfiguration {

        @Bean
        @Primary
        OpenAiEmbeddingGateway fakeEmbeddingGateway() {
            return new OpenAiEmbeddingGateway() {

                @Override
                public Embedding embedPurpose(PurposeInput input, String model, int dimensions) {
                    throw new AssertionError("적재는 목적 embedding을 호출하지 않습니다.");
                }

                @Override
                public Embedding embedPageAnalysis(
                        PageAnalysisInput input, String model, int dimensions) {
                    페이지_embedding_호출.incrementAndGet();
                    List<Double> vector = new ArrayList<>();
                    for (int index = 0; index < dimensions; index++) {
                        vector.add(0.1);
                    }
                    return new Embedding(vector, model, dimensions);
                }
            };
        }
    }

    @BeforeEach
    void 도서를_준비한다() {
        페이지_embedding_호출.set(0);
        테스트_도서를_지운다();
        jdbcTemplate.update(
                """
                INSERT INTO book (id, category, title, author, total_page_count, price_won)
                VALUES (?, '에세이', '적재 대상 도서', '저자', 1, 10000)
                """,
                BOOK_ID);
    }

    @AfterEach
    void 정리한다() {
        테스트_도서를_지운다();
    }

    @Test
    void 본문과_AI_메타데이터를_한_트랜잭션으로_쓴다() {
        importer.write(batch(2), prepared(2));

        assertAll(
                () -> assertEquals(2, 페이지_수()),
                () ->
                        assertEquals(
                                VERSION,
                                jdbcTemplate.queryForObject(
                                        "SELECT content_version FROM book WHERE id = ?",
                                        String.class,
                                        BOOK_ID)),
                () ->
                        assertEquals(
                                1,
                                jdbcTemplate.queryForObject(
                                        """
                                        SELECT COUNT(*) FROM book_page
                                        WHERE book_id = ? AND ai_route_candidate = 1
                                        """,
                                        Integer.class,
                                        BOOK_ID)));
    }

    @Test
    void AI_적재가_실패하면_본문도_남지_않는다() {
        // 본문은 두 페이지인데 AI 메타데이터는 세 페이지를 가리켜 뒤쪽에서 실패한다.
        assertThrows(RuntimeException.class, () -> importer.write(batch(2), prepared(3)));

        assertAll(
                () -> assertEquals(0, 페이지_수()),
                () ->
                        assertTrue(
                                jdbcTemplate.queryForObject(
                                        """
                                        SELECT content_version IS NULL OR content_version <> ?
                                        FROM book WHERE id = ?
                                        """,
                                        Boolean.class,
                                        VERSION,
                                        BOOK_ID)));
    }

    /**
     * 재적재는 지원 범위가 아니다. 막지 않으면 Embeddings를 다 부른 뒤 선수 관계 고유 제약에서 터진다.
     */
    @Test
    void 이미_적재된_DB에서는_embedding_전에_거부한다() {
        jdbcTemplate.update("UPDATE book SET content_version = ? WHERE id = ?", VERSION, BOOK_ID);

        AiRouteContentImportException exception =
                assertThrows(AiRouteContentImportException.class, importer::prepare);

        assertAll(
                () -> assertTrue(exception.getMessage().contains("이미 " + VERSION)),
                () -> assertEquals(0, 페이지_embedding_호출.get()));
    }

    private int 페이지_수() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM book_page WHERE book_id = ?", Integer.class, BOOK_ID);
    }

    private void 테스트_도서를_지운다() {
        jdbcTemplate.update("DELETE FROM ai_route_prerequisite WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id = ?", BOOK_ID);
        jdbcTemplate.update("DELETE FROM book WHERE id = ?", BOOK_ID);
    }

    private ContentBatch batch(int pageCount) {
        List<ConvertedPage> pages = new ArrayList<>();
        for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
            pages.add(
                    new ConvertedPage(
                            BOOK_ID,
                            pageNumber,
                            BookPageContentType.TEXT,
                            "p%d 본문".formatted(pageNumber),
                            null,
                            null));
        }
        return new ContentBatch(
                VERSION,
                "a".repeat(64),
                List.of(new ConvertedBook(BOOK_ID, "b".repeat(64), pageCount, pages)));
    }

    /** 페이지 1은 목차라 후보가 아니고 2번부터는 후보이며, 후보에는 모두 vector가 있다. */
    private AiRouteContentImporter.PreparedContent prepared(int metadataPages) {
        List<ValidatedAiRouteContent.ValidatedPage> pages = new ArrayList<>();
        Map<EmbeddedAiRouteContent.PageKey, List<Double>> vectors = new HashMap<>();
        for (int pageNumber = 1; pageNumber <= metadataPages; pageNumber++) {
            boolean candidate = pageNumber > 1;
            pages.add(
                    new ValidatedAiRouteContent.ValidatedPage(
                            pageNumber,
                            candidate,
                            "p%d 분석".formatted(pageNumber),
                            "p%d 주제".formatted(pageNumber),
                            60,
                            List.of()));
            if (candidate) {
                vectors.put(
                        new EmbeddedAiRouteContent.PageKey(BOOK_ID, pageNumber, VERSION),
                        List.of(0.1, 0.2, 0.3));
            }
        }
        ValidatedAiRouteContent validated =
                new ValidatedAiRouteContent(
                        VERSION,
                        POLICY,
                        MODEL,
                        DIMENSIONS,
                        List.of(
                                new ValidatedAiRouteContent.ValidatedBook(
                                        BOOK_ID, true, pages, List.of())));
        return new AiRouteContentImporter.PreparedContent(
                validated, new EmbeddedAiRouteContent(VERSION, MODEL, DIMENSIONS, vectors));
    }
}
