package com.example.ilgeobolkka.support.querydsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

// 컨텍스트 로딩 시점에는 이 클래스가 @BeforeEach에서 만드는 테이블이 아직 없어
// ddl-auto=validate(운영 기준값)를 그대로 적용하면 EntityManagerFactory 생성이 실패한다.
// 이 테스트 전용 엔티티의 테이블은 Flyway가 아니라 이 클래스가 raw SQL로 직접 관리하므로
// 이 테스트에서만 검증을 끈다.
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
class QuerydslMySqlIntegrationTest {

    private final JdbcTemplate jdbcTemplate;
    private final EntityManagerFactory entityManagerFactory;

    @Autowired
    QuerydslMySqlIntegrationTest(
            JdbcTemplate jdbcTemplate, EntityManagerFactory entityManagerFactory) {
        this.jdbcTemplate = jdbcTemplate;
        this.entityManagerFactory = entityManagerFactory;
    }

    @BeforeEach
    void 테스트_테이블을_생성한다() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS querydsl_test_entity");
        jdbcTemplate.execute(
                """
                CREATE TABLE querydsl_test_entity (
                    id BIGINT NOT NULL PRIMARY KEY,
                    title VARCHAR(255)
                )
                """);
    }

    @AfterEach
    void 테스트_테이블을_삭제한다() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS querydsl_test_entity");
    }

    @Test
    void MySQL에서_QueryDSL_JPA_조회가_동작한다() {
        EntityManager entityManager = entityManagerFactory.createEntityManager();

        try {
            entityManager.getTransaction().begin();

            QuerydslTestEntity entity = new QuerydslTestEntity();
            entity.id = 1L;
            entity.title = "타입 안전 쿼리";
            entityManager.persist(entity);
            entityManager.flush();
            entityManager.clear();

            QQuerydslTestEntity querydslTestEntity = QQuerydslTestEntity.querydslTestEntity;
            QuerydslTestEntity found =
                    new JPAQueryFactory(entityManager)
                            .selectFrom(querydslTestEntity)
                            .where(querydslTestEntity.title.eq("타입 안전 쿼리"))
                            .fetchOne();

            assertNotNull(found);
            assertEquals(1L, found.id);
            assertEquals("타입 안전 쿼리", found.title);

            entityManager.getTransaction().commit();
        } finally {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            entityManager.close();
        }
    }
}
