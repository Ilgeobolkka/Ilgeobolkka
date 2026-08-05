package com.example.ilgeobolkka.reading.dto;

import org.springframework.http.MediaType;

public record PageContent(byte[] body, MediaType mediaType) {}
