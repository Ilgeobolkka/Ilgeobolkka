package com.example.ilgeobolkka.airoute.repository;

import com.example.ilgeobolkka.airoute.entity.AiRouteDailyUsage;
import com.example.ilgeobolkka.airoute.entity.AiRouteDailyUsageId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRouteDailyUsageRepository
        extends JpaRepository<AiRouteDailyUsage, AiRouteDailyUsageId> {}
