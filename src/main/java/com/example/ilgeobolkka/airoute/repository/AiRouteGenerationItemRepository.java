package com.example.ilgeobolkka.airoute.repository;

import com.example.ilgeobolkka.airoute.entity.AiRouteGenerationItem;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiRouteGenerationItemRepository
        extends JpaRepository<AiRouteGenerationItem, Long> {

    /**
     * 한 생성의 임시 항목을 지운다. 저장으로 전환할 때와 만료 정리에서 쓴다. 생성 행보다 먼저 지워야
     * {@code fk_ai_route_generation_item_generation_book} 이 걸리지 않는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM AiRouteGenerationItem item WHERE item.generationId = :generationId")
    int deleteByGenerationId(@Param("generationId") UUID generationId);

    /** 만료 정리가 여러 생성의 항목을 한 문장으로 지운다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM AiRouteGenerationItem item WHERE item.generationId IN :generationIds")
    int deleteByGenerationIdIn(@Param("generationIds") Collection<UUID> generationIds);
}
