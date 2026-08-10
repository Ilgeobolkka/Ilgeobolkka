package com.example.ilgeobolkka.infra.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.ilgeobolkka.infra.openai.OpenAiRouteException.Failure;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway.CandidatePage;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway.ModelRouteItem;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway.PrerequisiteEdge;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway.Relevance;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway.Role;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway.RouteGatewayResult;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway.RouteInput;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(OutputCaptureExtension.class)
class OpenAiHttpRouteGatewayTest {

    private static final String API_KEY = "sk-route-secret-test";
    private static final String PROJECT_ID = "project-route-secret-test";
    private static final String MODEL = "gpt-5.6-terra";
    private static final String ENDPOINT = "https://api.openai.com/v1/responses";
    private static final String PROMPT_RESOURCE =
            "openai/ai-route/route-generation-prompt-v1.md";
    private static final String SCHEMA_RESOURCE =
            "openai/ai-route/route-proposal-v1.schema.json";

    private ObjectMapper objectMapper;
    private MockRestServiceServer server;
    private OpenAiHttpRouteGateway gateway;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY)
                .defaultHeader("OpenAI-Project", PROJECT_ID);
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new OpenAiHttpRouteGateway(builder.build(), objectMapper);
    }

    @Test
    void 정규화_목적과_서버_후보만_strict_Responses_API에_보낸다() throws Exception {
        RouteInput input = routeInput();
        String expectedPrompt = readResource(PROMPT_RESOURCE);
        expectRouteRequest(json -> {
                    assertRequestContract(json);
                    assertThat(json.path("instructions").asString())
                            .isEqualTo(expectedPrompt);

                    JsonNode payload = objectMapper.readTree(json.path("input").asString());
                    assertThat(new ArrayList<>(payload.propertyNames()))
                            .containsExactlyInAnyOrder(
                                    "normalizedPurpose", "candidates", "prerequisiteEdges");
                    assertThat(payload.path("normalizedPurpose").asString())
                            .isEqualTo(input.normalizedPurpose());
                    assertThat(payload.path("candidates")).hasSize(2);
                    assertThat(payload.path("candidates").get(0).path("pageNumber").asInt())
                            .isEqualTo(7);
                    assertThat(payload.path("candidates").get(0).path("analysisText").asString())
                            .isEqualTo("트랜잭션 격리 수준의 기본 개념");
                    assertThat(payload.path("prerequisiteEdges").get(0)
                                    .path("prerequisitePageNumber").asInt())
                            .isEqualTo(7);
                    assertThat(payload.path("prerequisiteEdges").get(0)
                                    .path("dependentPageNumber").asInt())
                            .isEqualTo(12);
                    assertThat(payload.toString())
                            .doesNotContain(
                                    "readerId", "budget", "ink", "rental", "ownership",
                                    "payment", "session", "evaluationAnswer");
                })
                .andRespond(withSuccess(
                        completedResponse(validProposal()), MediaType.APPLICATION_JSON));

        RouteGatewayResult result = gateway.proposeRoute(input);

        assertThat(result.proposal().items()).containsExactly(
                new ModelRouteItem(7, Relevance.MEDIUM, true, Role.PREREQUISITE),
                new ModelRouteItem(12, Relevance.HIGH, false, Role.CORE));
        assertThat(result.promptVersion())
                .isEqualTo(expectedVersion("air-route-prompt-v1", PROMPT_RESOURCE));
        assertThat(result.schemaVersion())
                .isEqualTo(expectedVersion("air-route-schema-v1", SCHEMA_RESOURCE));
        server.verify();
    }

    @Test
    void schema는_items와_각_item의_strict_계약을_모두_포함한다() {
        expectRouteRequest(this::assertRequestContract)
                .andRespond(withSuccess(
                        completedResponse(validProposal()), MediaType.APPLICATION_JSON));

        gateway.proposeRoute(routeInput());

        server.verify();
    }

    @Test
    void proposal_items는_72개까지_허용한다() {
        expectAnyRouteRequest()
                .andRespond(withSuccess(
                        completedResponse(proposalWithItemCount(72)),
                        MediaType.APPLICATION_JSON));

        RouteGatewayResult result = gateway.proposeRoute(routeInput());

        assertThat(result.proposal().items()).hasSize(72);
        server.verify();
    }

    @Test
    void proposal_items가_73개면_malformed_response로_실패한다() {
        expectAnyRouteRequest()
                .andRespond(withSuccess(
                        completedResponse(proposalWithItemCount(73)),
                        MediaType.APPLICATION_JSON));

        assertFailure(() -> gateway.proposeRoute(routeInput()), Failure.MALFORMED_RESPONSE);

        server.verify();
    }

    @Test
    void resource_version은_정규화하지_않은_UTF8_byte_전체의_SHA256이다()
            throws Exception {
        byte[] promptBytes = readResourceBytes(PROMPT_RESOURCE);
        byte[] schemaBytes = readResourceBytes(SCHEMA_RESOURCE);

        assertThat(OpenAiHttpRouteGateway.resourceVersion(
                        "air-route-prompt-v1", promptBytes))
                .isEqualTo("air-route-prompt-v1:sha256:" + sha256(promptBytes));
        assertThat(OpenAiHttpRouteGateway.resourceVersion(
                        "air-route-schema-v1", schemaBytes))
                .isEqualTo("air-route-schema-v1:sha256:" + sha256(schemaBytes));

        byte[] withWhitespace = (new String(promptBytes, StandardCharsets.UTF_8) + " ")
                .getBytes(StandardCharsets.UTF_8);
        assertThat(OpenAiHttpRouteGateway.resourceVersion(
                        "air-route-prompt-v1", withWhitespace))
                .isNotEqualTo(OpenAiHttpRouteGateway.resourceVersion(
                        "air-route-prompt-v1", promptBytes));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidProposals")
    void schema를_벗어난_proposal은_malformed_response로_전체_실패한다(
            String description,
            String proposal) {
        expectAnyRouteRequest()
                .andRespond(withSuccess(
                        completedResponse(proposal), MediaType.APPLICATION_JSON));

        assertFailure(() -> gateway.proposeRoute(routeInput()), Failure.MALFORMED_RESPONSE);

        server.verify();
    }

    @Test
    void 파싱할_수_없는_output_text는_malformed_response로_분류하고_원문을_숨긴다(
            CapturedOutput output) {
        String providerBody = completedResponse("{\"items\":[\"" + API_KEY);
        expectAnyRouteRequest()
                .andRespond(withSuccess(providerBody, MediaType.APPLICATION_JSON));

        OpenAiRouteException exception = assertFailure(
                () -> gateway.proposeRoute(routeInput()), Failure.MALFORMED_RESPONSE);

        assertNoSensitiveText(exception, output, providerBody);
        server.verify();
    }

    @Test
    void completed_응답에_message가_없으면_재시도_가능한_malformed_response다() {
        expectAnyRouteRequest()
                .andRespond(withSuccess(
                        "{\"status\":\"completed\",\"output\":[]}",
                        MediaType.APPLICATION_JSON));

        assertFailure(() -> gateway.proposeRoute(routeInput()), Failure.MALFORMED_RESPONSE);

        server.verify();
    }

    @Test
    void completed_응답에_message가_두_개면_재시도_가능한_malformed_response다() {
        String message = message(validProposal());
        expectAnyRouteRequest()
                .andRespond(withSuccess(
                        "{\"status\":\"completed\",\"output\":[" + message + "," + message + "]}",
                        MediaType.APPLICATION_JSON));

        assertFailure(() -> gateway.proposeRoute(routeInput()), Failure.MALFORMED_RESPONSE);

        server.verify();
    }

    @Test
    void incomplete는_검증_재시도_대상이_아닌_timeout_or_incomplete로_분류한다() {
        expectAnyRouteRequest()
                .andRespond(withSuccess(
                        "{\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"},\"output\":[]}",
                        MediaType.APPLICATION_JSON));

        assertFailure(
                () -> gateway.proposeRoute(routeInput()),
                Failure.TIMEOUT_OR_INCOMPLETE);

        server.verify();
    }

    @Test
    void failed는_검증_재시도_대상이_아닌_temporary로_분류하고_원문을_숨긴다(
            CapturedOutput output) {
        String providerBody = """
                {"status":"failed","error":{"code":"server_error",\
                "message":"민감한 공급자 오류 %s %s"},"output":[]}
                """.formatted(API_KEY, PROJECT_ID);
        expectAnyRouteRequest()
                .andRespond(withSuccess(providerBody, MediaType.APPLICATION_JSON));

        OpenAiRouteException exception = assertFailure(
                () -> gateway.proposeRoute(routeInput()), Failure.TEMPORARY);

        assertNoSensitiveText(exception, output, providerBody);
        server.verify();
    }

    @Test
    void refusal은_검증_재시도_대상이_아닌_refusal로_분류하고_원문을_숨긴다(
            CapturedOutput output) {
        String refusal = "거절 원문 " + API_KEY + " " + PROJECT_ID;
        String providerBody = """
                {"status":"completed","output":[{"type":"message","content":[
                {"type":"refusal","refusal":"%s"}]}]}
                """.formatted(refusal);
        expectAnyRouteRequest()
                .andRespond(withSuccess(providerBody, MediaType.APPLICATION_JSON));

        OpenAiRouteException exception = assertFailure(
                () -> gateway.proposeRoute(routeInput()), Failure.REFUSAL);

        assertNoSensitiveText(exception, output, providerBody);
        server.verify();
    }

    @Test
    void 공급자_5xx는_temporary로_분류하고_원문을_숨긴다(CapturedOutput output) {
        String providerBody = "민감한 분석 텍스트 " + API_KEY + " " + PROJECT_ID;
        expectAnyRouteRequest()
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(providerBody));

        OpenAiRouteException exception = assertFailure(
                () -> gateway.proposeRoute(routeInput()), Failure.TEMPORARY);

        assertNoSensitiveText(exception, output, providerBody);
        server.verify();
    }

    @Test
    void 공급자_4xx는_status와_허용된_error_code만_로그에_남긴다(CapturedOutput output) {
        String providerMessage = "민감한 원인 " + API_KEY + " " + PROJECT_ID;
        String providerBody = """
                {"error":{"message":"%s","type":"request_error",\
                "code":"unsupported_parameter"}}
                """.formatted(providerMessage);
        expectAnyRouteRequest()
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(providerBody));

        OpenAiRouteException exception = assertFailure(
                () -> gateway.proposeRoute(routeInput()), Failure.TEMPORARY);

        assertThat(output.getAll())
                .contains("WARN", "status=400", "errorCode=INVALID_REQUEST")
                .doesNotContain(providerMessage, "request_error");
        assertNoSensitiveText(exception, output, providerBody);
        server.verify();
    }

    @Test
    void 일반_429는_temporary로_분류한다() {
        expectAnyRouteRequest()
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error":{"message":"Rate limit reached",\
                                "type":"rate_limit_error","code":"rate_limit_exceeded"}}
                                """));

        assertFailure(() -> gateway.proposeRoute(routeInput()), Failure.TEMPORARY);

        server.verify();
    }

    @Test
    void budget_limit_429는_budget_limit으로_분류하고_원문을_숨긴다(
            CapturedOutput output) {
        String providerBody = """
                {"error":{"message":"Provider limit for %s and %s",\
                "type":"insufficient_quota","code":"project_spend_limit_exceeded"}}
                """.formatted(PROJECT_ID, API_KEY);
        expectAnyRouteRequest()
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(providerBody));

        OpenAiRouteException exception = assertFailure(
                () -> gateway.proposeRoute(routeInput()), Failure.BUDGET_LIMIT);

        assertNoSensitiveText(exception, output, providerBody);
        server.verify();
    }

    @Test
    void 네트워크_timeout은_timeout_or_incomplete로_분류하고_원인을_숨긴다() {
        expectAnyRouteRequest()
                .andRespond(withException(new SocketTimeoutException("timeout " + API_KEY)));

        OpenAiRouteException exception = assertFailure(
                () -> gateway.proposeRoute(routeInput()),
                Failure.TIMEOUT_OR_INCOMPLETE);

        assertThat(exception.getMessage()).doesNotContain(API_KEY);
        server.verify();
    }

    @Test
    void 일반_네트워크_오류는_temporary로_분류한다() {
        expectAnyRouteRequest()
                .andRespond(withException(new IOException("network " + API_KEY)));

        assertFailure(() -> gateway.proposeRoute(routeInput()), Failure.TEMPORARY);

        server.verify();
    }

    @Test
    void 입력_DTO는_독자_예산_잉크_결제_세션_평가정답을_받지_않고_toString에도_본문을_숨긴다() {
        RouteInput input = routeInput();

        assertThat(Stream.of(RouteInput.class.getRecordComponents())
                        .map(component -> component.getName()))
                .containsExactly("normalizedPurpose", "candidates", "prerequisiteEdges");
        assertThat(Stream.of(CandidatePage.class.getRecordComponents())
                        .map(component -> component.getName()))
                .containsExactly("pageNumber", "analysisText");
        assertThat(input.toString())
                .doesNotContain(input.normalizedPurpose(), input.candidates().get(0).analysisText());
        assertThat(input.candidates().get(0).toString())
                .doesNotContain(input.candidates().get(0).analysisText());
    }

    @Test
    void 필수_입력이_없으면_HTTP_호출_전에_거부한다() {
        assertThatThrownBy(() -> gateway.proposeRoute(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gateway.proposeRoute(
                        new RouteInput(" ", routeInput().candidates(), List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gateway.proposeRoute(
                        new RouteInput("목적", List.of(), List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gateway.proposeRoute(new RouteInput(
                        "목적", List.of(new CandidatePage(1, " ")), List.of())))
                .isInstanceOf(IllegalArgumentException.class);

        server.verify();
    }

    @Test
    void F03_OpenAI_HTTP_Bean이_필요한_조건에서_Gateway를_등록한다() {
        gatewayContextRunner()
                .withPropertyValues("ai-route.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OpenAiHttpRouteGateway.class);
                    assertThat(context.getBean(OpenAiRouteGateway.class))
                            .isSameAs(context.getBean(OpenAiHttpRouteGateway.class));
                });
    }

    @Test
    void F03_OpenAI_HTTP_Bean이_필요하지_않은_조건에서는_Gateway를_등록하지_않는다() {
        gatewayContextRunner()
                .withPropertyValues("ai-route.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(OpenAiRouteGateway.class);
                });
    }

    private ResponseActions expectRouteRequest(Consumer<JsonNode> bodyAssertions) {
        return server.expect(requestTo(ENDPOINT))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY))
                .andExpect(header("OpenAI-Project", PROJECT_ID))
                .andExpect(jsonBody(bodyAssertions));
    }

    private ResponseActions expectAnyRouteRequest() {
        return expectRouteRequest(this::assertRequestContract);
    }

    private RequestMatcher jsonBody(Consumer<JsonNode> assertions) {
        return request -> {
            MockClientHttpRequest mockRequest = (MockClientHttpRequest) request;
            JsonNode json = objectMapper.readTree(mockRequest.getBodyAsBytes());
            assertions.accept(json);
        };
    }

    private void assertRequestContract(JsonNode json) {
        assertThat(new ArrayList<>(json.propertyNames()))
                .containsExactlyInAnyOrder("model", "store", "instructions", "input", "text");
        assertThat(json.path("model").asString()).isEqualTo(MODEL);
        assertThat(json.path("store").asBoolean()).isFalse();
        assertThat(json.path("instructions").isString()).isTrue();
        assertThat(json.path("input").isString()).isTrue();

        JsonNode format = json.path("text").path("format");
        assertThat(new ArrayList<>(format.propertyNames()))
                .containsExactlyInAnyOrder("type", "name", "schema", "strict");
        assertThat(format.path("type").asString()).isEqualTo("json_schema");
        assertThat(format.path("name").asString()).isEqualTo("ai_route_proposal_v1");
        assertThat(format.path("strict").asBoolean()).isTrue();

        JsonNode schema = format.path("schema");
        assertThat(schema.path("type").asString()).isEqualTo("object");
        assertThat(schema.path("required"))
                .extracting(JsonNode::asString)
                .containsExactly("items");
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();

        JsonNode items = schema.path("properties").path("items");
        assertThat(items.path("type").asString()).isEqualTo("array");
        assertThat(items.path("minItems").asInt()).isEqualTo(1);
        assertThat(items.path("maxItems").asInt()).isEqualTo(72);

        JsonNode item = items.path("items");
        assertThat(item.path("required"))
                .extracting(JsonNode::asString)
                .containsExactlyInAnyOrder(
                        "pageNumber", "relevance", "prerequisite", "role");
        assertThat(item.path("additionalProperties").asBoolean()).isFalse();
        assertThat(item.path("properties").path("pageNumber").path("type").asString())
                .isEqualTo("integer");
        assertThat(item.path("properties").path("pageNumber").path("minimum").asInt())
                .isEqualTo(1);
        assertThat(item.path("properties").path("relevance").path("enum"))
                .extracting(JsonNode::asString)
                .containsExactly("HIGH", "MEDIUM");
        assertThat(item.path("properties").path("prerequisite").path("type").asString())
                .isEqualTo("boolean");
        assertThat(item.path("properties").path("role").path("enum"))
                .extracting(JsonNode::asString)
                .containsExactly(
                        "PREREQUISITE", "CORE", "EXAMPLE", "COUNTERPOINT", "CONCLUSION");

        assertThat(json.toString())
                .doesNotContain(
                        "previous_response_id", "background", "stream", "tools",
                        "readerId", "inkBalance", "payment", "session", "evaluationAnswer");
    }

    private OpenAiRouteException assertFailure(Runnable call, Failure expectedFailure) {
        OpenAiRouteException exception = catchThrowableOfType(OpenAiRouteException.class, call::run);
        assertThat(exception.failure()).isEqualTo(expectedFailure);
        assertThat(exception).hasNoCause();

        return exception;
    }

    private void assertNoSensitiveText(
            OpenAiRouteException exception,
            CapturedOutput output,
            String providerBody) {
        assertThat(exception.getMessage())
                .doesNotContain(
                        API_KEY, PROJECT_ID, providerBody,
                        "트랜잭션 격리 수준", "분석 텍스트", "독서 목적");
        assertThat(output.getAll())
                .doesNotContain(
                        API_KEY, PROJECT_ID, providerBody,
                        "트랜잭션 격리 수준", "분석 텍스트", "독서 목적");
    }

    private RouteInput routeInput() {
        return new RouteInput(
                "트랜잭션 격리 수준을 이해한다",
                List.of(
                        new CandidatePage(7, "트랜잭션 격리 수준의 기본 개념"),
                        new CandidatePage(12, "격리 수준별 동시성 문제 비교")),
                List.of(new PrerequisiteEdge(7, 12)));
    }

    private String validProposal() {
        return """
                {"items":[
                {"pageNumber":7,"relevance":"MEDIUM","prerequisite":true,"role":"PREREQUISITE"},
                {"pageNumber":12,"relevance":"HIGH","prerequisite":false,"role":"CORE"}
                ]}
                """;
    }

    private String proposalWithItemCount(int itemCount) {
        List<String> items = new ArrayList<>(itemCount);
        for (int pageNumber = 1; pageNumber <= itemCount; pageNumber++) {
            items.add("{\"pageNumber\":" + pageNumber
                    + ",\"relevance\":\"HIGH\",\"prerequisite\":false,\"role\":\"CORE\"}");
        }

        return "{\"items\":[" + String.join(",", items) + "]}";
    }

    private String completedResponse(String proposal) {
        return "{\"status\":\"completed\",\"output\":[" + message(proposal) + "]}";
    }

    private String message(String proposal) {
        try {
            return "{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":"
                    + objectMapper.writeValueAsString(proposal) + "}]}";
        } catch (RuntimeException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String expectedVersion(String logicalVersion, String resourcePath) throws Exception {
        return logicalVersion + ":sha256:" + sha256(readResourceBytes(resourcePath));
    }

    private String readResource(String resourcePath) throws Exception {
        return new String(readResourceBytes(resourcePath), StandardCharsets.UTF_8);
    }

    private byte[] readResourceBytes(String resourcePath) throws Exception {
        try (InputStream inputStream = new ClassPathResource(resourcePath).getInputStream()) {
            return inputStream.readAllBytes();
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Stream<Arguments> invalidProposals() {
        return Stream.of(
                Arguments.of("pageNumber 0", item(0, "HIGH", true, "CORE")),
                Arguments.of("음수 pageNumber", item(-1, "HIGH", true, "CORE")),
                Arguments.of("알 수 없는 relevance", item(1, "LOW", true, "CORE")),
                Arguments.of("알 수 없는 role", item(1, "HIGH", true, "SUMMARY")),
                Arguments.of("item 자유 필드", """
                        {"items":[{"pageNumber":1,"relevance":"HIGH",\
                        "prerequisite":true,"role":"CORE","guide":"금지"}]}
                        """),
                Arguments.of("최상위 자유 필드", """
                        {"items":[{"pageNumber":1,"relevance":"HIGH",\
                        "prerequisite":true,"role":"CORE"}],"guide":"금지"}
                        """),
                Arguments.of("빈 items", "{\"items\":[]}"),
                Arguments.of("필수 필드 누락", """
                        {"items":[{"pageNumber":1,"relevance":"HIGH","role":"CORE"}]}
                        """));
    }

    private static String item(
            int pageNumber,
            String relevance,
            boolean prerequisite,
            String role) {
        return "{\"items\":[{\"pageNumber\":" + pageNumber
                + ",\"relevance\":\"" + relevance
                + "\",\"prerequisite\":" + prerequisite
                + ",\"role\":\"" + role + "\"}]}";
    }

    private ApplicationContextRunner gatewayContextRunner() {
        return new ApplicationContextRunner().withUserConfiguration(GatewayTestConfiguration.class);
    }

    @Configuration(proxyBeanMethods = false)
    @Import(OpenAiHttpRouteGateway.class)
    static class GatewayTestConfiguration {

        @Bean
        RestClient openAiRestClient() {
            return RestClient.create();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
