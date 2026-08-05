package com.example.ilgeobolkka.ink.dto;

import com.example.ilgeobolkka.ink.repository.InkLedgerEntryProjection;
import java.util.List;
import org.springframework.data.domain.Page;

public record FindInkLedgerResponse(
        List<InkLedgerEntryResponse> entries,
        int page,
        int totalPages,
        long totalCount) {

    public static FindInkLedgerResponse from(
            Page<InkLedgerEntryProjection> entries,
            int requestedPage) {
        List<InkLedgerEntryResponse> items =
                entries.getContent().stream()
                        .map(InkLedgerEntryResponse::from)
                        .toList();
        return new FindInkLedgerResponse(
                items,
                requestedPage,
                entries.getTotalPages(),
                entries.getTotalElements());
    }
}
