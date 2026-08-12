package com.example.ilgeobolkka.contentimport;

import com.example.ilgeobolkka.contentimport.manifest.ContentManifest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("content-import")
class ContentImportService {

    private final ContentBatchConverter converter;
    private final ContentPageWriter pageWriter;
    private final AiRouteContentImporter aiRouteContentImporter;

    ContentImportService(
            ContentBatchConverter converter,
            ContentPageWriter pageWriter,
            AiRouteContentImporter aiRouteContentImporter) {
        this.converter = converter;
        this.pageWriter = pageWriter;
        this.aiRouteContentImporter = aiRouteContentImporter;
    }

    ContentBatch importContent() {
        ContentBatch batch = converter.convert();
        if (ContentManifest.AI_ROUTE_CONTENT_VERSION.equals(batch.contentVersion())) {
            // 검증·Embeddings를 먼저 끝내고 DB 쓰기만 트랜잭션에 넣는다. 두 단계를 나눠 부르는 이유는
            // 같은 빈 안에서 부르면 프록시를 거치지 않아 트랜잭션이 걸리지 않기 때문이다.
            AiRouteContentImporter.PreparedContent prepared = aiRouteContentImporter.prepare();
            aiRouteContentImporter.write(batch, prepared);
            return batch;
        }
        pageWriter.write(batch);
        return batch;
    }
}
