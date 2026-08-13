package com.example.ilgeobolkka.airoute.repository;

import com.example.ilgeobolkka.airoute.entity.AiRoutePrerequisite;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRoutePrerequisiteRepository
        extends JpaRepository<AiRoutePrerequisite, Long> {

    List<AiRoutePrerequisite> findAllByBookIdOrderByDependentPageNumberAscPrerequisitePageNumberAsc(
            long bookId);
}
