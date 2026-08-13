package com.example.ilgeobolkka.contentimport;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.contentimport.embedding.EmbeddedAiRouteContent;
import com.example.ilgeobolkka.contentimport.manifest.AiRouteContentManifest;
import com.example.ilgeobolkka.contentimport.validation.ValidatedAiRouteContent;
import com.example.ilgeobolkka.infra.openai.OpenAiEmbeddingGateway;
import com.example.ilgeobolkka.infra.openai.OpenAiProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class AiRouteContentImportPreparerTest {

    private static final String POLICY = "OPENAI_DEFAULT_RETENTION_V1";

    @TempDir Path tempDirectory;

    @Test
    void sibling_evaluation을_읽어_C02와_C03을_연결한다() throws IOException {
        Fixture fixture = fixture();
        RecordingGateway gateway = new RecordingGateway();
        AiRouteContentImportPreparer preparer =
                preparer(fixture.manifestPath(), gateway, new OpenAiProperties("project", "key", POLICY));

        ValidatedAiRouteContent validated = preparer.validate(fixture.manifest());
        EmbeddedAiRouteContent embedded = preparer.embed(validated);

        assertAll(
                () -> assertEquals("ai-route-v2", validated.contentVersion()),
                () -> assertEquals("소설", validated.books().getFirst().title()),
                () -> assertEquals(4, validated.books().getFirst().totalPageCount()),
                () -> assertTrue(validated.books().getFirst().pages().isEmpty()),
                () -> assertTrue(embedded.vectors().isEmpty()),
                () -> assertEquals(0, gateway.pageCalls));
    }

    @Test
    void OpenAI_필수_설정이나_evaluation이_없으면_embedding을_호출하지_않는다()
            throws IOException {
        Fixture fixture = fixture();
        RecordingGateway gateway = new RecordingGateway();
        AiRouteContentImportPreparer missingConfiguration =
                preparer(
                        fixture.manifestPath(),
                        gateway,
                        new OpenAiProperties("project", "", POLICY));

        assertThrows(
                IllegalStateException.class,
                () -> missingConfiguration.validate(fixture.manifest()));

        Files.delete(fixture.manifestPath().getParent().resolve("evaluation.json"));
        AiRouteContentImportPreparer missingEvaluation =
                preparer(
                        fixture.manifestPath(),
                        gateway,
                        new OpenAiProperties("project", "key", POLICY));
        assertThrows(
                IllegalStateException.class,
                () -> missingEvaluation.validate(fixture.manifest()));
        assertEquals(0, gateway.pageCalls);
    }

    private AiRouteContentImportPreparer preparer(
            Path manifestPath, RecordingGateway gateway, OpenAiProperties openAiProperties) {
        ContentImportProperties properties = new ContentImportProperties();
        properties.setManifest(manifestPath);
        return new AiRouteContentImportPreparer(
                properties, new ObjectMapper(), gateway, openAiProperties);
    }

    private Fixture fixture() throws IOException {
        Path manifestPath = tempDirectory.resolve("ai-route-v2/manifest.json");
        Path pdfPath = manifestPath.getParent().resolve("pdfs/book-001.pdf");
        Files.createDirectories(pdfPath.getParent());
        byte[] pdf = "fake-pdf".getBytes();
        Files.write(pdfPath, pdf);
        Files.writeString(
                manifestPath.getParent().resolve("evaluation.json"),
                """
                {"contentVersion":"ai-route-v2","cases":[]}
                """);
        AiRouteContentManifest manifest =
                new AiRouteContentManifest(
                        "ai-route-v2",
                        POLICY,
                        "text-embedding-3-small",
                        1536,
                        List.of(
                                new AiRouteContentManifest.Book(
                                        1,
                                        "소설",
                                        "pdfs/book-001.pdf",
                                        ContentBatchConverter.sha256(pdf),
                                        4,
                                        false,
                                        false,
                                        List.of())));
        return new Fixture(manifestPath, manifest);
    }

    private record Fixture(Path manifestPath, AiRouteContentManifest manifest) {}

    private static final class RecordingGateway implements OpenAiEmbeddingGateway {

        private int pageCalls;

        @Override
        public Embedding embedPurpose(PurposeInput input, String model, int dimensions) {
            throw new AssertionError("콘텐츠 적재는 목적 embedding을 호출하지 않습니다.");
        }

        @Override
        public Embedding embedPageAnalysis(PageAnalysisInput input, String model, int dimensions) {
            pageCalls++;
            throw new AssertionError("비후보 도서는 페이지 embedding을 호출하지 않습니다.");
        }
    }
}
