package com.example.ilgeobolkka.contentimport;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("content-import")
@ConfigurationProperties("content-import")
class ContentImportProperties {

    private Path manifest = Path.of("fixtures/content/manifest.json");
    /** AI 경로 평가 데이터. C02가 정답의 페이지 연결을 검사할 때 쓰며 `initial-v1` 적재에는 필요 없다. */
    private Path evaluation = Path.of("fixtures/content/ai-route-v2/evaluation.json");
    private String pdftotextCommand = "pdftotext";
    private String pdftoppmCommand = "pdftoppm";

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

    String pdftotextCommand() {
        return pdftotextCommand;
    }

    public void setPdftotextCommand(String pdftotextCommand) {
        this.pdftotextCommand = pdftotextCommand;
    }

    String pdftoppmCommand() {
        return pdftoppmCommand;
    }

    public void setPdftoppmCommand(String pdftoppmCommand) {
        this.pdftoppmCommand = pdftoppmCommand;
    }
}
