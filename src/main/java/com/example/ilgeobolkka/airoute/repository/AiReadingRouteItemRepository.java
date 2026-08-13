package com.example.ilgeobolkka.airoute.repository;

import com.example.ilgeobolkka.airoute.entity.AiReadingRouteItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiReadingRouteItemRepository extends JpaRepository<AiReadingRouteItem, Long> {

    /**
     * 경로 항목을 저장한 {@code position} 오름차순으로 조회한다.
     *
     * <p>소유자 확인은 호출 전에 경로 단위로 끝낸다. 항목은 경로에 종속이라 경로를 소유자 조건으로 찾은 뒤에만
     * 이 질의에 닿는다.
     *
     * <p>가이드 주제와 예상 시간을 페이지에서 함께 꺼내 항목 수만큼 페이지를 다시 읽지 않는다. 응답 조립이
     * lazy 연관에 기대면 트랜잭션 밖에서 초기화가 일어나므로 필요한 컬럼을 여기서 다 가져온다.
     */
    @Query(
            """
            SELECT item.position AS position,
                   page.id AS bookPageId,
                   page.pageNumber AS pageNumber,
                   item.relevance AS relevance,
                   item.prerequisite AS prerequisite,
                   item.role AS role,
                   item.openedAt AS openedAt,
                   page.aiPublicGuideTopic AS guideTopic,
                   page.estimatedReadingSeconds AS estimatedReadingSeconds
            FROM AiReadingRouteItem item
            JOIN item.bookPage page
            WHERE item.routeId = :routeId
            ORDER BY item.position ASC
            """)
    List<AiRouteItemProjection> findItemsByRouteId(@Param("routeId") long routeId);

    /**
     * 경로 삭제가 항목을 지운다. 항목이 들고 있던 열람 시각도 여기서 함께 사라진다. 경로 행보다 먼저
     * 지워야 {@code fk_ai_reading_route_item_route_book} 이 걸리지 않는다.
     *
     * <p>소유자 확인은 호출 전에 경로 단위로 끝낸다. 조회와 같은 이유로, 항목은 소유자 조건으로 찾은
     * 경로를 통해서만 이 문장에 닿는다.
     *
     * <p>{@code clearAutomatically} 는 켜지 않는다. 삭제 transaction 은 항목을 Entity 로 읽지 않고,
     * 켜면 잠금 조회로 들고 있던 경로 Entity 까지 떨어져 나가 뒤따르는 삭제가 merge 를 거치게 된다.
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM AiReadingRouteItem item WHERE item.routeId = :routeId")
    int deleteByRouteId(@Param("routeId") long routeId);
}
