package com.example.ilgeobolkka.airoute.repository;

import com.example.ilgeobolkka.airoute.entity.AiReadingRoute;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * 현재 경로 지정·삭제·콘텐츠 제공·피드백이 대상 경로를 잠금 조회한다. 소유자 조건을 잠금 조회 자체에
     * 두어 다른 독자의 경로와 없는 경로가 같은 빈 결과가 되게 한다. 상세 조회와 같은 이유다.
     *
     * <p>경로 행을 잠그는 것이 경로 상태 변경의 직렬화 지점이다. 모든 변경이 여기를 먼저 지나므로, 한쪽이
     * 지우는 중인 경로를 다른 쪽이 현재 경로로 올리거나 진행·피드백을 남기는 조합이 만들어지지 않는다.
     *
     * <p>저장은 이 조회에 닿지 않는다. 저장이 만드는 경로는 그 transaction 이 방금 insert 한 행이라 다른
     * 요청이 잡고 있을 수 없다. 그래서 저장과 이 경로 사이에는 {@code ai_route_current} 말고 겹치는 잠금이
     * 없고, 두 경로 모두 생성 행을 현재 경로보다 먼저 잠가 대기 고리가 생기지 않는다.
     *
     * <p>존재 확인 없이 잠그는 예외다. 지정과 삭제가 모두 {@code READ_COMMITTED} 로 열리고 이 격리 수준의
     * InnoDB 는 없는 식별자를 잠금 조회해도 gap lock 을 남기지 않는다. 격리 수준을 올리면 없는 경로를 찍은
     * 요청이 남긴 gap 과 저장의 insert 가 같은 자리에서 만나므로, 이 조회를 부르는 경로의 격리 수준을
     * 바꾸는 후속 작업은 {@link AiRouteGenerationRepository#findOwnedNotExpiredForUpdate} 와 같은 근거를
     * 다시 확인해야 한다.
     *
     * <p><b>이 잠금은 PK 로 클러스터드 레코드에만 걸려야 한다.</b> 실행 계획이
     * {@code uk_ai_reading_route_owner} 로 바뀌면, 삭제가 현재 포인터를 쥔 채 내는 후속 경로 insert 의 FK
     * 공유 잠금과 같은 레코드에서 만나 지정×삭제가 교착한다. 근거는
     * {@link AiRouteCurrentRepository#selectAsCurrent} 에 적어 두었다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT route
            FROM AiReadingRoute route
            WHERE route.readerId = :readerId
              AND route.id = :routeId
            """)
    Optional<AiReadingRoute> findOwnedByIdForUpdate(
            @Param("readerId") long readerId,
            @Param("routeId") long routeId);

    /**
     * 현재 경로를 삭제한 뒤 그 자리를 이을 경로를 정본이 정한 {@code createdAt DESC, id DESC} 순으로 읽는다.
     * 호출자가 한 건만 요청하므로 결과는 비었거나 한 건이다.
     *
     * <p>목록 조회와 보조 정렬을 맞춘 이유는 같다. 저장이 한 transaction 에서 여러 경로를 만들 수 있어
     * {@code createdAt} 이 같은 행이 실제로 생기고, 그때 어느 쪽이 후속 현재 경로인지가 정해져야 한다.
     *
     * <p>삭제한 경로를 조건에서 빼지 않는다. 호출자가 경로 행을 지우고 flush 한 뒤에 부르므로 이미 결과에
     * 없다. 조건을 하나 더 두면 삭제가 실제로 반영됐는지와 무관하게 통과해, flush 를 빠뜨린 순간을 이
     * 조회가 덮어 버린다.
     */
    @Query(
            """
            SELECT route.id
            FROM AiReadingRoute route
            WHERE route.readerId = :readerId
              AND route.bookId = :bookId
            ORDER BY route.createdAt DESC, route.id DESC
            """)
    List<Long> findRouteIdsByReaderIdAndBookId(
            @Param("readerId") long readerId,
            @Param("bookId") long bookId,
            Pageable pageable);
}
