package com.example.testfixture.querydsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.example.ilgeobolkka.IlgeobolkkaApplication;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

// 테스트 전용 QuerydslTestEntity는 애플리케이션 스캔 범위(com.example.ilgeobolkka) 밖의
// com.example.testfixture.querydsl 에 있어 기본 컨텍스트에는 등록되지 않는다. 이 통합 테스트에서만
// @EntityScan 으로 등록한다(그래서 IlgeobolkkaApplicationTests/CoreDomainSchemaMigrationTest 는
// 운영 기준값 ddl-auto=validate 로 기동한다).
// 이 엔티티의 테이블은 Flyway가 아니라 이 클래스가 @BeforeEach에서 raw SQL로 직접 만들므로,
// 컨텍스트 로딩 시점에는 테이블이 없다. validate면 EntityManagerFactory 생성이 실패하므로 여기서만 끈다.
// 이 테스트는 애플리케이션 패키지(com.example.ilgeobolkka) 밖에 있어 @SpringBootTest 가
// @SpringBootConfiguration 을 자동으로 찾지 못하므로 메인 설정을 classes 로 명시한다.
@SpringBootTest(
        classes = IlgeobolkkaApplication.class,
        properties = "spring.jpa.hibernate.ddl-auto=none")
@EntityScan(basePackageClasses = QuerydslTestEntity.class)
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
