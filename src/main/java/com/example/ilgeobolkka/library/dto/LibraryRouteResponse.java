package com.example.ilgeobolkka.library.dto;

import com.example.ilgeobolkka.library.repository.LibraryEntryView;

public record LibraryRouteResponse(long routeId, String purpose) {

    public static LibraryRouteResponse from(LibraryEntryView entry) {
        return new LibraryRouteResponse(entry.getRouteId(), entry.getRoutePurpose());
    }
}
