package com.example.ilgeobolkka.airoute.evaluation;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("evaluation")
@ConfigurationProperties("ai-route-evaluation")
class AiRouteEvaluationProperties {

    private Path manifest = Path.of("fixtures/content/ai-route-v2/manifest.json");
    private Path evaluation = Path.of("fixtures/content/ai-route-v2/evaluation.json");
    private Path output = Path.of("var/evaluation/ai-route-evaluation.json");
    private String manifestGitRevision;
    private String evaluationGitRevision;

    Path manifest() {
        return manifest;
    }

    public void setManifest(Path manifest) {
        this.manifest = manifest;
    }

    Path evaluation() {
        return evaluation;
    }

    public void setEvaluation(Path evaluation) {
        this.evaluation = evaluation;
    }

    Path output() {
        return output;
    }

    public void setOutput(Path output) {
        this.output = output;
    }

    String manifestGitRevision() {
        return manifestGitRevision;
    }

    public void setManifestGitRevision(String manifestGitRevision) {
        this.manifestGitRevision = manifestGitRevision;
    }

    String evaluationGitRevision() {
        return evaluationGitRevision;
    }

    public void setEvaluationGitRevision(String evaluationGitRevision) {
        this.evaluationGitRevision = evaluationGitRevision;
    }
}
