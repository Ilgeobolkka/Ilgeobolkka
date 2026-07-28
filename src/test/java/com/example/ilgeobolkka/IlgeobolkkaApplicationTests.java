package com.example.ilgeobolkka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

// 운영 기준값인 ddl-auto=validate(ADR-0005)를 그대로 적용해 기동하는 가드 테스트.
// 테스트 전용 엔티티는 com.example.testfixture.* 로 격리되어 기본 스캔에 잡히지 않으므로,
// 이 컨텍스트는 실제 애플리케이션 설정(Flyway 적용 + validate) 그대로 부팅된다.
// validate 설정을 삭제·변경하면 이 테스트가 실패해 회귀를 잡는다.
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
class IlgeobolkkaApplicationTests {

    private final Environment environment;
    private final ApplicationContext applicationContext;

    @Autowired
    IlgeobolkkaApplicationTests(
            Environment environment, ApplicationContext applicationContext) {
        this.environment = environment;
        this.applicationContext = applicationContext;
    }

    @Test
    void contextLoads() {
    }

    @Test
    void 로그인_세션의_비활성_만료_시간은_2시간이다() {
        Duration timeout =
                Binder.get(environment)
                        .bind("server.servlet.session.timeout", Duration.class)
                        .orElseThrow(() -> new IllegalStateException("세션 만료 설정이 없습니다."));

        assertEquals(Duration.ofHours(2), timeout);
    }

    @Test
    void 뷰_렌더링_시점에는_JPA_세션을_열어두지_않는다() {
        boolean openInView =
                Binder.get(environment)
                        .bind("spring.jpa.open-in-view", Boolean.class)
                        .orElseThrow(() -> new IllegalStateException("OSIV 설정이 없습니다."));

        assertFalse(openInView);
    }

    @Test
    void 임시_비밀번호를_로그에_남기는_기본_사용자를_생성하지_않는다() {
        assertTrue(applicationContext.getBeansOfType(InMemoryUserDetailsManager.class).isEmpty());
    }

    @Test
    void DB_접속_정보를_출력하는_라이브러리_로그는_WARN으로_제한한다() {
        assertEquals("WARN", environment.getProperty("logging.level.org.flywaydb.core"));
        assertEquals("WARN", environment.getProperty("logging.level.org.hibernate.orm.connections.pooling"));
    }
}
