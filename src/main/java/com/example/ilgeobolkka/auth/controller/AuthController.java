package com.example.ilgeobolkka.auth.controller;

import com.example.ilgeobolkka.auth.dto.SignupAuthRequest;
import com.example.ilgeobolkka.auth.dto.SignupAuthResponse;
import com.example.ilgeobolkka.auth.facade.AuthFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthFacade authFacade;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    SignupAuthResponse signup(@Valid @RequestBody SignupAuthRequest request) {
        return authFacade.signup(request);
    }
}
