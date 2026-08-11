package com.example.ilgeobolkka.performance;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("performance")
final class PerformanceExternalServiceGuard implements ApplicationRunner {

    private final Environment environment;

    PerformanceExternalServiceGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        requireExternalServicesDisabled(
                environment.getProperty("portone.payment.enabled", Boolean.class, false),
                environment.getProperty("ai-route.enabled", Boolean.class, false));
    }

    static void requireExternalServicesDisabled(boolean portOneEnabled, boolean aiRouteEnabled) {
        if (portOneEnabled || aiRouteEnabled) {
            throw new IllegalStateException(
                    "performance 프로필에서는 PortOne과 OpenAI 기능을 활성화할 수 없습니다.");
        }
    }
}
