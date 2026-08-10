package com.example.ilgeobolkka.airoute.repository;

import com.example.ilgeobolkka.airoute.entity.AiRouteCurrent;
import com.example.ilgeobolkka.airoute.entity.AiRouteCurrentId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRouteCurrentRepository
        extends JpaRepository<AiRouteCurrent, AiRouteCurrentId> {}
