package com.example.ilgeobolkka.airoute.service.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.example.ilgeobolkka.airoute.exception.InvalidAiRouteGenerationInputException;
import com.example.ilgeobolkka.book.service.BookService;
import com.example.ilgeobolkka.infra.openai.AiRouteFeatureProperties;
import com.example.ilgeobolkka.infra.openai.OpenAiEmbeddingGateway;
import com.example.ilgeobolkka.infra.openai.OpenAiProperties;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;

class AiRouteGenerationEngineContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(EngineConfiguration.class);

    @Test
    void evaluation은_공개_기능이_꺼져도_Engine을_등록한다() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=evaluation",
                        "ai-route.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AiRouteGenerationEngine.class);
                    assertThatThrownBy(() -> context.getBean(AiRouteGenerationEngine.class)
                            .generate(null, null, null))
                            .isInstanceOf(InvalidAiRouteGenerationInputException.class)
                            .hasMessageContaining("생성 콘텐츠·권한 사본과 제한 시간이 필요합니다.");
                });
    }

    @Test
    void 비활성_일반_서버는_Engine을_등록하지_않는다() {
        contextRunner
                .withPropertyValues("ai-route.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(AiRouteGenerationEngine.class);
                });
    }

    @Test
    void content_import는_Engine을_등록하지_않는다() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=content-import",
                        "ai-route.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(AiRouteGenerationEngine.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(AiRouteGenerationEngine.class)
    static class EngineConfiguration {

        @Bean
        AiRouteFeatureProperties aiRouteFeatureProperties() {
            return new AiRouteFeatureProperties(false);
        }

        @Bean
        OpenAiProperties openAiProperties() {
            return new OpenAiProperties("project-test", "secret-test", "policy-v1");
        }

        @Bean
        BookService bookService() {
            return mock(BookService.class);
        }

        @Bean
        AiRoutePrerequisiteService prerequisiteService() {
            return mock(AiRoutePrerequisiteService.class);
        }

        @Bean
        OpenAiEmbeddingGateway embeddingGateway() {
            return mock(OpenAiEmbeddingGateway.class);
        }

        @Bean
        OpenAiRouteGateway routeGateway() {
            return mock(OpenAiRouteGateway.class);
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }
    }
}
