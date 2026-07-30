package com.example.ilgeobolkka.ink.controller;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.ilgeobolkka.ink.dto.CompleteInkPurchaseResponse;
import com.example.ilgeobolkka.ink.dto.PrepareInkPurchaseResponse;
import com.example.ilgeobolkka.ink.entity.InkPurchaseStatus;
import com.example.ilgeobolkka.ink.facade.InkPurchaseFacade;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ink/purchases")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "portone.payment", name = "enabled", havingValue = "true")
public class InkPurchaseController {

    private final InkPurchaseFacade inkPurchaseFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PrepareInkPurchaseResponse prepare(
            @AuthenticationPrincipal AuthenticatedReader authenticatedReader) {
        return inkPurchaseFacade.prepare(authenticatedReader.readerId());
    }

    @PostMapping("/{paymentId}/complete")
    ResponseEntity<CompleteInkPurchaseResponse> complete(
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal AuthenticatedReader authenticatedReader) {
        CompleteInkPurchaseResponse response =
                inkPurchaseFacade.complete(authenticatedReader.readerId(), paymentId);
        HttpStatus status = response.status() == InkPurchaseStatus.PENDING
                ? HttpStatus.ACCEPTED
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }
}
