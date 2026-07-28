package com.example.ilgeobolkka.auth.dto;

import com.example.ilgeobolkka.reader.entity.Reader;

public record SignupAuthResponse(long readerId, String email) {

    public static SignupAuthResponse from(Reader reader) {
        return new SignupAuthResponse(reader.getId(), reader.getEmail());
    }
}
