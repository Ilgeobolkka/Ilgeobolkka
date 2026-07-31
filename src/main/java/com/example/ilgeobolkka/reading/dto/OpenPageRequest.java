package com.example.ilgeobolkka.reading.dto;

import jakarta.validation.constraints.Min;

public record OpenPageRequest(@Min(1) int pageNumber) {
}
