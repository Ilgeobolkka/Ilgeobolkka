package com.example.ilgeobolkka.airoute.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 명시적인 activate 단계에서만 통과 report의 지원 도서 전체를 활성화한다. */
@Component
@Profile("evaluation")
@ConditionalOnProperty(
        prefix = "ai-route-evaluation",
        name = "phase",
        havingValue = "activate")
final class AiRouteSupportActivationRunner implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(AiRouteSupportActivationRunner.class);

    private final AiRouteEvaluationProperties properties;
    private final AiRouteEvaluationArtifactReader reader;
    private final AiRouteSupportActivationService activationService;

    AiRouteSupportActivationRunner(
            AiRouteEvaluationProperties properties,
            AiRouteEvaluationArtifactReader reader,
            AiRouteSupportActivationService activationService) {
        this.properties = properties;
        this.reader = reader;
        this.activationService = activationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        AiRouteEvaluationReport report = reader.readReport(properties.reportOutput());
        AiRouteSupportActivationService.Activation activation =
                activationService.activate(report);
        log.info(
                "AI 경로 지원 활성화를 완료했습니다: contentVersion={}, dataPolicyVersion={}, books={}, report={}",
                activation.contentVersion(),
                activation.dataPolicyVersion(),
                activation.activatedBookIds().size(),
                properties.reportOutput().toAbsolutePath().normalize());
    }
}
