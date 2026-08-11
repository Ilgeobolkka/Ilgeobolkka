package com.example.ilgeobolkka.infra.openai;

import com.example.ilgeobolkka.infra.openai.OpenAiRouteException.Failure;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

@Component
@Conditional(OpenAiConfiguration.OpenAiRequiredCondition.class)
public final class OpenAiHttpRouteGateway implements OpenAiRouteGateway {

    private static final String RESPONSES_PATH = "/responses";
    private static final String MODEL = "gpt-5.6-terra";
    private static final String FORMAT_NAME = "ai_route_proposal_v1";
    private static final String PROMPT_RESOURCE =
            "openai/ai-route/route-generation-prompt-v1.md";
    private static final String SCHEMA_RESOURCE =
            "openai/ai-route/route-proposal-v1.schema.json";
    private static final Set<String> PROPOSAL_FIELDS = Set.of("items");
    private static final Set<String> ITEM_FIELDS =
            Set.of("pageNumber", "relevance", "prerequisite", "role");
    private static final Logger log = LoggerFactory.getLogger(OpenAiHttpRouteGateway.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ObjectReader strictJsonReader;
    private final String prompt;
    private final JsonNode schema;
    private final String promptVersion;
    private final String schemaVersion;

    public OpenAiHttpRouteGateway(
            @Qualifier("openAiRestClient") RestClient restClient,
            ObjectMapper objectMapper) {
        this.restClient = Objects.requireNonNull(restClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        strictJsonReader = objectMapper.reader(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);

        byte[] promptBytes = readResourceBytes(PROMPT_RESOURCE);
        byte[] schemaBytes = readResourceBytes(SCHEMA_RESOURCE);
        prompt = new String(promptBytes, StandardCharsets.UTF_8);
        schema = parseSchema(schemaBytes);
        promptVersion = resourceVersion("air-route-prompt-v1", promptBytes);
        schemaVersion = resourceVersion("air-route-schema-v1", schemaBytes);
    }

    @Override
    public RouteGatewayResult proposeRoute(RouteInput input) {
        validateInput(input);
        ResponsesRequest request = new ResponsesRequest(
                MODEL,
                false,
                prompt,
                serializeInput(input),
                new TextConfig(new JsonSchemaFormat("json_schema", FORMAT_NAME, schema, true)));

        try {
            JsonNode response = restClient.post()
                    .uri(RESPONSES_PATH)
                    .body(request)
                    .exchange((clientRequest, clientResponse) -> {
                        if (clientResponse.getStatusCode().value() == 429) {
                            throw new OpenAiRouteException(classifyLimit(clientResponse.getBody()));
                        }

                        if (clientResponse.getStatusCode().is5xxServerError()) {
                            throw new OpenAiRouteException(Failure.TEMPORARY);
                        }

                        if (clientResponse.getStatusCode().value() != 200) {
                            int status = clientResponse.getStatusCode().value();
                            String errorCode = extractErrorCode(clientResponse.getBody());
                            log.warn(
                                    "OpenAI Responses 요청 실패 status={} errorCode={}",
                                    status,
                                    errorCode);

                            throw new OpenAiRouteException(Failure.TEMPORARY);
                        }

                        return parseResponseBody(clientResponse.getBody());
                    });

            return new RouteGatewayResult(
                    parseProposal(response), promptVersion, schemaVersion);
        } catch (ResourceAccessException exception) {
            Failure failure = hasTimeoutCause(exception)
                    ? Failure.TIMEOUT_OR_INCOMPLETE
                    : Failure.TEMPORARY;
            throw new OpenAiRouteException(failure);
        } catch (RestClientException exception) {
            throw new OpenAiRouteException(Failure.TEMPORARY);
        }
    }

    static String resourceVersion(String logicalVersion, byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);

            return logicalVersion + ":sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.");
        }
    }

    private void validateInput(RouteInput input) {
        if (input == null) {
            throw new IllegalArgumentException("경로 생성 입력이 필요합니다.");
        }

        if (input.normalizedPurpose() == null || input.normalizedPurpose().isBlank()) {
            throw new IllegalArgumentException("정규화한 독서 목적이 필요합니다.");
        }

        if (input.candidates() == null || input.candidates().isEmpty()) {
            throw new IllegalArgumentException("경로 생성 후보가 필요합니다.");
        }

        if (input.prerequisiteEdges() == null) {
            throw new IllegalArgumentException("검증된 선수 관계가 필요합니다.");
        }

        for (CandidatePage candidate : input.candidates()) {
            if (candidate == null
                    || candidate.analysisText() == null
                    || candidate.analysisText().isBlank()) {
                throw new IllegalArgumentException("후보 페이지 분석 텍스트가 필요합니다.");
            }
        }
    }

    private String serializeInput(RouteInput input) {
        try {
            return objectMapper.writeValueAsString(new RouteInputPayload(
                    input.normalizedPurpose(), input.candidates(), input.prerequisiteEdges()));
        } catch (JacksonException exception) {
            throw new IllegalStateException("경로 생성 입력을 직렬화할 수 없습니다.");
        }
    }

    private JsonNode parseResponseBody(InputStream responseBody) {
        try {
            return strictJsonReader.readTree(responseBody);
        } catch (JacksonException exception) {
            throw malformedResponse("RESPONSE_BODY");
        }
    }

    private ModelRouteProposal parseProposal(JsonNode response) {
        if (response == null || !response.isObject()) {
            throw malformedResponse("RESPONSE_ENVELOPE");
        }

        String status = response.path("status").asString("");

        if ("incomplete".equals(status)) {
            log.warn(
                    "OpenAI Responses 응답 처리 실패 providerStatus=incomplete "
                            + "failure=TIMEOUT_OR_INCOMPLETE reason={}",
                    safeIncompleteReason(response));
            throw new OpenAiRouteException(Failure.TIMEOUT_OR_INCOMPLETE);
        }

        if ("failed".equals(status)) {
            log.warn(
                    "OpenAI Responses 응답 처리 실패 providerStatus=failed "
                            + "failure=TEMPORARY errorCode={}",
                    safeErrorCode(response.path("error").path("code")));
            throw new OpenAiRouteException(Failure.TEMPORARY);
        }

        if (!"completed".equals(status) || !response.path("output").isArray()) {
            throw malformedResponse("RESPONSE_ENVELOPE");
        }

        List<JsonNode> messages = new ArrayList<>();
        for (JsonNode output : response.path("output")) {
            if (output.isObject() && "message".equals(output.path("type").asString())) {
                messages.add(output);
            }
        }

        if (messages.size() != 1) {
            throw malformedResponse("OUTPUT_MESSAGES");
        }

        JsonNode content = messages.get(0).path("content");
        if (!content.isArray()) {
            throw malformedResponse("OUTPUT_CONTENT");
        }

        for (JsonNode item : content) {
            if ("refusal".equals(item.path("type").asString())) {
                log.warn(
                        "OpenAI Responses 응답 처리 실패 "
                                + "providerStatus=completed failure=REFUSAL");
                throw new OpenAiRouteException(Failure.REFUSAL);
            }
        }

        if (content.size() != 1
                || !"output_text".equals(content.get(0).path("type").asString())
                || !content.get(0).path("text").isString()) {
            throw malformedResponse("OUTPUT_CONTENT");
        }

        return parseProposalText(content.get(0).path("text").asString());
    }

    private ModelRouteProposal parseProposalText(String proposalText) {
        JsonNode proposal;
        try {
            proposal = strictJsonReader.readTree(proposalText);
        } catch (JacksonException exception) {
            throw malformedResponse("PROPOSAL_JSON");
        }

        if (!hasExactlyFields(proposal, PROPOSAL_FIELDS)
                || !proposal.path("items").isArray()
                || proposal.path("items").isEmpty()
                || proposal.path("items").size() > 72) {
            throw malformedResponse("PROPOSAL_SCHEMA");
        }

        List<ModelRouteItem> items = new ArrayList<>();

        for (JsonNode item : proposal.path("items")) {
            items.add(parseItem(item));
        }

        return new ModelRouteProposal(items);
    }

    private ModelRouteItem parseItem(JsonNode item) {
        JsonNode pageNumber = item.path("pageNumber");
        JsonNode relevance = item.path("relevance");
        JsonNode prerequisite = item.path("prerequisite");
        JsonNode role = item.path("role");

        if (!hasExactlyFields(item, ITEM_FIELDS)
                || !pageNumber.isIntegralNumber()
                || !pageNumber.canConvertToInt()
                || pageNumber.intValue() < 1
                || !relevance.isString()
                || !prerequisite.isBoolean()
                || !role.isString()) {
            throw malformedResponse("PROPOSAL_ITEM");
        }

        try {
            return new ModelRouteItem(
                    pageNumber.intValue(),
                    Relevance.valueOf(relevance.asString()),
                    prerequisite.asBoolean(),
                    Role.valueOf(role.asString()));
        } catch (IllegalArgumentException exception) {
            throw malformedResponse("PROPOSAL_ITEM");
        }
    }

    private OpenAiRouteException malformedResponse(String stage) {
        log.warn(
                "OpenAI Responses 응답 처리 실패 "
                        + "failure=MALFORMED_RESPONSE stage={}",
                stage);
        return new OpenAiRouteException(Failure.MALFORMED_RESPONSE);
    }

    private String safeIncompleteReason(JsonNode response) {
        return switch (response.path("incomplete_details").path("reason").asString("")) {
            case "max_output_tokens" -> "MAX_OUTPUT_TOKENS";
            case "content_filter" -> "CONTENT_FILTER";
            default -> "-";
        };
    }

    private String safeErrorCode(JsonNode errorCodeNode) {
        if (!errorCodeNode.isString()) {
            return "-";
        }

        return switch (errorCodeNode.asString()) {
            case "unsupported_parameter" -> "INVALID_REQUEST";
            case "invalid_api_key" -> "AUTHENTICATION_FAILED";
            case "project_permission_denied" -> "PERMISSION_DENIED";
            case "server_error" -> "SERVER_ERROR";
            case "rate_limit_exceeded" -> "RATE_LIMIT";
            default -> "-";
        };
    }

    private boolean hasExactlyFields(JsonNode node, Set<String> expectedFields) {
        if (node == null || !node.isObject()) {
            return false;
        }

        Set<String> actualFields = new HashSet<>();
        node.propertyNames().forEach(actualFields::add);

        return actualFields.equals(expectedFields);
    }

    private JsonNode parseSchema(byte[] schemaBytes) {
        try {
            return objectMapper.readTree(schemaBytes);
        } catch (JacksonException exception) {
            throw new IllegalStateException("경로 생성 schema를 읽을 수 없습니다.");
        }
    }

    private byte[] readResourceBytes(String resourcePath) {
        try (InputStream inputStream = new ClassPathResource(resourcePath).getInputStream()) {
            return inputStream.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("경로 생성 resource를 읽을 수 없습니다.");
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
            return Failure.TEMPORARY;
        }

        return Failure.TEMPORARY;
    }

    private String extractErrorCode(InputStream responseBody) {
        try {
            JsonNode errorCodeNode = objectMapper.readTree(responseBody)
                    .path("error")
                    .path("code");

            return safeErrorCode(errorCodeNode);
        } catch (RuntimeException exception) {
            return "-";
        }
    }

    private boolean hasTimeoutCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException) {
                return true;
            }
            current = current.getCause();
        }

        return false;
    }

    private record RouteInputPayload(
            String normalizedPurpose,
            List<CandidatePage> candidates,
            List<PrerequisiteEdge> prerequisiteEdges) {
    }

    private record ResponsesRequest(
            String model,
            boolean store,
            String instructions,
            String input,
            TextConfig text) {
    }

    private record TextConfig(JsonSchemaFormat format) {
    }

    private record JsonSchemaFormat(
            String type,
            String name,
            JsonNode schema,
            boolean strict) {
    }
}
