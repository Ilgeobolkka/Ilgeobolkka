package com.example.ilgeobolkka.infra.openai;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AiRouteFeatureProperties.class, OpenAiProperties.class})
public class OpenAiConfiguration {

    private static final String OPENAI_PROJECT_HEADER = "OpenAI-Project";

    @Bean
    @Conditional(OpenAiRequiredCondition.class)
    RestClient openAiRestClient(
            AiRouteFeatureProperties featureProperties,
            OpenAiProperties openAiProperties,
            Environment environment) {
        validateForActiveMode(featureProperties, openAiProperties, environment);

        RestClient.Builder builder = RestClient.builder().baseUrl(openAiProperties.baseUrl());
        if (environment.acceptsProfiles(Profiles.of("content-import"))) {
            return builder.requestInterceptor((request, body, execution) -> {
                        openAiProperties.validateForContentImport();
                        setAuthenticationHeaders(request.getHeaders(), openAiProperties);

                        return execution.execute(request, body);
                    })
                    .build();
        }

        return builder
                .defaultHeaders(headers -> setAuthenticationHeaders(headers, openAiProperties))
                .build();
    }

    private void validateForActiveMode(
            AiRouteFeatureProperties featureProperties,
            OpenAiProperties openAiProperties,
            Environment environment) {
        // 두 프로필이 같이 활성화되어도 evaluation의 기동 중 검증을 content-import의 요청 직전 검증보다 우선한다.
        if (environment.acceptsProfiles(Profiles.of("evaluation"))) {
            openAiProperties.validateForEvaluation();

            return;
        }

        if (environment.acceptsProfiles(Profiles.of("content-import"))) {
            return;
        }

        openAiProperties.validateForServer(featureProperties.enabled());
    }

    private void setAuthenticationHeaders(HttpHeaders headers, OpenAiProperties openAiProperties) {
        headers.setBearerAuth(openAiProperties.apiKey());
        headers.set(OPENAI_PROJECT_HEADER, openAiProperties.projectId());
    }

    static final class OpenAiRequiredCondition extends AnyNestedCondition {

        OpenAiRequiredCondition() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "ai-route", name = "enabled", havingValue = "true")
        static final class AiRouteEnabled {
        }

        @Profile({"content-import", "evaluation"})
        static final class AiBatchProfile {
        }
    }
}
