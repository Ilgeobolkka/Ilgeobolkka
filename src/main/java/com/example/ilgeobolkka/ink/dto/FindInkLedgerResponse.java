package com.example.ilgeobolkka.ink.dto;

import com.example.ilgeobolkka.ink.repository.InkLedgerEntryQuery;
import java.util.List;
import org.springframework.data.domain.Page;

public record FindInkLedgerResponse(
        List<InkLedgerEntryResponse> entries,
        int page,
        int totalPages,
        long totalCount) {

    public static FindInkLedgerResponse from(
            Page<InkLedgerEntryQuery> entries,
            int requestedPage) {
        List<InkLedgerEntryResponse> responses = entries.getContent().stream()
                .map(InkLedgerEntryResponse::from)
                .toList();

        return new FindInkLedgerResponse(
                responses,
                requestedPage,
                entries.getTotalPages(),
                entries.getTotalElements());
    }
}
