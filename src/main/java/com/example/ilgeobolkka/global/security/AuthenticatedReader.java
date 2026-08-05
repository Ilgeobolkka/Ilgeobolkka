package com.example.ilgeobolkka.global.security;

public record AuthenticatedReader(long readerId) {

    public AuthenticatedReader {
        if (readerId <= 0) {
            throw new IllegalArgumentException("readerId는 0보다 커야 합니다.");
        }
    }
}
