package com.example.ilgeobolkka.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignupAuthRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotNull String password) {

    public SignupAuthRequest {
        email = AuthInputPolicy.normalizeEmail(email);
    }

    @AssertTrue
    public boolean isPasswordValid() {
        return AuthInputPolicy.isPasswordValid(password);
    }
}
