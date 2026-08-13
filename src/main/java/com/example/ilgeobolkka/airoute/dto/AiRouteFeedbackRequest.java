package com.example.ilgeobolkka.airoute.dto;

import com.example.ilgeobolkka.airoute.entity.AiReadingRouteFeedback;
import jakarta.validation.constraints.NotNull;

public record AiRouteFeedbackRequest(
        @NotNull AiReadingRouteFeedback rating
) {
}
