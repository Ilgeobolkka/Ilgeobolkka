package com.example.ilgeobolkka.global.smoke;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SmokeController {

    @GetMapping("/api/smoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void smoke() {
    }
}
