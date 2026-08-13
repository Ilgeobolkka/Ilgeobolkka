package com.example.ilgeobolkka.contentimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ilgeobolkka.contentimport.embedding.EmbeddedAiRouteContent;
import com.example.ilgeobolkka.contentimport.manifest.AiRouteContentManifest;
import com.example.ilgeobolkka.contentimport.manifest.InitialContentManifest;
import com.example.ilgeobolkka.contentimport.validation.ValidatedAiRouteContent;
import com.example.ilgeobolkka.demo.DemoBookCatalog;
import com.example.ilgeobolkka.demo.DemoBookWriter;
import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
class ContentImportAtomicityMySqlIntegrationTest {

    private static final String AI_VERSION = "ai-route-v2";
    private static final String AI_POLICY = "OPENAI_DEFAULT_RETENTION_V1";
    private static final String AI_MODEL = "text-embedding-3-small";
    private static final int AI_DIMENSIONS = 3;
    private static final long AI_BOOK_ID = 41L;

    @TempDir Path tempDirectory;

    private final ContentPageWriter pageWriter;
    private final DemoBookCatalog demoBookCatalog;
    private final DemoBookWriter demoBookWriter;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    ContentImportAtomicityMySqlIntegrationTest(
            ContentPageWriter pageWriter,
            DemoBookCatalog demoBookCatalog,
            DemoBookWriter demoBookWriter,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate) {
        this.pageWriter = pageWriter;
        this.demoBookCatalog = demoBookCatalog;
        this.demoBookWriter = demoBookWriter;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    @BeforeEach
    void 초기_도서를_준비한다() {
        테스트_콘텐츠를_지운다();
        demoBookWriter.ensureBooks(demoBookCatalog.books());
    }

    @AfterEach
    void 테스트_콘텐츠를_정리한다() {
        테스트_콘텐츠를_지운다();
    }

    @Test
    void 실제_service가_DB_트랜잭션을_commit한_뒤_변환_파일을_남긴다() throws IOException {
        Fixture fixture = fixture();
        ContentImportService service = service(fixture);

        ContentBatch batch = service.importContent();

        assertEquals(
                400,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM book_page WHERE book_id BETWEEN 1 AND 100",
                        Integer.class));
        assertTrue(
                Files.isRegularFile(
                        fixture.outputRoot()
                                .resolve(batch.manifestSha256())
                                .resolve("manifest.json")));
    }

    @Test
    void DB_사전검증이_실패하면_기존_DB와_페이지를_보존하고_파일을_게시하지_않는다()
            throws IOException {
        Fixture fixture = fixture();
        long unexpectedPage = demoBookCatalog.books().getFirst().totalPageCount() + 1L;
        jdbcTemplate.update(
                """
                INSERT INTO book_page
                    (book_id, page_number, content_type, text_content, image_path)
                VALUES (1, ?, 'TEXT', '보존할 본문', NULL)
                """,
                unexpectedPage);
        String manifestSha256 =
                ContentBatchConverter.sha256(Files.readAllBytes(fixture.manifestPath()));

        assertThrows(IllegalStateException.class, () -> service(fixture).importContent());

        assertEquals(
                "보존할 본문",
                jdbcTemplate.queryForObject(
                        "SELECT text_content FROM book_page WHERE book_id = 1 AND page_number = ?",
                        String.class,
                        unexpectedPage));
        assertFalse(Files.exists(fixture.outputRoot().resolve(manifestSha256)));
        assertFalse(
                Files.list(fixture.outputRoot())
                        .anyMatch(path -> path.getFileName().toString().contains(".staging-")));
    }

    @Test
    void 같은_AI_manifest를_service로_재적재하면_기존_배치_디렉터리와_페이지_ID를_재사용한다()
            throws IOException {
        Fixture fixture = aiFixture();
        AiRouteContentImportPreparer preparer = mock(AiRouteContentImportPreparer.class);
        ValidatedAiRouteContent content = aiContent();
        EmbeddedAiRouteContent embedded = aiEmbedding();
        when(preparer.validate(any(AiRouteContentManifest.class))).thenReturn(content);
        when(preparer.embed(content)).thenReturn(embedded);
        ContentImportService service = service(fixture, preparer);

        ContentBatch first = service.importContent();
        Long firstPageId = pageId(AI_BOOK_ID, 1);
        Path finalDirectory = fixture.outputRoot().resolve(first.manifestSha256());
        Path reuseMarker = finalDirectory.resolve("reuse-marker");
        Files.writeString(reuseMarker, "기존 배치 디렉터리");

        ContentBatch second = service.importContent();

        assertEquals(first.manifestSha256(), second.manifestSha256());
        assertEquals(firstPageId, pageId(AI_BOOK_ID, 1));
        assertTrue(Files.isRegularFile(reuseMarker));
        verify(preparer, times(2)).validate(any(AiRouteContentManifest.class));
        verify(preparer, times(2)).embed(content);
    }

    private ContentImportService service(Fixture fixture) {
        return service(fixture, mock(AiRouteContentImportPreparer.class));
    }

    private ContentImportService service(
            Fixture fixture, AiRouteContentImportPreparer aiRoutePreparer) {
        ContentBatchConverter converter =
                new ContentBatchConverter(
                        fixture.manifestPath(),
                        fixture.outputRoot(),
                        objectMapper,
                        new TextOnlyPdfTool());
        return new ContentImportService(
                converter,
                pageWriter,
                aiRoutePreparer,
                transactionTemplate);
    }

    private Fixture fixture() throws IOException {
        Path fixtureRoot = tempDirectory.resolve("fixture");
        Path pdfRoot = fixtureRoot.resolve("pdfs");
        Files.createDirectories(pdfRoot);
        List<InitialContentManifest.Book> books = new ArrayList<>();
        for (DemoBookCatalog.BookSeed seed : demoBookCatalog.books()) {
            String relativePath = "pdfs/book-%03d.pdf".formatted(seed.id());
            byte[] pdf = "fake-pdf-%03d".formatted(seed.id()).getBytes();
            Files.write(fixtureRoot.resolve(relativePath), pdf);
            books.add(
                    new InitialContentManifest.Book(
                            seed.id(),
                            relativePath,
                            ContentBatchConverter.sha256(pdf),
                            seed.totalPageCount()));
        }
        Path manifestPath = fixtureRoot.resolve("manifest.json");
        objectMapper.writeValue(
                manifestPath.toFile(), new InitialContentManifest("initial-v1", books));
        return new Fixture(manifestPath, tempDirectory.resolve("output"));
    }

    private Fixture aiFixture() throws IOException {
        Path fixtureRoot = tempDirectory.resolve("ai-fixture");
        Path pdfPath = fixtureRoot.resolve("pdfs/book-041.pdf");
        Files.createDirectories(pdfPath.getParent());
        byte[] pdf = "fake-ai-pdf-041".getBytes();
        Files.write(pdfPath, pdf);
        AiRouteContentManifest.Page page =
                new AiRouteContentManifest.Page(
                        1,
                        "1장",
                        "1절",
                        List.of("원자성"),
                        List.of(),
                        AiRouteContentManifest.ContentRole.CORE,
                        true,
                        "AI 콘텐츠 원자적 적재 분석",
                        "c".repeat(64),
                        "원자적 적재",
                        60,
                        List.of(),
                        List.of());
        AiRouteContentManifest manifest =
                new AiRouteContentManifest(
                        AI_VERSION,
                        AI_POLICY,
                        AI_MODEL,
                        AI_DIMENSIONS,
                        List.of(
                                new AiRouteContentManifest.Book(
                                        AI_BOOK_ID,
                                        "새 AI 도서 제목",
                                        "pdfs/book-041.pdf",
                                        ContentBatchConverter.sha256(pdf),
                                        1,
                                        true,
                                        true,
                                        List.of(page))));
        Path manifestPath = fixtureRoot.resolve("manifest.json");
        objectMapper.writeValue(manifestPath.toFile(), manifest);
        return new Fixture(manifestPath, tempDirectory.resolve("ai-output"));
    }

    private ValidatedAiRouteContent aiContent() {
        return new ValidatedAiRouteContent(
                AI_VERSION,
                AI_POLICY,
                AI_MODEL,
                AI_DIMENSIONS,
                List.of(
                        new ValidatedAiRouteContent.ValidatedBook(
                                AI_BOOK_ID,
                                "새 AI 도서 제목",
                                1,
                                true,
                                true,
                                List.of(
                                        new ValidatedAiRouteContent.ValidatedPage(
                                                1,
                                                true,
                                                "AI 콘텐츠 원자적 적재 분석",
                                                "원자적 적재",
                                                60,
                                                List.of())),
                                List.of())));
    }

    private EmbeddedAiRouteContent aiEmbedding() {
        return new EmbeddedAiRouteContent(
                AI_VERSION,
                AI_MODEL,
                AI_DIMENSIONS,
                Map.of(
                        new EmbeddedAiRouteContent.PageKey(AI_BOOK_ID, 1, AI_VERSION),
                        List.of(0.1, 0.2, 0.3)));
    }

    private Long pageId(long bookId, int pageNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM book_page WHERE book_id = ? AND page_number = ?",
                Long.class,
                bookId,
                pageNumber);
    }

    private void 테스트_콘텐츠를_지운다() {
        jdbcTemplate.update("DELETE FROM ai_route_prerequisite WHERE book_id BETWEEN 1 AND 100");
        jdbcTemplate.update("DELETE FROM book_page WHERE book_id BETWEEN 1 AND 100");
        jdbcTemplate.update("DELETE FROM book WHERE id BETWEEN 1 AND 100");
    }

    private record Fixture(Path manifestPath, Path outputRoot) {}

    private static final class TextOnlyPdfTool implements PdfTool {

        @Override
        public String pdftotextVersion() {
            return "26.05.0";
        }

        @Override
        public String pdftoppmVersion() {
            return "26.05.0";
        }

        @Override
        public String extractText(Path pdfPath, int pageNumber) {
            return "%s 본문 %d".formatted(pdfPath.getFileName(), pageNumber);
        }

        @Override
        public boolean pageExists(Path pdfPath, int pageNumber) {
            return false;
        }

        @Override
        public void renderJpeg(Path pdfPath, int pageNumber, Path outputPrefix) {
            throw new AssertionError("TEXT 전용 fixture는 JPEG를 만들지 않습니다.");
        }
    }
}
