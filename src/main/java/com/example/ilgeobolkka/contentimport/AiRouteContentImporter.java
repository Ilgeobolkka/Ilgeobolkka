package com.example.ilgeobolkka.contentimport;

import com.example.ilgeobolkka.contentimport.embedding.AiRouteContentEmbeddingService;
import com.example.ilgeobolkka.contentimport.embedding.EmbeddedAiRouteContent;
import com.example.ilgeobolkka.contentimport.manifest.AiRouteContentManifest;
import com.example.ilgeobolkka.contentimport.manifest.AiRouteEvaluationDataset;
import com.example.ilgeobolkka.contentimport.manifest.ContentManifest;
import com.example.ilgeobolkka.contentimport.manifest.ContentManifestParser;
import com.example.ilgeobolkka.contentimport.validation.AiRouteContentValidator;
import com.example.ilgeobolkka.contentimport.validation.ValidatedAiRouteContent;
import com.example.ilgeobolkka.infra.openai.OpenAiProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 변환이 끝난 `ai-route-v2` 콘텐츠를 검증하고 embedding한 뒤 DB에 반영한다.
 *
 * <p>순서가 계약이다. 파일 I/O와 Embeddings 호출은 트랜잭션 **밖**에서 끝내고, 본문 페이지와 AI
 * 메타데이터는 **한 트랜잭션**에 함께 쓴다. 그래야 커넥션을 외부 호출 동안 붙잡지 않고, 중간에
 * 실패해도 본문만 들어간 어중간한 상태가 남지 않는다.
 */
@Component
@Profile("content-import")
class AiRouteContentImporter {

    private final ContentImportProperties properties;
    private final ContentManifestParser manifestParser;
    private final AiRouteContentValidator validator;
    private final AiRouteContentEmbeddingService embeddingService;
    private final ContentPageWriter pageWriter;
    private final AiRouteContentWriter aiRouteContentWriter;
    private final OpenAiProperties openAiProperties;

    AiRouteContentImporter(
            ContentImportProperties properties,
            ContentManifestParser manifestParser,
            AiRouteContentValidator validator,
            AiRouteContentEmbeddingService embeddingService,
            ContentPageWriter pageWriter,
            AiRouteContentWriter aiRouteContentWriter,
            OpenAiProperties openAiProperties) {
        this.properties = properties;
        this.manifestParser = manifestParser;
        this.validator = validator;
        this.embeddingService = embeddingService;
        this.pageWriter = pageWriter;
        this.aiRouteContentWriter = aiRouteContentWriter;
        this.openAiProperties = openAiProperties;
    }

    /**
     * 검증과 embedding까지 끝낸다. 파일 I/O와 외부 호출이 여기서 모두 일어나며 DB는 건드리지 않는다.
     */
    PreparedContent prepare() {
        String dataPolicyVersion = openAiProperties.dataPolicyVersion();
        AiRouteContentManifest manifest = readManifest();
        AiRouteEvaluationDataset evaluation = readEvaluation();

        ValidatedAiRouteContent validated =
                validator.validate(manifest, evaluation, fixtureRoot(), dataPolicyVersion);
        EmbeddedAiRouteContent embedded = embeddingService.embed(validated, dataPolicyVersion);
        return new PreparedContent(validated, embedded);
    }

    /**
     * 본문 페이지와 AI 메타데이터를 한 트랜잭션으로 쓴다. 실패하면 둘 다 없던 일이 된다.
     *
     * <p>같은 클래스 안에서 부르면 프록시를 거치지 않아 트랜잭션이 걸리지 않는다. 준비 단계와 나눠
     * 둔 이유이며, 호출은 {@link ContentImportService}가 한다.
     */
    @Transactional
    void write(ContentBatch batch, PreparedContent prepared) {
        pageWriter.write(batch);
        aiRouteContentWriter.write(prepared.validated(), prepared.embedded());
    }

    record PreparedContent(
            ValidatedAiRouteContent validated, EmbeddedAiRouteContent embedded) {}

    private AiRouteContentManifest readManifest() {
        ContentManifest manifest = manifestParser.parseManifest(readString(properties.manifest()));
        if (manifest instanceof AiRouteContentManifest aiRouteManifest) {
            return aiRouteManifest;
        }
        throw new AiRouteContentImportException(
                "AI 경로 적재에 " + manifest.contentVersion() + " manifest를 쓸 수 없습니다.");
    }

    private AiRouteEvaluationDataset readEvaluation() {
        return manifestParser.parseEvaluation(readString(properties.evaluation()));
    }

    /** PDF 경로는 manifest 파일 기준 상대 경로다. */
    private Path fixtureRoot() {
        Path parent = properties.manifest().toAbsolutePath().normalize().getParent();
        if (parent == null) {
            throw new AiRouteContentImportException("manifest 상위 디렉터리를 확인할 수 없습니다.");
        }
        return parent;
    }

    private String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new AiRouteContentImportException("파일을 읽지 못했습니다: " + path);
        }
    }
}
