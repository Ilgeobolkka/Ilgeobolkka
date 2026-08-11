package com.example.ilgeobolkka.airoute.repository;

import com.example.ilgeobolkka.airoute.entity.AiRouteGeneration;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiRouteGenerationRepository extends JpaRepository<AiRouteGeneration, UUID> {

    /**
     * 잠그지 않고 존재만 확인한다. 아래 잠금 조회를 부르기 전에 반드시 먼저 부른다.
     *
     * <p>엔티티를 적재하지 않는 조회여야 한다. 적재하면 뒤따르는 잠금 조회가 영속성 컨텍스트에 이미
     * 있는 인스턴스를 그대로 돌려주어, 잠금은 잡았는데 값은 잠그기 전 것을 읽는 상태가 된다.
     */
    boolean existsByReaderIdAndIdempotencyKey(long readerId, UUID idempotencyKey);

    /**
     * 멱등 키로 기존 생성을 잠금 조회한다. {@code readerId}를 조건에 두는 이유는 두 가지다. 남의 키를
     * 찍어 남의 생성 상태를 읽는 경로를 막고, {@code uk_ai_route_generation_reader_idempotency}와 같은
     * 열 순서를 써서 인덱스를 그대로 탄다.
     *
     * <p>행을 잠그는 것은 G06이 같은 행의 상태를 옮기는 중에 반쯤 바뀐 상태를 읽지 않기 위해서다.
     *
     * <p><b>뒤에 insert 할 수 있는 경로에서는 {@link #existsByReaderIdAndIdempotencyKey}가 참일 때만
     * 부른다.</b> 없는 행에 잠금을 걸면 InnoDB가 그 자리에 gap lock을 남기고, 그 gap 안에 insert하려는
     * 다른 요청과 교착한다. 인덱스가 비어 있을수록 두 요청이 같은 gap에 떨어질 확률이 높아 초기 운영과
     * 테스트가 최악 조건이다.
     *
     * <p>예외는 잠금을 잡고 아무것도 기다리지 않은 채 돌아가는 경로다. 대기 고리가 만들어지지 않아
     * gap lock이 교착으로 이어지지 않는다. 그런 곳에서는 존재 확인을 건너뛰고 바로 부른다. 존재 확인은
     * 일반 조회라 transaction 의 read view 에 묶여 있어서, 그 view 가 만들어진 뒤에 commit 된 행을
     * 놓치기 때문이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT generation
            FROM AiRouteGeneration generation
            WHERE generation.readerId = :readerId
              AND generation.idempotencyKey = :idempotencyKey
            """)
    Optional<AiRouteGeneration> findByReaderIdAndIdempotencyKeyForUpdate(
            @Param("readerId") long readerId, @Param("idempotencyKey") UUID idempotencyKey);

    /**
     * 소유자의 아직 유효한 생성만 돌려준다. 다른 독자의 식별자와 만료한 식별자를 같은 빈 결과로 만들어,
     * 존재 여부가 응답에서 갈리지 않게 한다.
     *
     * <p>만료 판정을 조건에 넣는 이유는 cleanup 실행 여부에 기대지 않기 위해서다. 정리 배치가 아직 안
     * 돌았어도 만료 시각을 지난 행은 여기서 보이지 않는다.
     *
     * <p>경계는 {@code now < expiresAt} 이 유효다. {@code expiresAt} 그 시각부터 만료이며
     * {@code PageRental#isActive} 와 같은 관례다. {@code GENERATING} 은 아직 만료 시각이 없어
     * {@code NULL} 이고 유효로 본다.
     */
    @Query(
            """
            SELECT generation
            FROM AiRouteGeneration generation
            WHERE generation.generationId = :generationId
              AND generation.readerId = :readerId
              AND (generation.expiresAt IS NULL OR generation.expiresAt > :now)
            """)
    Optional<AiRouteGeneration> findOwnedNotExpired(
            @Param("generationId") UUID generationId,
            @Param("readerId") long readerId,
            @Param("now") Instant now);

    /**
     * 상태를 옮기려고 잠금 조회한다. 전이는 이미 만들어진 생성에만 일어나므로
     * {@link #existsByReaderIdAndIdempotencyKey} 같은 존재 확인을 앞에 두지 않는다. 없는 행은 정상 경로가
     * 아니라 호출자의 잘못이고, 기본 키 조회라 없을 때 잡히는 gap 도 임의의 UUID 자리 하나뿐이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT generation
            FROM AiRouteGeneration generation
            WHERE generation.generationId = :generationId
            """)
    Optional<AiRouteGeneration> findByGenerationIdForUpdate(
            @Param("generationId") UUID generationId);

    /**
     * 정리 대상 식별자. 만료 시각을 지난 행이며 경계는 조회·저장 거부와 같은 {@code now >= expiresAt} 이다.
     *
     * <p>{@code expiresAt} 이 {@code null} 인 {@code GENERATING} 은 대상이 아니다. 중단된 생성은 먼저
     * 복구가 {@code FAILED} 로 바꾸면서 만료 시각을 매기고, 그다음 정리 대상이 된다.
     */
    @Query(
            """
            SELECT generation.generationId
            FROM AiRouteGeneration generation
            WHERE generation.expiresAt IS NOT NULL
              AND generation.expiresAt <= :now
            """)
    List<UUID> findExpiredGenerationIds(@Param("now") Instant now);

    /**
     * 전체 요청 제한을 지나도록 {@code GENERATING} 에 머문 생성. 생성 중 서버가 내려갔거나 호출자가
     * 완료를 남기지 못한 경우다.
     *
     * <p>여기서 나온 뒤 잠글 때까지 사이에 호출자가 정상 완료할 수 있으므로, 복구는 잠근 다음 상태를 다시
     * 보고 그때도 {@code GENERATING} 인 것만 바꾼다.
     *
     * <p>경계는 배제다. PRD 가 제한 시간을 "넘으면" 실패로 규정하므로 나이가 정확히 제한 시간인 생성은
     * 아직 대상이 아니다. "만료 시각부터" 거부하는 {@link #findExpiredGenerationIds} 와 방향이 반대인데,
     * 정본이 두 경계를 서로 다른 말로 정하기 때문이다.
     */
    @Query(
            """
            SELECT generation.generationId
            FROM AiRouteGeneration generation
            WHERE generation.status
                = com.example.ilgeobolkka.airoute.entity.AiRouteGenerationStatus.GENERATING
              AND generation.createdAt < :startedBefore
            """)
    List<UUID> findAbandonedGenerationIds(@Param("startedBefore") Instant startedBefore);
}
