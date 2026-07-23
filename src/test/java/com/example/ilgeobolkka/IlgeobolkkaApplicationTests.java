package com.example.ilgeobolkka;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

// 운영 기준값인 ddl-auto=validate(ADR-0010)를 그대로 적용해 기동하는 가드 테스트.
// 테스트 전용 엔티티는 com.example.testfixture.* 로 격리되어 기본 스캔에 잡히지 않으므로,
// 이 컨텍스트는 실제 애플리케이션 설정(Flyway 적용 + validate) 그대로 부팅된다.
// validate 설정을 삭제·변경하면 이 테스트가 실패해 회귀를 잡는다.
@SpringBootTest
class IlgeobolkkaApplicationTests {

    private final Environment environment;

    @Autowired
    IlgeobolkkaApplicationTests(Environment environment) {
        this.environment = environment;
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
}
