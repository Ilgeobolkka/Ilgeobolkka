package com.example.ilgeobolkka.ownership.controller;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.ilgeobolkka.ownership.dto.CompleteOwnershipPaymentResponse;
import com.example.ilgeobolkka.ownership.dto.FindOwnershipPaymentsResponse;
import com.example.ilgeobolkka.ownership.entity.OwnershipPaymentStatus;
import com.example.ilgeobolkka.ownership.facade.OwnershipPaymentFacade;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ownership-payments")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "portone.payment", name = "enabled", havingValue = "true")
@Profile("!prod")
public class OwnershipPaymentsController {

    private final OwnershipPaymentFacade ownershipPaymentFacade;

    @PostMapping("/{paymentId}/complete")
    ResponseEntity<CompleteOwnershipPaymentResponse> complete(
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal AuthenticatedReader authenticatedReader) {
        CompleteOwnershipPaymentResponse response =
                ownershipPaymentFacade.complete(authenticatedReader.readerId(), paymentId);
        HttpStatus status = response.status() == OwnershipPaymentStatus.PENDING
                ? HttpStatus.ACCEPTED
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping
    FindOwnershipPaymentsResponse findHistory(
            @RequestParam @Min(1) int page,
            @AuthenticationPrincipal AuthenticatedReader authenticatedReader) {
        return ownershipPaymentFacade.findHistory(authenticatedReader.readerId(), page);
    }
}
