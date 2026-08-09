package com.example.ilgeobolkka.infra.openai;

import com.example.ilgeobolkka.infra.openai.OpenAiEmbeddingException.Failure;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@Conditional(OpenAiConfiguration.OpenAiRequiredCondition.class)
public final class OpenAiHttpEmbeddingGateway implements OpenAiEmbeddingGateway {

    private static final String EMBEDDINGS_PATH = "/embeddings";
    private static final Logger log = LoggerFactory.getLogger(OpenAiHttpEmbeddingGateway.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OpenAiHttpEmbeddingGateway(
            @Qualifier("openAiRestClient") RestClient restClient,
            ObjectMapper objectMapper) {
        this.restClient = Objects.requireNonNull(restClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public Embedding embedPurpose(PurposeInput input, String model, int dimensions) {
        if (input == null) {
            throw new IllegalArgumentException("정규화한 독서 목적이 필요합니다.");
        }

        return embed(input.normalizedPurpose(), model, dimensions);
    }

    @Override
    public Embedding embedPageAnalysis(PageAnalysisInput input, String model, int dimensions) {
        if (input == null) {
            throw new IllegalArgumentException("페이지 분석 텍스트가 필요합니다.");
        }

        return embed(input.aiAnalysisText(), model, dimensions);
    }

    private Embedding embed(String text, String model, int dimensions) {
        validateRequest(text, model, dimensions);
        EmbeddingRequest request = new EmbeddingRequest(
                List.of(text), model, dimensions, "float");

        try {
            EmbeddingResponse response = restClient.post()
                    .uri(EMBEDDINGS_PATH)
                    .body(request)
                    .exchange((clientRequest, clientResponse) -> {
                        if (clientResponse.getStatusCode().value() == 429) {
                            throw new OpenAiEmbeddingException(classifyLimit(clientResponse.getBody()));
                        }

                        if (clientResponse.getStatusCode().is5xxServerError()) {
                            throw new OpenAiEmbeddingException(Failure.TEMPORARY);
                        }

                        if (clientResponse.getStatusCode().isError()) {
                            int status = clientResponse.getStatusCode().value();
                            String errorCode = extractErrorCode(clientResponse.getBody());
                            log.warn(
                                    "OpenAI Embeddings 요청 실패 status={} errorCode={}",
                                    status,
                                    errorCode);

                            throw new OpenAiEmbeddingException(Failure.INVALID_RESPONSE);
                        }

                        return clientResponse.bodyTo(EmbeddingResponse.class);
                    });

            return validateResponse(response, model, dimensions);
        } catch (ResourceAccessException exception) {
            throw new OpenAiEmbeddingException(Failure.TEMPORARY);
        } catch (RestClientException exception) {
            throw new OpenAiEmbeddingException(Failure.INVALID_RESPONSE);
        }
    }

    private void validateRequest(String text, String model, int dimensions) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Embedding 입력 텍스트가 필요합니다.");
        }

        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Embedding 모델이 필요합니다.");
        }

        if (dimensions < 1) {
            throw new IllegalArgumentException("Embedding 차원은 1 이상이어야 합니다.");
        }
    }

    private Embedding validateResponse(
            EmbeddingResponse response,
            String requestedModel,
            int requestedDimensions) {
        if (response == null
                || !requestedModel.equals(response.model())
                || response.data() == null
                || response.data().size() != 1) {
            throw new OpenAiEmbeddingException(Failure.INVALID_RESPONSE);
        }

        EmbeddingData item = response.data().getFirst();
        if (item == null || item.index() == null || item.index() != 0) {
            throw new OpenAiEmbeddingException(Failure.INVALID_RESPONSE);
        }

        List<Double> vector = item.embedding();
        if (vector == null
                || vector.isEmpty()
                || vector.size() != requestedDimensions
                || vector.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new OpenAiEmbeddingException(Failure.INVALID_RESPONSE);
        }

        return new Embedding(List.copyOf(vector), response.model(), requestedDimensions);
    }

    private String extractErrorCode(InputStream responseBody) {
        try {
            String errorCode = objectMapper.readTree(responseBody)
                    .path("error")
                    .path("code")
                    .asString("");

            return errorCode.matches("[A-Za-z0-9._-]{1,100}") ? errorCode : "-";
        } catch (RuntimeException exception) {
            return "-";
        }
    }

    private Failure classifyLimit(InputStream responseBody) {
        try {
            JsonNode error = objectMapper.readTree(responseBody).path("error");
            String category = String.join(" ",
                            error.path("code").asString(""),
                            error.path("type").asString(""))
                    .toLowerCase(Locale.ROOT);
            String message = error.path("message").asString("").toLowerCase(Locale.ROOT);

            if (category.contains("insufficient_quota")
                    || category.contains("spend_limit")
                    || category.contains("usage_limit")
                    || category.contains("credit")
                    || category.contains("billing")
                    || message.contains("spend limit")
                    || message.contains("usage limit")
                    || message.contains("credit")) {
                return Failure.BUDGET_LIMIT;
            }
        } catch (RuntimeException exception) {
            return Failure.RATE_LIMIT;
        }

        return Failure.RATE_LIMIT;
    }

    private record EmbeddingRequest(
            List<String> input,
            String model,
            int dimensions,
            @JsonProperty("encoding_format") String encodingFormat) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingResponse(List<EmbeddingData> data, String model) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmbeddingData(List<Double> embedding, Integer index) {
    }
}
