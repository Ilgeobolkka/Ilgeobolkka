package com.example.ilgeobolkka.contentimport;

import com.example.ilgeobolkka.contentimport.embedding.AiRouteContentEmbeddingService;
import com.example.ilgeobolkka.contentimport.manifest.ContentManifestParser;
import com.example.ilgeobolkka.contentimport.validation.AiRouteContentValidator;
import com.example.ilgeobolkka.contentimport.validation.PrerequisiteGraphValidator;
import com.example.ilgeobolkka.infra.openai.OpenAiEmbeddingGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.ObjectMapper;

/**
 * `ai-route-v2` 적재에 쓰는 검증·embedding 구성요소.
 *
 * <p>검증기는 외부 의존이 없고, embedding service는 Gateway 하나만 받는다. 둘 다 적재 배치에서만
 * 필요하므로 `content-import` profile에서만 만든다.
 */
@Configuration
@Profile("content-import")
class AiRouteContentImportConfiguration {

    @Bean
    ContentManifestParser contentManifestParser(ObjectMapper objectMapper) {
        return new ContentManifestParser(objectMapper);
    }

    @Bean
    PrerequisiteGraphValidator prerequisiteGraphValidator() {
        return new PrerequisiteGraphValidator();
    }

    @Bean
    AiRouteContentValidator aiRouteContentValidator(PrerequisiteGraphValidator graphValidator) {
        return new AiRouteContentValidator(graphValidator);
    }

    @Bean
    AiRouteContentEmbeddingService aiRouteContentEmbeddingService(
            OpenAiEmbeddingGateway embeddingGateway) {
        return new AiRouteContentEmbeddingService(embeddingGateway);
    }
}
