package com.example.ilgeobolkka.infra.portone;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("portone.payment")
public record PortOnePaymentProperties(
        boolean enabled,
        String storeId,
        String channelKey,
        String apiSecret,
        String webhookSecret) {

    void validateEnabledConfiguration() {
        requireValue(storeId, "PORTONE_STORE_ID");
        requireValue(channelKey, "PORTONE_CHANNEL_KEY");
        requireValue(apiSecret, "PORTONE_API_SECRET");
        requireValue(webhookSecret, "PORTONE_WEBHOOK_SECRET");
    }

    private void requireValue(String value, String environmentVariable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "PortOne 결제 활성화에는 " + environmentVariable + " 값이 필요합니다.");
        }
    }
}
