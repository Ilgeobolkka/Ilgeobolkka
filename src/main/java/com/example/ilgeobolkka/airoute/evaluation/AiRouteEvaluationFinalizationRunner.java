package com.example.ilgeobolkka.airoute.evaluation;

import com.example.ilgeobolkka.infra.openai.OpenAiProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Q01 결과와 지정 검수자 판정을 Q02 report로 확정하며 DB 지원 상태는 바꾸지 않는다. */
@Component
@Profile("evaluation")
@ConditionalOnProperty(
        prefix = "ai-route-evaluation",
        name = "phase",
        havingValue = "finalize")
final class AiRouteEvaluationFinalizationRunner implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(AiRouteEvaluationFinalizationRunner.class);

    private final AiRouteEvaluationProperties properties;
    private final AiRouteEvaluationArtifactReader reader;
    private final AiRouteEvaluationReportWriter writer;
    private final OpenAiProperties openAiProperties;

    AiRouteEvaluationFinalizationRunner(
            AiRouteEvaluationProperties properties,
            AiRouteEvaluationArtifactReader reader,
            AiRouteEvaluationReportWriter writer,
            OpenAiProperties openAiProperties) {
        this.properties = properties;
        this.reader = reader;
        this.writer = writer;
        this.openAiProperties = openAiProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        writer.requireAvailable(properties.reportOutput());
        AiRouteEvaluationResult result = reader.readResult(properties.output());
        List<AiRouteHumanJudgment> judgments = reader.readJudgments(properties.judgments());
        AiRouteEvaluationReport report = AiRouteEvaluationReport.create(
                result, judgments, openAiProperties.dataPolicyVersion());
        AiRouteEvaluationReportWriter.Artifact artifact =
                writer.write(properties.reportOutput(), report);

        log.info(
                "AI 경로 평가 report를 작성했습니다: cases={}, passed={}, artifact={}, sha256={}",
                report.metrics().totalCases(),
                report.metrics().passed(),
                artifact.path(),
                artifact.sha256());
        if (!report.metrics().passed()) {
            throw new IllegalStateException(
                    "AI 경로 평가가 전체 품질 기준을 통과하지 못했습니다: " + artifact.path());
        }
    }
}
