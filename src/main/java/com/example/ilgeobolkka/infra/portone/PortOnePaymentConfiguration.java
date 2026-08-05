package com.example.ilgeobolkka.infra.portone;

import io.portone.sdk.server.payment.PaymentClient;
import io.portone.sdk.server.webhook.WebhookVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PortOnePaymentProperties.class)
@Profile("!prod")
public class PortOnePaymentConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "portone.payment", name = "enabled", havingValue = "true")
    PaymentClient portOneSdkPaymentClient(PortOnePaymentProperties properties) {
        properties.validateEnabledConfiguration();
        return new PaymentClient(
                properties.apiSecret(),
                "https://api.portone.io",
                null);
    }

    @Bean
    @ConditionalOnMissingBean(PortOnePaymentGateway.class)
    @ConditionalOnProperty(prefix = "portone.payment", name = "enabled", havingValue = "true")
    PortOnePaymentGateway portOnePaymentGateway(PaymentClient paymentClient) {
        return new PortOneSdkPaymentGateway(paymentClient);
    }

    @Bean
    @ConditionalOnProperty(prefix = "portone.payment", name = "enabled", havingValue = "true")
    PortOneWebhookVerifier portOneWebhookVerifier(PortOnePaymentProperties properties) {
        return new PortOneWebhookVerifier(new WebhookVerifier(properties.webhookSecret()));
    }
}
