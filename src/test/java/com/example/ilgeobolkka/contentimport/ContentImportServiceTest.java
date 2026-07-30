package com.example.ilgeobolkka.contentimport;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentImportServiceTest {

    @Mock private ContentBatchConverter converter;
    @Mock private ContentPageWriter pageWriter;

    @Test
    void 전체_변환이_끝난_결과만_DB에_적재한다() {
        ContentBatch batch = ContentBatchTestFixture.demoPageCountBatch();
        when(converter.convert()).thenReturn(batch);
        var service = new ContentImportService(converter, pageWriter);

        ContentBatch result = service.importContent();

        assertSame(batch, result);
        verify(pageWriter).write(batch);
    }

    @Test
    void 변환이나_검증이_실패하면_DB_적재를_시작하지_않는다() {
        when(converter.convert()).thenThrow(new IllegalStateException("변환 실패"));
        var service = new ContentImportService(converter, pageWriter);

        assertThrows(IllegalStateException.class, service::importContent);

        verifyNoInteractions(pageWriter);
    }
}
