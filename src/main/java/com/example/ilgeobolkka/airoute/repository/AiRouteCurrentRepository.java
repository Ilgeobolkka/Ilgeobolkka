package com.example.ilgeobolkka.airoute.repository;

import com.example.ilgeobolkka.airoute.entity.AiRouteCurrent;
import com.example.ilgeobolkka.airoute.entity.AiRouteCurrentId;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiRouteCurrentRepository
        extends JpaRepository<AiRouteCurrent, AiRouteCurrentId> {

    /**
     * 같은 독자·도서의 현재 경로를 한 문장으로 지정한다. 행이 없으면 만들고 있으면 가리키는 경로를 바꾼다.
     *
     * <p>조회로 잠그고 없으면 insert 하는 방식을 쓰지 않는다. 저장은 {@code READ_COMMITTED} 로 열리고,
     * 이 격리 수준에서 InnoDB 는 없는 행을 잠금 조회해도 아무 잠금을 남기지 않는다(gap lock 은 중복 키·외래
     * 키 검사에만 쓴다). 그래서 같은 독자·도서를 동시에 저장한 두 요청이 모두 "행 없음"을 보고 둘 다 insert
     * 하고, 뒤에 온 쪽이 중복 키 오류로 실패한다. 첫 저장이 곧 없는 행이라 가장 흔한 경로가 최악 조건이 된다.
     * upsert 는 판단과 기록이 한 문장이라 그 틈이 없고, 같은 PK 를 동시에 노린 두 요청은 뒤에 온 쪽이 앞의
     * commit 을 기다렸다가 덮어써서 "어떤 완료 시점에도 현재 경로는 최대 하나"가 그대로 성립한다.
     *
     * <p>{@code uk_ai_route_current_route} 는 이 문장에서 부딪히지 않는다. {@code routeId} 는 같은
     * transaction 이 방금 만든 경로라 다른 행이 이미 가리킬 수 없다.
     *
     * <p>native 인 이유는 JPA 에 upsert 가 없어서다. 영속성 컨텍스트를 거치지 않으므로 이 문장 뒤에
     * {@link AiRouteCurrent} 를 같은 transaction 에서 Entity 로 읽지 않는다.
     */
    @Modifying(flushAutomatically = true)
    @Query(
            value =
                    """
                    INSERT INTO ai_route_current (reader_id, book_id, route_id, updated_at)
                    VALUES (:readerId, :bookId, :routeId, :updatedAt) AS incoming
                    ON DUPLICATE KEY UPDATE
                        route_id = incoming.route_id,
                        updated_at = incoming.updated_at
                    """,
            nativeQuery = true)
    void selectAsCurrent(
            @Param("readerId") long readerId,
            @Param("bookId") long bookId,
            @Param("routeId") long routeId,
            @Param("updatedAt") Instant updatedAt);
}
