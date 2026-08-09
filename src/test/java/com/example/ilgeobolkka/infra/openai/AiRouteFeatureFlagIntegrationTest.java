package com.example.ilgeobolkka.infra.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(OutputCaptureExtension.class)
class AiRouteFeatureFlagIntegrationTest {

    private static final String API_KEY = "sk-openai-context-secret-test-value";
    private static final String BOOT_CUSTOMIZER_HEADER = "X-Boot-RestClient-Customizer";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
            .withBean(
                    RestClientCustomizer.class,
                    () -> builder -> builder.defaultHeader(BOOT_CUSTOMIZER_HEADER, "applied"))
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(OpenAiConfiguration.class);

    @Test
    void 비활성_일반_서버는_OpenAI_설정_없이_기동하고_HTTP_Bean을_등록하지_않는다() {
        contextRunner
                .withPropertyValues(
                        "AI_ROUTE_ENABLED=false",
                        "OPENAI_PROJECT_ID=",
                        "OPENAI_API_KEY=",
                        "OPENAI_DATA_POLICY_VERSION=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(RestClient.class);
                    assertThat(context).hasSingleBean(AiRouteFeatureProperties.class);
                    assertThat(context.getBean(AiRouteFeatureProperties.class).enabled()).isFalse();
                });
    }

    @Test
    void 활성_일반_서버는_검증된_OpenAI_HTTP_Bean을_등록한다() {
        completeServerContext().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RestClient.class);
            assertThat(context.getBean(AiRouteFeatureProperties.class).enabled()).isTrue();
            assertThat(context.getBean(OpenAiProperties.class).apiKey()).isEqualTo(API_KEY);
        });
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @MethodSource("missingSettings")
    void 활성_일반_서버는_OpenAI_설정이_하나라도_없으면_기동을_거부한다(
            String projectId,
            String apiKey,
            String dataPolicyVersion,
            String environmentVariable) {
        contextRunner
                .withPropertyValues(
                        "AI_ROUTE_ENABLED=true",
                        "OPENAI_PROJECT_ID=" + projectId,
                        "OPENAI_API_KEY=" + apiKey,
                        "OPENAI_DATA_POLICY_VERSION=" + dataPolicyVersion)
                .run(context -> assertStartupFailure(
                        context.getStartupFailure(),
                        "AI 경로 활성화에는 " + environmentVariable + " 값이 필요합니다."));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"content-import", "evaluation"})
    void 비웹_실행은_공개_기능_플래그가_꺼져도_OpenAI_HTTP_Bean을_등록한다(String profile) {
        completeBatchContext(profile).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RestClient.class);
            assertThat(context.getBean(AiRouteFeatureProperties.class).enabled()).isFalse();
        });
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"server", "evaluation"})
    void 일반_서버와_evaluation의_OpenAI_HTTP_Bean은_인증_헤더와_Boot_공통_설정을_적용한다(String executionMode) {
        completeExternalRequestContext(executionMode).run(context -> {
            RestClient.Builder builder = context.getBean(RestClient.class).mutate();
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

            server.expect(requestTo("https://api.openai.com/v1/models"))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY))
                    .andExpect(header("OpenAI-Project", "project-test"))
                    .andExpect(header(BOOT_CUSTOMIZER_HEADER, "applied"))
                    .andRespond(withSuccess());

            builder.build().get().uri("/models").retrieve().toBodilessEntity();

            server.verify();
        });
    }

    @Test
    void 초기_content_import는_OpenAI_설정_없이_기동한다() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=content-import",
                        "AI_ROUTE_ENABLED=false",
                        "OPENAI_PROJECT_ID=",
                        "OPENAI_API_KEY=",
                        "OPENAI_DATA_POLICY_VERSION=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RestClient.class);
                    assertThat(context.getEnvironment().getProperty("content-import.manifest"))
                            .isEqualTo("fixtures/content/manifest.json");
                });
    }

    @Test
    void content_import의_OpenAI_HTTP_Bean은_요청_직전에_설정을_검증한다() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=content-import",
                        "AI_ROUTE_ENABLED=false",
                        "OPENAI_PROJECT_ID=",
                        "OPENAI_API_KEY=",
                        "OPENAI_DATA_POLICY_VERSION=")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    RestClient restClient = context.getBean(RestClient.class);
                    assertThatThrownBy(() -> restClient.get()
                                    .uri("/embeddings")
                                    .retrieve()
                                    .toBodilessEntity())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage("content-import 실행에는 OPENAI_PROJECT_ID 값이 필요합니다.");
                });
    }

    @ParameterizedTest(name = "[{index}] {3}")
    @MethodSource("missingSettings")
    void evaluation은_OpenAI_설정이_하나라도_없으면_외부_호출_전에_실패한다(
            String projectId,
            String apiKey,
            String dataPolicyVersion,
            String environmentVariable) {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=evaluation",
                        "AI_ROUTE_ENABLED=false",
                        "OPENAI_PROJECT_ID=" + projectId,
                        "OPENAI_API_KEY=" + apiKey,
                        "OPENAI_DATA_POLICY_VERSION=" + dataPolicyVersion)
                .run(context -> assertStartupFailure(
                        context.getStartupFailure(),
                        "evaluation 실행에는 " + environmentVariable + " 값이 필요합니다."));
    }

    @Test
    void 설정_검증_예외와_로그는_API_키_원문을_노출하지_않는다(CapturedOutput output) {
        contextRunner
                .withPropertyValues(
                        "AI_ROUTE_ENABLED=true",
                        "OPENAI_PROJECT_ID=",
                        "OPENAI_API_KEY=" + API_KEY,
                        "OPENAI_DATA_POLICY_VERSION=policy-v1")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(causeMessages(context.getStartupFailure())).doesNotContain(API_KEY);
                });

        assertThat(output.getAll()).doesNotContain(API_KEY);
    }

    private ApplicationContextRunner completeServerContext() {
        return contextRunner.withPropertyValues(
                "AI_ROUTE_ENABLED=true",
                "OPENAI_PROJECT_ID=project-test",
                "OPENAI_API_KEY=" + API_KEY,
                "OPENAI_DATA_POLICY_VERSION=policy-v1");
    }

    private ApplicationContextRunner completeBatchContext(String profile) {
        return contextRunner.withPropertyValues(
                "spring.profiles.active=" + profile,
                "AI_ROUTE_ENABLED=false",
                "OPENAI_PROJECT_ID=project-test",
                "OPENAI_API_KEY=" + API_KEY,
                "OPENAI_DATA_POLICY_VERSION=policy-v1");
    }

    private ApplicationContextRunner completeExternalRequestContext(String executionMode) {
        if ("evaluation".equals(executionMode)) {
            return completeBatchContext(executionMode);
        }

        return completeServerContext();
    }

    private void assertStartupFailure(Throwable failure, String expectedRootCauseMessage) {
        assertThat(failure)
                .isNotNull()
                .hasRootCauseMessage(expectedRootCauseMessage);
        assertThat(causeMessages(failure)).doesNotContain(API_KEY);
    }

    private String causeMessages(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        Throwable cause = failure;
        while (cause != null) {
            messages.append(cause.getClass().getName())
                    .append(':')
                    .append(cause.getMessage())
                    .append('\n');
            cause = cause.getCause();
        }
        return messages.toString();
    }

    private static Stream<Arguments> missingSettings() {
        return Stream.of(
                Arguments.of("", API_KEY, "policy-v1", "OPENAI_PROJECT_ID"),
                Arguments.of("project-test", "", "policy-v1", "OPENAI_API_KEY"),
                Arguments.of("project-test", API_KEY, "", "OPENAI_DATA_POLICY_VERSION"));
    }
}
