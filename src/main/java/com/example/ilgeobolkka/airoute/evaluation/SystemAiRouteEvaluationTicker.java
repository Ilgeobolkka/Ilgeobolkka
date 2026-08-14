package com.example.ilgeobolkka.airoute.evaluation;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("evaluation")
class SystemAiRouteEvaluationTicker implements AiRouteEvaluationTicker {

    @Override
    public long readNanos() {
        return System.nanoTime();
    }
}
