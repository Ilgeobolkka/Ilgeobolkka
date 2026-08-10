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
     * 멱등 키로 기존 생성을 잠금 조회한다. {@code readerId}를 조건에 두는 이유는 두 가지다. 남의 키를
     * 찍어 남의 생성 상태를 읽는 경로를 막고, {@code uk_ai_route_generation_reader_idempotency}와 같은
     * 열 순서를 써서 인덱스를 그대로 탄다.
     *
     * <p>행을 잠그는 것은 G06이 같은 행의 상태를 옮기는 중에 반쯤 바뀐 상태를 읽지 않기 위해서다. 행이
     * 아직 없을 때 동시 insert를 막는 용도가 아니다. 그쪽은 unique key 경합을 잡아 재실행으로 수렴한다.
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
