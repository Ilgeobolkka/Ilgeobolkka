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

        requireDataSourceProperty(environment, "spring.datasource.url", "DB_URL");
        requireDataSourceProperty(environment, "spring.datasource.username", "DB_USERNAME");
        requireDataSourceProperty(environment, "spring.datasource.password", "DB_PASSWORD");
    }

    private void requireDataSourceProperty(
            ConfigurableEnvironment environment,
            String propertyName,
            String environmentVariable) {
        String value = environment.getRequiredProperty(propertyName);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    "Required environment variable '"
                            + environmentVariable
                            + "' must not be blank");
        }
    }
}
