package com.example.ilgeobolkka;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// 테스트 전용 QuerydslTestEntity의 테이블은 QuerydslMySqlIntegrationTest가 raw SQL로 직접
// 생성·삭제한다(conventions.md Repository와 스키마 절과 무관한 테스트 픽스처). 이 스모크 테스트는
// 그 테이블 생성 없이 컨텍스트만 띄우므로, 운영 기준값인 ddl-auto=validate를 그대로 적용하면
// 스키마 검증이 실패한다. 이 테스트에서만 검증을 끈다.
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
class IlgeobolkkaApplicationTests {

    @Test
    void contextLoads() {
    }

}
