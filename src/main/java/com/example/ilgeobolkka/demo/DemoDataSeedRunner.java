package com.example.ilgeobolkka.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "demo"})
class DemoDataSeedRunner implements ApplicationRunner {

    private final DemoDataSeeder demoDataSeeder;
    private final String demoValidationPassword;

    DemoDataSeedRunner(
            DemoDataSeeder demoDataSeeder,
            @Value("${DEMO_VALIDATION_PASSWORD:}") String demoValidationPassword) {
        this.demoDataSeeder = demoDataSeeder;
        this.demoValidationPassword = demoValidationPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (demoValidationPassword.isBlank()) {
            return;
        }

        demoDataSeeder.seed(demoValidationPassword);
    }
}
