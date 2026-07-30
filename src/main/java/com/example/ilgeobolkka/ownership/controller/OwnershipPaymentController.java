package com.example.ilgeobolkka.ownership.controller;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.ilgeobolkka.ownership.dto.PrepareOwnershipPaymentResponse;
import com.example.ilgeobolkka.ownership.facade.OwnershipPaymentFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books/{bookId}/ownership-payments")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "portone.payment", name = "enabled", havingValue = "true")
@Profile("!prod")
public class OwnershipPaymentController {

    private final OwnershipPaymentFacade ownershipPaymentFacade;

    @PostMapping
    ResponseEntity<PrepareOwnershipPaymentResponse> prepare(
            @PathVariable long bookId,
            @AuthenticationPrincipal AuthenticatedReader authenticatedReader) {
        OwnershipPaymentFacade.Preparation preparation =
                ownershipPaymentFacade.prepare(authenticatedReader.readerId(), bookId);
        HttpStatus status = preparation.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(preparation.response());
    }
}
