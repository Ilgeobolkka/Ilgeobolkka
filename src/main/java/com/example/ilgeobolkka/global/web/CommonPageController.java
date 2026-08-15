package com.example.ilgeobolkka.global.web;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CommonPageController {

    private final Environment environment;

    CommonPageController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/")
    String redirectToBooks() {
        return "redirect:/books";
    }

    @GetMapping("/books")
    String books(Model model) {
        model.addAttribute("pageTitle", "도서 탐색");
        return "pages/books";
    }

    @GetMapping("/books/{bookId}")
    String bookDetail(@PathVariable long bookId, Model model) {
        model.addAttribute("bookId", bookId);
        model.addAttribute("pageTitle", "도서 상세");
        model.addAttribute("ownershipPaymentEnabled", paymentEnabled());
        model.addAttribute("aiRouteEnabled", aiRouteEnabled());
        return "pages/book-detail";
    }

    @GetMapping("/signup")
    String signup(Model model) {
        model.addAttribute("pageTitle", "회원가입");
        return "pages/signup";
    }

    @GetMapping("/login")
    String login(
            @RequestParam(required = false) String returnTo,
            Model model) {
        model.addAttribute("pageTitle", "로그인");
        model.addAttribute("loginSuccessPath", loginSuccessPath(returnTo));
        return "pages/login";
    }

    @GetMapping("/books/{bookId}/viewer")
    String viewer(
            @PathVariable long bookId,
            @RequestParam(defaultValue = "1") int page,
            Model model) {
        model.addAttribute("bookId", bookId);
        model.addAttribute("initialPage", page);
        model.addAttribute("pageTitle", "뷰어");
        return "pages/viewer";
    }

    @GetMapping("/ink")
    String ink(Model model) {
        model.addAttribute("pageTitle", "잉크");
        model.addAttribute("inkPurchaseEnabled", paymentEnabled());
        return "pages/ink";
    }

    @GetMapping("/ownership-payments")
    String ownershipPayments(Model model) {
        model.addAttribute("pageTitle", "소장 결제 내역");
        return "pages/ownership-payments";
    }

    @GetMapping("/library")
    String library(Model model) {
        model.addAttribute("pageTitle", "내 서재");
        model.addAttribute("aiRouteEnabled", aiRouteEnabled());
        return "pages/library";
    }

    private String loginSuccessPath(String returnTo) {
        if (returnTo != null && returnTo.matches("/books/[1-9][0-9]*/ai-route")) {
            return returnTo;
        }
        return "/books";
    }

    private boolean aiRouteEnabled() {
        return environment.getProperty("ai-route.enabled", Boolean.class, false);
    }

    private boolean paymentEnabled() {
        return environment.acceptsProfiles(Profiles.of("!prod"))
                && environment.getProperty("portone.payment.enabled", Boolean.class, false);
    }
}
