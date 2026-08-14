package com.example.ilgeobolkka.airoute.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("evaluation")
class AiRouteEvaluationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AiRouteEvaluationRunner.class);

    private final AiRouteEvaluationReader reader;
    private final AiRouteEvaluationService service;
    private final AiRouteEvaluationResultWriter writer;

    AiRouteEvaluationRunner(
            AiRouteEvaluationReader reader,
            AiRouteEvaluationService service,
            AiRouteEvaluationResultWriter writer) {
        this.reader = reader;
        this.service = service;
        this.writer = writer;
    }

    @Override
    public void run(ApplicationArguments args) {
        writer.requireAvailable();
        AiRouteEvaluationResult result = service.evaluate(reader.read());
        writer.write(result);
        if (!result.successful()) {
            log.error(
                    "AI 경로 평가가 실패했습니다: caseId={}, reason={}, completedCases={}, artifact={}",
                    result.failedCase().caseId(),
                    result.failedCase().reason(),
                    result.completedCases().size(),
                    writer.output());
            throw new IllegalStateException(
                    "AI 경로 평가가 실패했습니다: caseId=%s, reason=%s"
                            .formatted(
                                    result.failedCase().caseId(), result.failedCase().reason()));
        }
        log.info(
                "AI 경로 평가를 완료했습니다: cases={}, artifact={}",
                result.completedCases().size(),
                writer.output());
    }
}
