package com.example.ilgeobolkka.contentimport;

import com.example.ilgeobolkka.contentimport.embedding.AiRouteContentEmbeddingService;
import com.example.ilgeobolkka.contentimport.embedding.EmbeddedAiRouteContent;
import com.example.ilgeobolkka.contentimport.manifest.AiRouteContentManifest;
import com.example.ilgeobolkka.contentimport.manifest.AiRouteEvaluationDataset;
import com.example.ilgeobolkka.contentimport.manifest.ContentManifestParser;
import com.example.ilgeobolkka.contentimport.validation.AiRouteContentValidator;
import com.example.ilgeobolkka.contentimport.validation.PrerequisiteGraphValidator;
import com.example.ilgeobolkka.contentimport.validation.ValidatedAiRouteContent;
import com.example.ilgeobolkka.infra.openai.OpenAiEmbeddingGateway;
import com.example.ilgeobolkka.infra.openai.OpenAiProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** C01 manifest와 evaluation을 C02로 검증하고 C03 embedding batch를 만드는 content-import 전용 경계. */
@Component
@Profile("content-import")
class AiRouteContentImportPreparer {

    private static final String EVALUATION_FILE_NAME = "evaluation.json";

    private final Path fixtureRoot;
    private final Path evaluationPath;
    private final ContentManifestParser parser;
    private final AiRouteContentValidator validator;
    private final AiRouteContentEmbeddingService embeddingService;
    private final OpenAiProperties openAiProperties;

    AiRouteContentImportPreparer(
            ContentImportProperties properties,
            ObjectMapper objectMapper,
            OpenAiEmbeddingGateway embeddingGateway,
            OpenAiProperties openAiProperties) {
        Path normalizedManifest = properties.manifest().toAbsolutePath().normalize();
        this.fixtureRoot = normalizedManifest.getParent();
        if (fixtureRoot == null) {
            throw new IllegalStateException("AI 콘텐츠 manifest 상위 디렉터리를 확인할 수 없습니다.");
        }
        this.evaluationPath = fixtureRoot.resolve(EVALUATION_FILE_NAME);
        this.parser = new ContentManifestParser(objectMapper);
        this.validator = new AiRouteContentValidator(new PrerequisiteGraphValidator());
        this.embeddingService = new AiRouteContentEmbeddingService(embeddingGateway);
        this.openAiProperties = openAiProperties;
    }

    ValidatedAiRouteContent validate(AiRouteContentManifest manifest) {
        openAiProperties.validateForContentImport();
        AiRouteEvaluationDataset evaluation = parser.parseEvaluation(readEvaluation());
        return validator.validate(
                manifest,
                evaluation,
                fixtureRoot,
                openAiProperties.dataPolicyVersion());
    }

    EmbeddedAiRouteContent embed(ValidatedAiRouteContent content) {
        return embeddingService.embed(content, openAiProperties.dataPolicyVersion());
    }

    private byte[] readEvaluation() {
        try {
            return Files.readAllBytes(evaluationPath);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "AI 경로 evaluation 파일을 읽을 수 없습니다: " + evaluationPath,
                    exception);
        }
    }
}
