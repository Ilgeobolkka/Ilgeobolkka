package com.example.ilgeobolkka.global.config;

import java.util.Objects;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.util.StringUtils;

public final class ProdDataSourceEnvironmentValidator
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String PROD_PROFILE = "prod";
    private static final String LOCAL_PROFILE = "local";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        if (!environment.acceptsProfiles(Profiles.of(PROD_PROFILE))) {
            return;
        }
        if (environment.acceptsProfiles(Profiles.of(LOCAL_PROFILE))) {
            throw new IllegalStateException(
                    "Profiles 'prod' and 'local' must not be active at the same time");
        }

        String dbUrl = requireEnvironmentVariable(environment, "DB_URL");
        String dbUsername = requireEnvironmentVariable(environment, "DB_USERNAME");
        String dbPassword = requireEnvironmentVariable(environment, "DB_PASSWORD");

        requireDataSourcePropertyMatches(
                environment, "spring.datasource.url", "DB_URL", dbUrl);
        requireDataSourcePropertyMatches(
                environment, "spring.datasource.username", "DB_USERNAME", dbUsername);
        requireDataSourcePropertyMatches(
                environment, "spring.datasource.password", "DB_PASSWORD", dbPassword);
    }

    private String requireEnvironmentVariable(
            ConfigurableEnvironment environment, String environmentVariable) {
        String value = null;
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (propertySource instanceof SystemEnvironmentPropertySource) {
                Object rawValue = propertySource.getProperty(environmentVariable);
                if (rawValue != null) {
                    value = rawValue.toString();
                    break;
                }
            }
        }
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    "Required environment variable '"
                            + environmentVariable
                            + "' must be set and not blank");
        }
        return value;
    }

    private void requireDataSourcePropertyMatches(
            ConfigurableEnvironment environment,
            String dataSourceProperty,
            String environmentVariable,
            String environmentValue) {
        if (!Objects.equals(
                environment.getProperty(dataSourceProperty),
                environmentValue)) {
            throw new IllegalStateException(
                    "Spring datasource property '"
                            + dataSourceProperty
                            + "' must match environment variable '"
                            + environmentVariable
                            + "' in the prod profile");
        }
    }
}
