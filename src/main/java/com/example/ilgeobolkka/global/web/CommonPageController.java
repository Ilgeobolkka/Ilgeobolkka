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
        return placeholder(model, "도서 탐색", "도서 목록 화면을 준비하고 있습니다.");
    }

    @GetMapping("/books/{bookId}")
    String bookDetail(@PathVariable long bookId, Model model) {
        model.addAttribute("bookId", bookId);
        return placeholder(model, "도서 상세", "도서 상세 화면을 준비하고 있습니다.");
    }

    @GetMapping("/signup")
    String signup(Model model) {
        return placeholder(model, "회원가입", "회원가입 화면을 준비하고 있습니다.");
    }

    @GetMapping("/login")
    String login(Model model) {
        return placeholder(model, "로그인", "로그인 화면을 준비하고 있습니다.");
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
        model.addAttribute("inkPurchaseEnabled", inkPurchaseEnabled());
        return "pages/ink";
    }

    @GetMapping("/ownership-payments")
    String ownershipPayments(Model model) {
        return placeholder(model, "소장 결제 내역", "완료된 소장 결제 내역 화면을 준비하고 있습니다.");
    }

    @GetMapping("/library")
    String library(Model model) {
        return placeholder(model, "내 서재", "내 서재 화면을 준비하고 있습니다.");
    }

    private String placeholder(Model model, String pageTitle, String pageDescription) {
        model.addAttribute("pageTitle", pageTitle);
        model.addAttribute("pageDescription", pageDescription);
        return "pages/placeholder";
    }

    private boolean inkPurchaseEnabled() {
        return environment.acceptsProfiles(Profiles.of("!prod"))
                && environment.getProperty("portone.payment.enabled", Boolean.class, false);
    }
}
