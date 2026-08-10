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

import com.example.ilgeobolkka.infra.openai.OpenAiEmbeddingException.Failure;
import com.example.ilgeobolkka.infra.openai.OpenAiEmbeddingGateway.Embedding;
import com.example.ilgeobolkka.infra.openai.OpenAiEmbeddingGateway.PageAnalysisInput;
import com.example.ilgeobolkka.infra.openai.OpenAiEmbeddingGateway.PurposeInput;
import java.io.IOException;
import java.util.ArrayList;
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
class OpenAiHttpEmbeddingGatewayTest {

    private static final String API_KEY = "sk-embedding-secret-test";
    private static final String PROJECT_ID = "project-embedding-secret-test";
    private static final String MODEL = "text-embedding-3-small";
    private static final int DIMENSIONS = 3;
    private static final String ENDPOINT = "https://api.openai.com/v1/embeddings";

    private ObjectMapper objectMapper;
    private MockRestServiceServer server;
    private OpenAiHttpEmbeddingGateway gateway;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY)
                .defaultHeader("OpenAI-Project", PROJECT_ID);
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new OpenAiHttpEmbeddingGateway(builder.build(), objectMapper);
    }

    @Test
    void 정규화한_목적만_Embedding_API에_보내고_vector를_반환한다() {
        String normalizedPurpose = "트랜잭션 격리 수준을 이해한다";
        expectEmbeddingRequest(json -> {
                    assertRequestFields(json);
                    assertThat(json.path("input").get(0).asString()).isEqualTo(normalizedPurpose);
                    assertThat(json.toString())
                            .doesNotContain("aiAnalysisText", "readerId", "inkBalance", "rental");
                })
                .andRespond(withSuccess(validResponse(), MediaType.APPLICATION_JSON));

        Embedding embedding = gateway.embedPurpose(
                new PurposeInput(normalizedPurpose), MODEL, DIMENSIONS);

        assertThat(embedding.vector()).containsExactly(0.1, 0.2, 0.3);
        assertThat(embedding.model()).isEqualTo(MODEL);
        assertThat(embedding.dimensions()).isEqualTo(DIMENSIONS);
        server.verify();
    }

    @Test
    void 한_페이지_분석_텍스트만_Embedding_API에_보낸다() {
        String aiAnalysisText = "격리 수준별 dirty read와 phantom read를 비교한다.";
        expectEmbeddingRequest(json -> {
                    assertRequestFields(json);
                    assertThat(json.path("input").get(0).asString()).isEqualTo(aiAnalysisText);
                    assertThat(json.toString())
                            .doesNotContain("normalizedPurpose", "readerId", "ink", "payment", "session");
                })
                .andRespond(withSuccess(validResponse(), MediaType.APPLICATION_JSON));

        Embedding embedding = gateway.embedPageAnalysis(
                new PageAnalysisInput(aiAnalysisText), MODEL, DIMENSIONS);

        assertThat(embedding.vector()).containsExactly(0.1, 0.2, 0.3);
        server.verify();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidResponses")
    void 잘못된_공급자_응답은_전체_실패한다(String description, String responseBody) {
        expectAnyEmbeddingRequest()
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        assertFailure(
                () -> gateway.embedPurpose(new PurposeInput("목적"), MODEL, DIMENSIONS),
                Failure.INVALID_RESPONSE);
        server.verify();
    }

    @Test
    void 파싱할_수_없는_2xx_응답은_invalid_response로_분류하고_원문을_숨긴다(CapturedOutput output) {
        String providerBody = "{\"data\":[\"" + API_KEY + " " + PROJECT_ID;
        expectAnyEmbeddingRequest()
                .andRespond(withSuccess(providerBody, MediaType.APPLICATION_JSON));

        OpenAiEmbeddingException exception = assertFailure(
                () -> gateway.embedPurpose(new PurposeInput("로그에 남으면 안 되는 목적"), MODEL, DIMENSIONS),
                Failure.INVALID_RESPONSE);

        assertNoSensitiveText(exception, output, providerBody);
        server.verify();
    }

    @Test
    void 공급자_5xx는_일시_오류로_분류하고_원문을_숨긴다(CapturedOutput output) {
        String providerBody = "분석 텍스트와 " + API_KEY + " " + PROJECT_ID;
        expectAnyEmbeddingRequest()
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(providerBody));

        OpenAiEmbeddingException exception = assertFailure(
                () -> gateway.embedPurpose(new PurposeInput("로그에 남으면 안 되는 목적"), MODEL, DIMENSIONS),
                Failure.TEMPORARY);

        assertNoSensitiveText(exception, output, providerBody);
        server.verify();
    }

    @ParameterizedTest(name = "[{index}] HTTP {0}")
    @MethodSource("clientErrorResponses")
    void 공급자_4xx는_status와_error_code만_로그에_남긴다(
            HttpStatus status,
            String errorCode,
            String expectedErrorCode,
            CapturedOutput output) {
        String purpose = "로그에 남으면 안 되는 목적";
        String providerMessage = "민감한 원인 " + API_KEY + " " + PROJECT_ID;
        String providerBody = """
                {"error":{"message":"%s","type":"request_error","code":"%s"}}
                """.formatted(providerMessage, errorCode);
        expectAnyEmbeddingRequest()
                .andRespond(withStatus(status)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(providerBody));

        OpenAiEmbeddingException exception = assertFailure(
                () -> gateway.embedPurpose(new PurposeInput(purpose), MODEL, DIMENSIONS),
                Failure.INVALID_RESPONSE);

        assertThat(output.getAll())
                .contains("WARN", "status=" + status.value(), "errorCode=" + expectedErrorCode)
                .doesNotContain(providerMessage, "request_error");
        assertNoSensitiveText(exception, output, providerBody);
        server.verify();
    }

    @Test
    void 알_수_없는_error_code는_로그에_노출하지_않는다(CapturedOutput output) {
        String providerBody = """
                {"error":{"message":"민감한 원인","type":"request_error","code":"%s"}}
                """.formatted(API_KEY);
        expectAnyEmbeddingRequest()
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(providerBody));

        OpenAiEmbeddingException exception = assertFailure(
                () -> gateway.embedPurpose(new PurposeInput("로그에 남으면 안 되는 목적"), MODEL, DIMENSIONS),
                Failure.INVALID_RESPONSE);

        assertThat(output.getAll()).contains("WARN", "status=400", "errorCode=-");
        assertNoSensitiveText(exception, output, providerBody);
        server.verify();
    }

    @Test
    void 파싱할_수_없는_4xx_응답도_원문_없이_status만_로그에_남긴다(CapturedOutput output) {
        String providerBody = "invalid-body " + API_KEY + " " + PROJECT_ID;
        expectAnyEmbeddingRequest()
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(providerBody));

        OpenAiEmbeddingException exception = assertFailure(
                () -> gateway.embedPurpose(new PurposeInput("로그에 남으면 안 되는 목적"), MODEL, DIMENSIONS),
                Failure.INVALID_RESPONSE);

        assertThat(output.getAll())
                .contains("WARN", "status=400", "errorCode=-")
                .doesNotContain(providerBody);
        assertNoSensitiveText(exception, output, providerBody);
        server.verify();
    }

    @Test
    void 일반_429는_rate_limit으로_분류한다() {
        expectAnyEmbeddingRequest()
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error":{"message":"Rate limit reached for current usage tier",\
                                "type":"rate_limit_error","code":"rate_limit_exceeded"}}
                                """));

        assertFailure(
                () -> gateway.embedPurpose(new PurposeInput("목적"), MODEL, DIMENSIONS),
                Failure.RATE_LIMIT);
        server.verify();
    }

    @Test
    void 비_JSON_429는_rate_limit으로_폴백하고_원문을_숨긴다(CapturedOutput output) {
        String providerBody = "invalid 429 body " + API_KEY + " " + PROJECT_ID;
        expectAnyEmbeddingRequest()
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(providerBody));

        OpenAiEmbeddingException exception = assertFailure(
                () -> gateway.embedPurpose(new PurposeInput("로그에 남으면 안 되는 목적"), MODEL, DIMENSIONS),
                Failure.RATE_LIMIT);

        assertNoSensitiveText(exception, output, providerBody);
        server.verify();
    }

    @Test
    void insufficient_quota_type_429는_budget_limit으로_분류하고_원문을_숨긴다(CapturedOutput output) {
        String providerBody = """
                {"error":{"message":"Provider limit for %s and %s",\
                "type":"insufficient_quota","code":"unknown_limit"}}
                """.formatted(PROJECT_ID, API_KEY);
        expectAnyEmbeddingRequest()
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(providerBody));

        OpenAiEmbeddingException exception = assertFailure(
                () -> gateway.embedPageAnalysis(
                        new PageAnalysisInput("외부에 전송하는 분석 텍스트"), MODEL, DIMENSIONS),
                Failure.BUDGET_LIMIT);

        assertNoSensitiveText(exception, output, providerBody);
        server.verify();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("budgetLimitErrorCodes")
    void 공식_예산_한도_error_code_429는_budget_limit으로_분류하고_원문을_숨긴다(
            String errorCode,
            CapturedOutput output) {
        String providerBody = """
                {"error":{"message":"Provider limit for %s and %s",\
                "type":"provider_error","code":"%s"}}
                """.formatted(PROJECT_ID, API_KEY, errorCode);
        expectAnyEmbeddingRequest()
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(providerBody));

        OpenAiEmbeddingException exception = assertFailure(
                () -> gateway.embedPageAnalysis(
                        new PageAnalysisInput("외부에 전송하는 분석 텍스트"), MODEL, DIMENSIONS),
                Failure.BUDGET_LIMIT);

        assertNoSensitiveText(exception, output, providerBody);
        server.verify();
    }

    @Test
    void 네트워크_오류는_일시_오류로_분류하고_원인을_노출하지_않는다() {
        expectAnyEmbeddingRequest()
                .andRespond(withException(new IOException("network " + API_KEY)));

        OpenAiEmbeddingException exception = assertFailure(
                () -> gateway.embedPurpose(new PurposeInput("목적"), MODEL, DIMENSIONS),
                Failure.TEMPORARY);

        assertThat(exception).hasNoCause();
        assertThat(exception.getMessage()).doesNotContain(API_KEY);
        server.verify();
    }

    @Test
    void 빈_입력과_모델_잘못된_차원은_HTTP_호출_전에_거부한다() {
        assertThatThrownBy(() -> gateway.embedPurpose(null, MODEL, DIMENSIONS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gateway.embedPurpose(new PurposeInput(" "), MODEL, DIMENSIONS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gateway.embedPageAnalysis(new PageAnalysisInput(" "), MODEL, DIMENSIONS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gateway.embedPurpose(new PurposeInput("목적"), " ", DIMENSIONS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gateway.embedPurpose(new PurposeInput("목적"), MODEL, 0))
                .isInstanceOf(IllegalArgumentException.class);

        server.verify();
    }

    @Test
    void F03_OpenAI_HTTP_Bean이_필요한_조건에서_Gateway를_등록한다() {
        gatewayContextRunner()
                .withPropertyValues("ai-route.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OpenAiHttpEmbeddingGateway.class);
                    assertThat(context.getBean(OpenAiEmbeddingGateway.class))
                            .isSameAs(context.getBean(OpenAiHttpEmbeddingGateway.class));
                });
    }

    @Test
    void F03_OpenAI_HTTP_Bean이_필요하지_않은_조건에서는_Gateway를_등록하지_않는다() {
        gatewayContextRunner()
                .withPropertyValues("ai-route.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(OpenAiEmbeddingGateway.class);
                });
    }

    private ResponseActions expectEmbeddingRequest(Consumer<JsonNode> bodyAssertions) {
        return server.expect(requestTo(ENDPOINT))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY))
                .andExpect(header("OpenAI-Project", PROJECT_ID))
                .andExpect(jsonBody(bodyAssertions));
    }

    private ResponseActions expectAnyEmbeddingRequest() {
        return expectEmbeddingRequest(json -> assertRequestFields(json));
    }

    private RequestMatcher jsonBody(Consumer<JsonNode> assertions) {
        return request -> {
            MockClientHttpRequest mockRequest = (MockClientHttpRequest) request;
            JsonNode json = objectMapper.readTree(mockRequest.getBodyAsBytes());
            assertions.accept(json);
        };
    }

    private void assertRequestFields(JsonNode json) {
        List<String> fields = new ArrayList<>(json.propertyNames());
        assertThat(fields)
                .containsExactlyInAnyOrder("input", "model", "dimensions", "encoding_format");
        assertThat(json.path("input").isArray()).isTrue();
        assertThat(json.path("input")).hasSize(1);
        assertThat(json.path("model").asString()).isEqualTo(MODEL);
        assertThat(json.path("dimensions").asInt()).isEqualTo(DIMENSIONS);
        assertThat(json.path("encoding_format").asString()).isEqualTo("float");
    }

    private OpenAiEmbeddingException assertFailure(Runnable call, Failure expectedFailure) {
        OpenAiEmbeddingException exception = catchThrowableOfType(
                call::run, OpenAiEmbeddingException.class);
        assertThat(exception.failure()).isEqualTo(expectedFailure);
        assertThat(exception).hasNoCause();

        return exception;
    }

    private void assertNoSensitiveText(
            OpenAiEmbeddingException exception,
            CapturedOutput output,
            String providerBody) {
        assertThat(exception.getMessage())
                .doesNotContain(API_KEY, PROJECT_ID, providerBody, "분석 텍스트", "목적");
        assertThat(output.getAll())
                .doesNotContain(API_KEY, PROJECT_ID, providerBody, "분석 텍스트", "목적");
    }

    private String validResponse() {
        return """
                {"object":"list","data":[{"object":"embedding",\
                "embedding":[0.1,0.2,0.3],"index":0}],\
                "model":"text-embedding-3-small",\
                "usage":{"prompt_tokens":3,"total_tokens":3}}
                """;
    }

    private static Stream<Arguments> invalidResponses() {
        return Stream.of(
                Arguments.of("차원 불일치", response("[0.1,0.2]", "0", MODEL)),
                Arguments.of("null 원소", response("[0.1,null,0.3]", "0", MODEL)),
                Arguments.of("비유한 수", response("[0.1,1e309,0.3]", "0", MODEL)),
                Arguments.of("문자열 vector 원소", response("[0.1,\"0.2\",0.3]", "0", MODEL)),
                Arguments.of("문자열 index", response("[0.1,0.2,0.3]", "\"0\"", MODEL)),
                Arguments.of("빈 vector", response("[]", "0", MODEL)),
                Arguments.of("빈 data", "{\"data\":[],\"model\":\"" + MODEL + "\"}"),
                Arguments.of("data 원소 2개", """
                        {"data":[{"embedding":[0.1,0.2,0.3],"index":0},
                        {"embedding":[0.4,0.5,0.6],"index":0}],
                        "model":"text-embedding-3-small"}
                        """),
                Arguments.of("누락 index", """
                        {"data":[{"embedding":[0.1,0.2,0.3]}],
                        "model":"text-embedding-3-small"}
                        """),
                Arguments.of("model 불일치", response("[0.1,0.2,0.3]", "0", "other-model")),
                Arguments.of("null data", "{\"data\":null,\"model\":\"" + MODEL + "\"}"),
                Arguments.of("null vector", response("null", "0", MODEL)));
    }

    private static Stream<Arguments> clientErrorResponses() {
        return Stream.of(
                Arguments.of(HttpStatus.BAD_REQUEST, "unsupported_parameter", "INVALID_REQUEST"),
                Arguments.of(HttpStatus.UNAUTHORIZED, "invalid_api_key", "AUTHENTICATION_FAILED"),
                Arguments.of(HttpStatus.FORBIDDEN, "project_permission_denied", "PERMISSION_DENIED"));
    }

    private static Stream<String> budgetLimitErrorCodes() {
        return Stream.of(
                "organization_spend_limit_exceeded",
                "project_spend_limit_exceeded",
                "organization_usage_limit_exceeded",
                "credit_balance_exhausted");
    }

    private static String response(String vector, String index, String model) {
        return "{\"data\":[{\"embedding\":" + vector + ",\"index\":" + index
                + "}],\"model\":\"" + model + "\"}";
    }

    private ApplicationContextRunner gatewayContextRunner() {
        return new ApplicationContextRunner().withUserConfiguration(GatewayTestConfiguration.class);
    }

    @Configuration(proxyBeanMethods = false)
    @Import(OpenAiHttpEmbeddingGateway.class)
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
