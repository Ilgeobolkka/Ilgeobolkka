package com.example.ilgeobolkka.ink.controller;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import com.example.ilgeobolkka.ink.dto.FindInkBalanceResponse;
import com.example.ilgeobolkka.ink.dto.FindInkLedgerResponse;
import com.example.ilgeobolkka.ink.facade.InkFacade;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ink")
@RequiredArgsConstructor
public class InkController {

    private final InkFacade inkFacade;

    @GetMapping("/balance")
    FindInkBalanceResponse findBalance(
            @AuthenticationPrincipal AuthenticatedReader authenticatedReader) {
        return inkFacade.findBalance(authenticatedReader.readerId());
    }

    @GetMapping("/ledger")
    FindInkLedgerResponse findLedger(
            @AuthenticationPrincipal AuthenticatedReader authenticatedReader,
            @RequestParam @Min(1) int page) {
        return inkFacade.findLedger(authenticatedReader.readerId(), page);
    }
}
