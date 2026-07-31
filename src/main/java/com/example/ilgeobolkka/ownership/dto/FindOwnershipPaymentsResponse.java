package com.example.ilgeobolkka.ownership.dto;

import com.example.ilgeobolkka.ownership.repository.OwnershipPaymentEntryProjection;
import java.util.List;
import org.springframework.data.domain.Page;

public record FindOwnershipPaymentsResponse(
        List<OwnershipPaymentHistoryEntryResponse> payments,
        int page,
        int totalPages,
        long totalCount) {

    public static FindOwnershipPaymentsResponse from(
            Page<OwnershipPaymentEntryProjection> entries,
            int requestedPage) {
        List<OwnershipPaymentHistoryEntryResponse> items =
                entries.getContent().stream()
                        .map(OwnershipPaymentHistoryEntryResponse::from)
                        .toList();
        return new FindOwnershipPaymentsResponse(
                items,
                requestedPage,
                entries.getTotalPages(),
                entries.getTotalElements());
    }
}
