package com.example.ilgeobolkka.webhook.controller;

import com.example.ilgeobolkka.webhook.facade.PortOneWebhookFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/portone")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "portone.payment", name = "enabled", havingValue = "true")
@Profile("!prod")
public class PortOneWebhookController {

    private final PortOneWebhookFacade portOneWebhookFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    void receive(
            @RequestBody String rawBody,
            @RequestHeader(name = "webhook-id", required = false) String webhookId,
            @RequestHeader(name = "webhook-signature", required = false) String webhookSignature,
            @RequestHeader(name = "webhook-timestamp", required = false) String webhookTimestamp) {
        portOneWebhookFacade.handle(
                rawBody,
                webhookId,
                webhookSignature,
                webhookTimestamp);
    }
}
