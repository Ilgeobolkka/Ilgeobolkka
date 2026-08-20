package com.example.ilgeobolkka.airoute.repository;

import com.example.ilgeobolkka.airoute.entity.AiRouteCurrent;
import com.example.ilgeobolkka.airoute.entity.AiRouteCurrentId;
import java.time.Instant;
import java.util.Optional;
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
     * <p>{@code uk_ai_route_current_route} 가 이 문장에서 부딪히지 않는 근거는 경로가 한
     * {@code (readerId, bookId)} 에만 속한다는 것이다. 소유자가 맞는 값을 넘기면 PK 매칭과
     * {@code route_id} 유니크 매칭이 언제나 같은 행을 가리킨다.
     *
     * <p><b>그래서 호출자는 이 문장 앞에서 소유자를 반드시 확인한다.</b> 어긋난 값을 넘기면 MySQL 이 PK 가
     * 아니라 {@code route_id} 유니크 키로 매칭해, 그 경로를 이미 가리키던 <b>다른 독자의 행</b>을 예외 없이
     * 갱신할 수 있다. 복합 FK 는 갱신된 행의 조합이 맞으면 걸리지 않으므로 이 확인이 유일한 방어선이다.
     * 저장만 호출자였을 때는 방금 만든 경로라 이 조건이 저절로 성립했지만, 지금은 기존 경로를 넘기는
     * 호출자(현재 경로 지정과 삭제의 후속 선택)가 있다.
     *
     * <p>이 insert 는 복합 FK {@code fk_ai_route_current_route} 검사 때문에 부모 경로 행에 공유 잠금을
     * 잡는다. 그 잠금은 FK 가 참조하는 {@code uk_ai_reading_route_owner} 보조 인덱스 레코드에 걸리고,
     * 소유자 확인({@link AiReadingRouteRepository#findOwnedByIdForUpdate})은 PK 로 클러스터드 레코드에
     * 배타 잠금을 잡는다. 서로 다른 레코드라 부딪히지 않는다. 실제 MySQL 8.4 에서 확인했다 — 한 세션이
     * 경로를 PK 로 잠근 상태에서 다른 세션이 그 경로를 가리키는 현재 포인터를 넣어도 대기하지 않는다.
     *
     * <p><b>이 두 잠금이 같은 레코드가 되면 교착이 생긴다.</b> 삭제는 현재 포인터를 쥔 채 이 insert 로
     * 후속 경로의 공유 잠금을 기다리게 되고, 그 경로를 이미 배타 잠금한 지정 요청은 현재 포인터를 기다리기
     * 때문이다. 소유자 확인의 실행 계획이 {@code uk_ai_reading_route_owner} 로 바뀌거나 잠금 순서를
     * 손대는 후속 작업은 이 조합을 다시 재어 봐야 한다.
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

    /**
     * 삭제가 같은 독자·도서의 현재 경로를 잠그고 읽는다. 지우려는 경로가 현재 경로인지 판단하는 값이자,
     * 같은 도서의 삭제들을 한 줄로 세우는 잠금이다.
     *
     * <p>현재가 아닌 경로를 지울 때도 반드시 잠근다. 잠그지 않으면 현재가 아닌 경로를 지우는 요청과 현재
     * 경로를 지우는 요청이 겹쳤을 때, 뒤엣것이 아직 커밋되지 않은 앞엣것의 경로를 후속 현재 경로로
     * 고르고 그 뒤에 참조 무결성으로 터진다. 이 잠금이 있으면 둘 중 하나가 끝난 뒤에 다른 하나가 시작해
     * 그런 조합이 만들어지지 않는다.
     *
     * <p>행이 없을 때 gap lock 을 남기지 않는 것은 삭제가 {@code READ_COMMITTED} 로 열리기 때문이다.
     * 게다가 이 조회에 닿는 시점에는 이미 소유한 경로를 하나 잠근 뒤라 같은 독자·도서에 현재 경로 행이
     * 반드시 있다.
     *
     * <p>{@link AiRouteCurrent} 를 Entity 로 읽지 않고 native 로 {@code route_id} 만 꺼낸다. Entity 로
     * 읽으면 뒤따르는 bulk 삭제가 지운 행이 영속성 컨텍스트에 살아남는다. {@link #selectAsCurrent} 가
     * 같은 이유로 native 인 것과 짝을 맞춘다.
     */
    @Query(
            value =
                    """
                    SELECT route_id
                    FROM ai_route_current
                    WHERE reader_id = :readerId
                      AND book_id = :bookId
                    FOR UPDATE
                    """,
            nativeQuery = true)
    Optional<Long> findRouteIdForUpdate(
            @Param("readerId") long readerId, @Param("bookId") long bookId);

    /**
     * 현재 경로 포인터를 없앤다. 경로 행보다 먼저 지워야 {@code fk_ai_route_current_route} 가 걸리지
     * 않는다. 호출자는 지우려는 경로가 현재 경로일 때만 부른다.
     *
     * <p>{@code flushAutomatically} 는 삭제가 앞서 걸어 둔 생성의 {@code CONSUMED} 전이를 이 문장 앞에서
     * 내보낸다. {@code fk_ai_route_generation_saved_route} 가 생성의 {@code saved_route_id} 를 경로에
     * 묶고 있어, 그 전이가 나가기 전에 경로를 지우면 참조 무결성에 걸린다.
     */
    @Modifying(flushAutomatically = true)
    @Query(
            """
            DELETE FROM AiRouteCurrent current
            WHERE current.readerId = :readerId
              AND current.bookId = :bookId
            """)
    void clearCurrent(@Param("readerId") long readerId, @Param("bookId") long bookId);
}
