package com.example.ilgeobolkka.global.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationClockConfig {

    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }
}
