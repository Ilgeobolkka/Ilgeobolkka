package com.example.ilgeobolkka.airoute.repository;

import com.example.ilgeobolkka.airoute.entity.AiRouteGeneration;
import jakarta.persistence.LockModeType;
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
}
