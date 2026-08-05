package com.example.ilgeobolkka.global.web;

import com.example.ilgeobolkka.global.security.AuthenticatedReader;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class CommonWebModelAdvice {

    @ModelAttribute
    void addCommonModel(
            Authentication authentication,
            HttpServletRequest request,
            Model model) {
        boolean authenticated = authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedReader;
        model.addAttribute("authenticated", authenticated);
        model.addAttribute("currentPath", request.getRequestURI());
    }
}
