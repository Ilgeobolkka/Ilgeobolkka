package com.example.ilgeobolkka.contentimport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("content-import")
class ContentImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ContentImportRunner.class);

    private final ContentImportService contentImportService;

    ContentImportRunner(ContentImportService contentImportService) {
        this.contentImportService = contentImportService;
    }

    @Override
    public void run(ApplicationArguments args) {
        ContentBatch batch = contentImportService.importContent();
        log.info(
                "콘텐츠 적재를 완료했습니다: manifestSha256={}, books={}, pages={}",
                batch.manifestSha256(),
                batch.books().size(),
                batch.pages().size());
    }
}
