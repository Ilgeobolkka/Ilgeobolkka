package com.example.ilgeobolkka.infra.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ai-route")
public record AiRouteFeatureProperties(boolean enabled) {
}
