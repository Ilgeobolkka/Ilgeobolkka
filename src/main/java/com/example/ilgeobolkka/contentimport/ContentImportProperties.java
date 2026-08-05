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
    private String pdftotextCommand = "pdftotext";
    private String pdftoppmCommand = "pdftoppm";

    Path manifest() {
        return manifest;
    }

    public void setManifest(Path manifest) {
        this.manifest = manifest;
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
