package com.example.ilgeobolkka.auth.dto;

import com.example.ilgeobolkka.reader.entity.Reader;

public record LoginAuthResponse(long readerId, String email) {

    public static LoginAuthResponse from(Reader reader) {
        return new LoginAuthResponse(reader.getId(), reader.getEmail());
    }
}
