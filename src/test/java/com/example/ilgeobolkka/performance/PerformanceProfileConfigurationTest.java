package com.example.ilgeobolkka.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class PerformanceProfileConfigurationTest {

    @Test
    void management_포트와_외부_서비스_차단이_고정된다() throws IOException {
        PropertySource<?> properties = new YamlPropertySourceLoader()
                .load("performance", new ClassPathResource("application-performance.yaml"))
                .getFirst();

        assertEquals("127.0.0.1", properties.getProperty("server.address"));
        assertEquals(8080, properties.getProperty("server.port"));
        assertEquals("127.0.0.1", properties.getProperty("management.server.address"));
        assertEquals(8081, properties.getProperty("management.server.port"));
        assertEquals(
                "health,prometheus",
                properties.getProperty("management.endpoints.web.exposure.include"));
        assertEquals(false, properties.getProperty("portone.payment.enabled"));
        assertEquals(false, properties.getProperty("ai-route.enabled"));
    }
}
