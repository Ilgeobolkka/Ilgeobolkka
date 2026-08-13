package com.example.ilgeobolkka.airoute.facade;

import com.example.ilgeobolkka.airoute.dto.AiRouteFeedbackRequest;
import com.example.ilgeobolkka.airoute.dto.AiRouteFeedbackResponse;
import com.example.ilgeobolkka.airoute.entity.AiReadingRoute;
import com.example.ilgeobolkka.airoute.exception.AiRouteNotCompletedException;
import com.example.ilgeobolkka.airoute.exception.AiRouteNotFoundException;
import com.example.ilgeobolkka.airoute.repository.AiReadingRouteRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiRouteFeedbackFacade {

    private final AiReadingRouteRepository aiReadingRouteRepository;
    private final Clock clock;

    @Transactional
    public AiRouteFeedbackResponse changeFeedback(long routeId, long readerId, AiRouteFeedbackRequest request) {
        AiReadingRoute route = aiReadingRouteRepository.findOwnedByIdForUpdate(readerId, routeId)
                .orElseThrow(() -> new AiRouteNotFoundException(routeId));

        if (route.getCompletedAt() == null) {
            throw new AiRouteNotCompletedException();
        }

        Instant now = Instant.now(clock);
        route.updateFeedback(request.rating(), now);

        return new AiRouteFeedbackResponse(route.getId(), route.getFeedback(), route.getFeedbackAt());
    }
}
