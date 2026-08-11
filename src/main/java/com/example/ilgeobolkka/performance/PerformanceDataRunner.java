package com.example.ilgeobolkka.performance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("performance-seed")
final class PerformanceDataRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PerformanceDataRunner.class);

    private final PerformanceDataSeeder dataSeeder;
    private final Environment environment;

    PerformanceDataRunner(PerformanceDataSeeder dataSeeder, Environment environment) {
        this.dataSeeder = dataSeeder;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String rawPassword = environment.getRequiredProperty("DEMO_VALIDATION_PASSWORD");
        PerformanceDataSeeder.SeedSummary summary = dataSeeder.resetMvp(rawPassword);
        log.info(
                "성능 mvp 데이터를 준비했습니다: rowCounts={}, balanceMismatchCount={}",
                summary.rowCounts(),
                summary.balanceMismatchCount());
    }
}
