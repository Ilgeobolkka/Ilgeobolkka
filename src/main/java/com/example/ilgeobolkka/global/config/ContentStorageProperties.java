package com.example.ilgeobolkka.global.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("content-storage")
public class ContentStorageProperties {

    private Path root = Path.of("var/content/pages");

    public Path root() {
        return root;
    }

    public void setRoot(Path root) {
        if (root == null || root.toString().isBlank()) {
            throw new IllegalArgumentException("콘텐츠 저장소 루트는 비어 있을 수 없습니다.");
        }
        this.root = root;
    }
}
