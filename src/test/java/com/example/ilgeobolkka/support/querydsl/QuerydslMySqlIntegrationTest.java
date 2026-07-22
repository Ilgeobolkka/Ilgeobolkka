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

@SpringBootTest
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
