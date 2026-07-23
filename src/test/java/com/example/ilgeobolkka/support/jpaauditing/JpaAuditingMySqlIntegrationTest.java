package com.example.ilgeobolkka.support.jpaauditing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

// QuerydslMySqlIntegrationTest와 같은 이유로, 이 테스트 전용 엔티티의 테이블은 Flyway가 아니라
// 이 클래스가 raw SQL로 직접 관리하므로 ddl-auto=validate 검증을 끈다.
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
class JpaAuditingMySqlIntegrationTest {

    private final JdbcTemplate jdbcTemplate;
    private final EntityManagerFactory entityManagerFactory;

    @Autowired
    JpaAuditingMySqlIntegrationTest(
            JdbcTemplate jdbcTemplate, EntityManagerFactory entityManagerFactory) {
        this.jdbcTemplate = jdbcTemplate;
        this.entityManagerFactory = entityManagerFactory;
    }

    @BeforeEach
    void 테스트_테이블을_생성한다() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS jpa_auditing_test_entity");
        jdbcTemplate.execute(
                """
                CREATE TABLE jpa_auditing_test_entity (
                    id BIGINT NOT NULL PRIMARY KEY,
                    title VARCHAR(255),
                    created_at DATETIME(6),
                    updated_at DATETIME(6)
                )
                """);
    }

    @AfterEach
    void 테스트_테이블을_삭제한다() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS jpa_auditing_test_entity");
    }

    @Test
    void 엔티티를_저장하면_생성일시와_수정일시가_자동으로_채워진다() {
        EntityManager entityManager = entityManagerFactory.createEntityManager();

        try {
            entityManager.getTransaction().begin();

            JpaAuditingTestEntity entity = new JpaAuditingTestEntity();
            entity.id = 1L;
            entity.title = "감사 대상";
            entityManager.persist(entity);
            entityManager.flush();
            entityManager.clear();

            entityManager.getTransaction().commit();

            JpaAuditingTestEntity found = entityManager.find(JpaAuditingTestEntity.class, 1L);

            assertNotNull(found.createdAt);
            assertNotNull(found.updatedAt);
        } finally {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            entityManager.close();
        }
    }

    @Test
    void 저장된_엔티티를_수정하면_수정일시만_갱신되고_생성일시는_변하지_않는다() throws InterruptedException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();

        try {
            entityManager.getTransaction().begin();
            JpaAuditingTestEntity entity = new JpaAuditingTestEntity();
            entity.id = 2L;
            entity.title = "최초 제목";
            entityManager.persist(entity);
            entityManager.getTransaction().commit();
            entityManager.clear();

            JpaAuditingTestEntity saved = entityManager.find(JpaAuditingTestEntity.class, 2L);
            Instant createdAtAfterInsert = saved.createdAt;
            Instant updatedAtAfterInsert = saved.updatedAt;

            // MySQL DATETIME(6)의 마이크로초 해상도 안에서도 변화가 보이도록 시간 간격을 둔다.
            Thread.sleep(10);

            entityManager.getTransaction().begin();
            JpaAuditingTestEntity toUpdate = entityManager.find(JpaAuditingTestEntity.class, 2L);
            toUpdate.title = "수정된 제목";
            entityManager.getTransaction().commit();
            entityManager.clear();

            JpaAuditingTestEntity updated = entityManager.find(JpaAuditingTestEntity.class, 2L);

            assertEquals(createdAtAfterInsert, updated.createdAt);
            assertTrue(updated.updatedAt.isAfter(updatedAtAfterInsert));
        } finally {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            entityManager.close();
        }
    }
}
