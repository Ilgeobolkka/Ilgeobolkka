package com.example.ilgeobolkka.airoute.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@ConditionalOnProperty(prefix = "ai-route", name = "enabled", havingValue = "true")
public class AiRouteGenerationPageController {

    @GetMapping("/books/{bookId}/ai-route")
    String generationPage(@PathVariable long bookId, Model model) {
        model.addAttribute("bookId", bookId);
        model.addAttribute("pageTitle", "AI 독서 경로 만들기");
        return "pages/ai-route-generation";
    }
}
