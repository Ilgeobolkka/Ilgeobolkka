package com.example.ilgeobolkka.airoute.dto;

import com.example.ilgeobolkka.airoute.entity.AiReadingRouteFeedback;
import java.time.Instant;

public record AiRouteFeedbackResponse(
        Long routeId,
        AiReadingRouteFeedback rating,
        Instant feedbackAt
) {
}
