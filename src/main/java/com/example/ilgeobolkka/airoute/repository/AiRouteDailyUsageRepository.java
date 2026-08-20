package com.example.ilgeobolkka.airoute.repository;

import com.example.ilgeobolkka.airoute.entity.AiRouteDailyUsage;
import com.example.ilgeobolkka.airoute.entity.AiRouteDailyUsageId;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiRouteDailyUsageRepository
        extends JpaRepository<AiRouteDailyUsage, AiRouteDailyUsageId> {

    /**
     * 그날의 사용량 행을 0회로 만든다. 이미 있으면 값을 바꾸지 않는다.
     *
     * <p>조회 후 저장으로 나누면 같은 독자의 동시 요청이 나란히 INSERT를 시도해 기본 키가 깨진다. 한
     * 문장으로 처리해 뒤따르는 잠금 조회가 항상 존재하는 행을 잠그게 만든다. 없는 행에 잠금을 걸면 gap
     * lock이 잡혀 동시 요청끼리 교착한다.
     *
     * <p>{@code usage_date}는 기본 키의 일부라 같은 값으로 다시 쓰는 것이 곧 아무것도 바꾸지 않는다는
     * 뜻이다.
     */
    @Modifying(flushAutomatically = true)
    @Query(
            value =
                    """
                    INSERT INTO ai_route_daily_usage (reader_id, usage_date, generation_count)
                    VALUES (:readerId, :usageDate, 0)
                    AS incoming
                    ON DUPLICATE KEY UPDATE
                        usage_date = incoming.usage_date
                    """,
            nativeQuery = true)
    void insertUnusedDayIfAbsent(
            @Param("readerId") long readerId, @Param("usageDate") LocalDate usageDate);

    /**
     * 사용량 행을 잠근다. {@link #insertUnusedDayIfAbsent} 뒤에만 부르므로 행은 반드시 있다.
     *
     * <p>이 잠금이 같은 독자의 동시 생성 시작을 줄 세운다. 한도 판정과 증가 사이에 다른 요청이 끼어들지
     * 못해야 동시 요청으로 하루 상한을 넘을 수 없다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT dailyUsage
            FROM AiRouteDailyUsage dailyUsage
            WHERE dailyUsage.readerId = :readerId
              AND dailyUsage.usageDate = :usageDate
            """)
    Optional<AiRouteDailyUsage> findByReaderIdAndUsageDateForUpdate(
            @Param("readerId") long readerId, @Param("usageDate") LocalDate usageDate);
}
