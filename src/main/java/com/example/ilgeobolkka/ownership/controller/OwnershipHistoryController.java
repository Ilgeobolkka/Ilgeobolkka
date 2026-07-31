package com.example.ilgeobolkka.ownership.controller;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.ilgeobolkka.ownership.dto.FindOwnershipPaymentsResponse;
import com.example.ilgeobolkka.ownership.facade.OwnershipHistoryFacade;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ownership-payments")
@RequiredArgsConstructor
public class OwnershipHistoryController {

    private final OwnershipHistoryFacade ownershipHistoryFacade;

    @GetMapping
    FindOwnershipPaymentsResponse findHistory(
            @RequestParam @Min(1) int page,
            @AuthenticationPrincipal AuthenticatedReader authenticatedReader) {
        return ownershipHistoryFacade.findHistory(authenticatedReader.readerId(), page);
    }
}
