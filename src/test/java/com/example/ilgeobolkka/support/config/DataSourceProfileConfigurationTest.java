package com.example.ilgeobolkka.support.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

class DataSourceProfileConfigurationTest {

    private static final String DB_URL = "jdbc:mysql://db.example:3306/ilgeobolkka";
    private static final String DB_USERNAME = "app-user";
    private static final String DB_PASSWORD = "app-password";

    @Test
    void 기본_프로파일은_로컬이고_공통_설정에_DataSource가_없다() throws IOException {
        PropertySourcesPropertyResolver resolver = resolverFor("application.yaml");

        assertAll(
                () -> assertEquals("local", resolver.getProperty("spring.profiles.default")),
                () -> assertNull(resolver.getProperty("spring.datasource.url")),
                () -> assertNull(resolver.getProperty("spring.datasource.username")),
                () -> assertNull(resolver.getProperty("spring.datasource.password")));
    }

    @Test
    void 로컬_프로파일은_dotenv_import와_DataSource_환경변수_계약을_정의한다()
            throws IOException {
        PropertySourcesPropertyResolver resolver = resolverFor("application-local.yml");

        assertAll(
                () ->
                        assertEquals(
                                "optional:file:.env[.properties]",
                                resolver.getProperty("spring.config.import")),
                () -> assertDataSourceProperties(resolver));
    }

    @Test
    void 운영_프로파일은_dotenv_import_없이_DataSource_환경변수_계약을_정의한다()
            throws IOException {
        PropertySourcesPropertyResolver resolver = resolverFor("application-prod.yml");

        assertAll(
                () -> assertNull(resolver.getProperty("spring.config.import")),
                () -> assertDataSourceProperties(resolver));
    }

    private PropertySourcesPropertyResolver resolverFor(String resourcePath) throws IOException {
        Map<String, Object> environmentVariables =
                Map.of(
                        "DB_URL", DB_URL,
                        "DB_USERNAME", DB_USERNAME,
                        "DB_PASSWORD", DB_PASSWORD);
        MutablePropertySources propertySources = new MutablePropertySources();
        propertySources.addFirst(new MapPropertySource("test-environment", environmentVariables));

        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        loader.load(resourcePath, new ClassPathResource(resourcePath))
                .forEach(propertySources::addLast);

        return new PropertySourcesPropertyResolver(propertySources);
    }

    private void assertDataSourceProperties(PropertySourcesPropertyResolver resolver) {
        assertAll(
                () -> assertEquals(DB_URL, resolver.getProperty("spring.datasource.url")),
                () -> assertEquals(DB_USERNAME, resolver.getProperty("spring.datasource.username")),
                () -> assertEquals(DB_PASSWORD, resolver.getProperty("spring.datasource.password")));
    }
}
