package com.example.ilgeobolkka.demo;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("!prod & (local | demo)")
class DemoDataSeedRunner implements ApplicationRunner {

    private final DemoDataSeeder demoDataSeeder;
    private final Environment environment;

    DemoDataSeedRunner(DemoDataSeeder demoDataSeeder, Environment environment) {
        this.demoDataSeeder = demoDataSeeder;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String demoValidationPassword =
                environment.getProperty("DEMO_VALIDATION_PASSWORD", "");
        if (demoValidationPassword.isBlank()) {
            return;
        }

        demoDataSeeder.seed(demoValidationPassword);
    }
}
