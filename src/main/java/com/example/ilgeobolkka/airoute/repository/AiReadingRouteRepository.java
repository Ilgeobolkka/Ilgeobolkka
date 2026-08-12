package com.example.ilgeobolkka.airoute.repository;

import com.example.ilgeobolkka.airoute.entity.AiReadingRoute;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiReadingRouteRepository extends JpaRepository<AiReadingRoute, Long> {

    long countByReaderId(long readerId);

    /**
     * 인증 계정의 저장 경로만 {@code createdAt DESC, id DESC}로 조회한다.
     *
     * <p>보조 정렬 {@code id DESC}가 없으면 같은 {@code createdAt}을 가진 행의 페이지 간 순서가 정해지지
     * 않아 한 행이 두 페이지에 나오거나 아예 빠질 수 있다. 저장이 같은 트랜잭션에서 여러 건을 만들 수 있으므로
     * 동시각은 실제로 생긴다.
     *
     * <p>현재 경로 판정은 {@code ai_route_current}를 left join 해서 가져온다. 목록 한 페이지의 경로가 서로
     * 다른 도서일 수 있어 도서별 현재 경로를 따로 조회하면 도서 수만큼 질의가 늘기 때문이다.
     */
    @Query(
            value =
                    """
                    SELECT route.id AS routeId,
                           route.bookId AS bookId,
                           book.title AS bookTitle,
                           route.normalizedPurpose AS purpose,
                           currentRoute.routeId AS currentRouteId,
                           route.createdAt AS createdAt,
                           route.completedAt AS completedAt,
                           route.feedback AS rating
                    FROM AiReadingRoute route
                    JOIN route.book book
                    LEFT JOIN AiRouteCurrent currentRoute ON currentRoute.routeId = route.id
                    WHERE route.readerId = :readerId
                    ORDER BY route.createdAt DESC, route.id DESC
                    """,
            countQuery =
                    """
                    SELECT COUNT(route.id)
                    FROM AiReadingRoute route
                    WHERE route.readerId = :readerId
                    """)
    Page<AiRouteSummaryProjection> findSummariesByReaderId(
            @Param("readerId") long readerId,
            Pageable pageable);

    /**
     * 인증 계정과 경로 식별자를 함께 조건에 넣어 상세를 조회한다.
     *
     * <p>먼저 경로를 찾고 소유자를 비교하면 존재 여부가 응답 시점·분기로 새어 나갈 수 있다. 조건을 한 번에
     * 넣어 다른 독자의 경로와 없는 경로가 같은 빈 결과가 되게 한다.
     */
    @Query(
            """
            SELECT route.id AS routeId,
                   route.bookId AS bookId,
                   book.title AS bookTitle,
                   route.normalizedPurpose AS purpose,
                   currentRoute.routeId AS currentRouteId,
                   route.createdAt AS createdAt,
                   route.completedAt AS completedAt,
                   route.feedback AS rating
            FROM AiReadingRoute route
            JOIN route.book book
            LEFT JOIN AiRouteCurrent currentRoute ON currentRoute.routeId = route.id
            WHERE route.readerId = :readerId
              AND route.id = :routeId
            """)
    Optional<AiRouteSummaryProjection> findSummaryByReaderIdAndId(
            @Param("readerId") long readerId,
            @Param("routeId") long routeId);
}
