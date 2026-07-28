package com.example.ilgeobolkka.auth.controller;

import com.example.ilgeobolkka.auth.dto.LoginAuthRequest;
import com.example.ilgeobolkka.auth.dto.LoginAuthResponse;
import com.example.ilgeobolkka.auth.dto.LogoutAuthResponse;
import com.example.ilgeobolkka.auth.dto.SignupAuthRequest;
import com.example.ilgeobolkka.auth.dto.SignupAuthResponse;
import com.example.ilgeobolkka.auth.facade.AuthFacade;
import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
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
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final LogoutHandler logoutHandler;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    SignupAuthResponse signup(@Valid @RequestBody SignupAuthRequest request) {
        return authFacade.signup(request);
    }

    @PostMapping("/login")
    LoginAuthResponse login(
            @Valid @RequestBody LoginAuthRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        LoginAuthResponse response = authFacade.login(request);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedReader(response.readerId()),
                null,
                List.of());

        httpRequest.getSession(true);
        sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
        securityContextRepository.saveContext(securityContext, httpRequest, httpResponse);
        return response;
    }

    @PostMapping("/logout")
    LogoutAuthResponse logout(
            @AuthenticationPrincipal AuthenticatedReader authenticatedReader,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        long readerId = authenticatedReader.readerId();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        authFacade.logout(readerId);
        logoutHandler.logout(httpRequest, httpResponse, authentication);
        return new LogoutAuthResponse(readerId);
    }
}
