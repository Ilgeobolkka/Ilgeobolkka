package com.example.ilgeobolkka.contentimport;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("content-import")
class ContentImportService {

    private final ContentBatchConverter converter;
    private final ContentPageWriter pageWriter;

    ContentImportService(ContentBatchConverter converter, ContentPageWriter pageWriter) {
        this.converter = converter;
        this.pageWriter = pageWriter;
    }

    ContentBatch importContent() {
        ContentBatch batch = converter.convert();
        pageWriter.write(batch);
        return batch;
    }
}
