package com.example.ilgeobolkka.global.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;

public final class ProdDataSourceEnvironmentValidator
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String PROD_PROFILE = "prod";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        if (!environment.acceptsProfiles(Profiles.of(PROD_PROFILE))) {
            return;
        }

        requireEnvironmentVariable(environment, "DB_URL");
        requireEnvironmentVariable(environment, "DB_USERNAME");
        requireEnvironmentVariable(environment, "DB_PASSWORD");
    }

    private void requireEnvironmentVariable(
            ConfigurableEnvironment environment, String environmentVariable) {
        String value = environment.getProperty(environmentVariable);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    "Required environment variable '"
                            + environmentVariable
                            + "' must be set and not blank");
        }
    }
}
