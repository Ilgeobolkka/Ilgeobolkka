package com.example.ilgeobolkka.airoute.repository;

import com.example.ilgeobolkka.airoute.entity.AiRouteGeneration;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRouteGenerationRepository extends JpaRepository<AiRouteGeneration, UUID> {}
