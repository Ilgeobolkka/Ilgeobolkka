package com.example.ilgeobolkka.contentimport;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentImportServiceTest {

    @Mock private ContentBatchConverter converter;
    @Mock private ContentPageWriter pageWriter;
    @Mock private AiRouteContentImporter aiRouteContentImporter;

    @Test
    void 전체_변환이_끝난_결과만_DB에_적재한다() {
        ContentBatch batch = ContentBatchTestFixture.demoPageCountBatch();
        when(converter.convert()).thenReturn(batch);
        var service = new ContentImportService(converter, pageWriter, aiRouteContentImporter);

        ContentBatch result = service.importContent();

        assertSame(batch, result);
        verify(pageWriter).write(batch);
        // initial-v1은 AI 경로를 거치지 않는다.
        verifyNoInteractions(aiRouteContentImporter);
    }

    @Test
    void ai_route_v2는_검증_embedding을_먼저_끝내고_적재한다() {
        ContentBatch batch = new ContentBatch("ai-route-v2", "a".repeat(64), List.of());
        var prepared = mock(AiRouteContentImporter.PreparedContent.class);
        when(converter.convert()).thenReturn(batch);
        when(aiRouteContentImporter.prepare()).thenReturn(prepared);
        var service = new ContentImportService(converter, pageWriter, aiRouteContentImporter);

        service.importContent();

        // 본문 적재는 AI 적재와 한 트랜잭션에 묶여 importer 안에서 일어난다.
        verifyNoInteractions(pageWriter);
        InOrder inOrder = inOrder(aiRouteContentImporter);
        inOrder.verify(aiRouteContentImporter).prepare();
        inOrder.verify(aiRouteContentImporter).write(batch, prepared);
    }

    @Test
    void 변환이나_검증이_실패하면_DB_적재를_시작하지_않는다() {
        when(converter.convert()).thenThrow(new IllegalStateException("변환 실패"));
        var service = new ContentImportService(converter, pageWriter, aiRouteContentImporter);

        assertThrows(IllegalStateException.class, service::importContent);

        verifyNoInteractions(pageWriter, aiRouteContentImporter);
    }
}
