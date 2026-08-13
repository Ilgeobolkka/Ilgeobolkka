package com.example.ilgeobolkka.contentimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.ilgeobolkka.book.entity.BookPageContentType;
import com.example.ilgeobolkka.contentimport.embedding.EmbeddedAiRouteContent;
import com.example.ilgeobolkka.contentimport.manifest.AiRouteContentManifest;
import com.example.ilgeobolkka.contentimport.manifest.InitialContentManifest;
import com.example.ilgeobolkka.contentimport.validation.ValidatedAiRouteContent;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ContentImportServiceTest {

    @Mock private ContentBatchConverter converter;
    @Mock private ContentBatchConverter.PreparedBatch preparedBatch;
    @Mock private ContentPageWriter pageWriter;
    @Mock private AiRouteContentImportPreparer aiRoutePreparer;
    @Mock private ContentImportLock importLock;
    @Mock private TransactionTemplate transactionTemplate;

    private void 적재_잠금_callback을_즉시_실행한다() {
        doAnswer(
                        invocation ->
                                invocation.<Supplier<ContentBatch>>getArgument(0).get())
                .when(importLock)
                .executeLocked(any());
    }

    private void 잠금_callback을_즉시_실행한다() {
        doAnswer(
                        invocation -> {
                            invocation.<Runnable>getArgument(0).run();
                            return null;
                        })
                .when(preparedBatch)
                .withPublicationLock(any());
    }

    private void 트랜잭션_callback을_즉시_실행한다() {
        doAnswer(
                        invocation -> {
                            Consumer<TransactionStatus> callback = invocation.getArgument(0);
                            TransactionSynchronizationManager.initSynchronization();
                            try {
                                callback.accept(null);
                                트랜잭션_완료를_알린다(TransactionSynchronization.STATUS_COMMITTED);
                                return null;
                            } catch (RuntimeException | Error failure) {
                                트랜잭션_완료를_알린다(TransactionSynchronization.STATUS_ROLLED_BACK);
                                throw failure;
                            } finally {
                                TransactionSynchronizationManager.clearSynchronization();
                            }
                        })
                .when(transactionTemplate)
                .executeWithoutResult(any());
    }

    private void 트랜잭션_완료를_알린다(int status) {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(status));
    }

    @Test
    void 기존_transaction_안에서는_적재를_시작하지_않는다() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        var service = service();

        try {
            assertThrows(IllegalTransactionStateException.class, service::importContent);
        } finally {
            TransactionSynchronizationManager.clear();
        }

        verifyNoInteractions(
                converter,
                preparedBatch,
                pageWriter,
                aiRoutePreparer,
                importLock,
                transactionTemplate);
    }

    @Test
    void initial_v1은_DB_SQL_성공_뒤_커밋_직전에_파일을_공개한다() {
        ContentBatch batch = ContentBatchTestFixture.demoPageCountBatch();
        InitialContentManifest manifest =
                new InitialContentManifest("initial-v1", List.of());
        when(converter.prepare()).thenReturn(preparedBatch);
        when(preparedBatch.manifest()).thenReturn(manifest);
        when(preparedBatch.batch()).thenReturn(batch);
        잠금_callback을_즉시_실행한다();
        트랜잭션_callback을_즉시_실행한다();
        적재_잠금_callback을_즉시_실행한다();
        var service = service();

        ContentBatch result = service.importContent();

        assertSame(batch, result);
        var order = inOrder(importLock, converter, pageWriter, preparedBatch);
        order.verify(importLock).executeLocked(any());
        order.verify(converter).prepare();
        order.verify(pageWriter).write(batch);
        order.verify(preparedBatch).publishWhileLocked();
        verifyNoInteractions(aiRoutePreparer);
    }

    @Test
    void ai_route_v2는_검증_embedding_결과_manifest_순서로_준비한_뒤_원자적_writer를_호출한다() {
        ContentBatch batch = aiBatch();
        AiRouteContentManifest manifest =
                new AiRouteContentManifest(
                        "ai-route-v2",
                        "OPENAI_DEFAULT_RETENTION_V1",
                        "text-embedding-3-small",
                        3,
                        List.of());
        ValidatedAiRouteContent content = validated();
        EmbeddedAiRouteContent embedded = embedded();
        when(converter.prepare()).thenReturn(preparedBatch);
        when(preparedBatch.manifest()).thenReturn(manifest);
        when(preparedBatch.batch()).thenReturn(batch);
        when(aiRoutePreparer.validate(manifest)).thenReturn(content);
        when(aiRoutePreparer.embed(content)).thenReturn(embedded);
        잠금_callback을_즉시_실행한다();
        트랜잭션_callback을_즉시_실행한다();
        적재_잠금_callback을_즉시_실행한다();
        var service = service();

        ContentBatch result = service.importContent();

        assertSame(batch, result);
        var order = inOrder(aiRoutePreparer, preparedBatch, pageWriter);
        order.verify(aiRoutePreparer).validate(manifest);
        order.verify(aiRoutePreparer).embed(content);
        order.verify(preparedBatch).writeAiResultManifest(any(AiRouteContentImportCommand.class));
        order.verify(pageWriter).write(any(AiRouteContentImportCommand.class));
        order.verify(preparedBatch).publishWhileLocked();
    }

    @Test
    void 변환이_실패하면_DB_적재와_파일_공개를_시작하지_않는다() {
        when(converter.prepare()).thenThrow(new IllegalStateException("변환 실패"));
        적재_잠금_callback을_즉시_실행한다();
        var service = service();

        assertThrows(IllegalStateException.class, service::importContent);

        verifyNoInteractions(pageWriter, aiRoutePreparer, preparedBatch);
    }

    @Test
    void AI_사전_검증이_실패하면_embedding_DB_적재_파일_공개를_시작하지_않는다() {
        AiRouteContentManifest manifest =
                new AiRouteContentManifest(
                        "ai-route-v2",
                        "OPENAI_DEFAULT_RETENTION_V1",
                        "text-embedding-3-small",
                        3,
                        List.of());
        when(converter.prepare()).thenReturn(preparedBatch);
        when(preparedBatch.manifest()).thenReturn(manifest);
        when(aiRoutePreparer.validate(manifest))
                .thenThrow(new IllegalStateException("프로필 불일치"));
        적재_잠금_callback을_즉시_실행한다();
        var service = service();

        assertThrows(IllegalStateException.class, service::importContent);

        verify(aiRoutePreparer, never()).embed(any());
        verify(preparedBatch, never()).publishWhileLocked();
        verifyNoInteractions(pageWriter);
    }

    @Test
    void DB_writer가_실패하면_파일을_공개하지_않는다() {
        ContentBatch batch = ContentBatchTestFixture.demoPageCountBatch();
        when(converter.prepare()).thenReturn(preparedBatch);
        when(preparedBatch.manifest())
                .thenReturn(new InitialContentManifest("initial-v1", List.of()));
        when(preparedBatch.batch()).thenReturn(batch);
        doThrow(new IllegalStateException("DB 실패")).when(pageWriter).write(batch);
        잠금_callback을_즉시_실행한다();
        트랜잭션_callback을_즉시_실행한다();
        적재_잠금_callback을_즉시_실행한다();
        var service = service();

        assertThrows(IllegalStateException.class, service::importContent);

        verify(preparedBatch, never()).publishWhileLocked();
        verify(preparedBatch).rollbackPublicationWhileLocked();
    }

    @Test
    void DB_commit_결과가_불명확하면_파일을_보존하고_복구할_배치를_경고한다(
            CapturedOutput output) {
        ContentBatch batch = ContentBatchTestFixture.demoPageCountBatch();
        Path finalDirectory = Path.of("/var/content/pages", batch.manifestSha256());
        when(converter.prepare()).thenReturn(preparedBatch);
        when(preparedBatch.manifest())
                .thenReturn(new InitialContentManifest("initial-v1", List.of()));
        when(preparedBatch.batch()).thenReturn(batch);
        when(preparedBatch.finalDirectory()).thenReturn(finalDirectory);
        잠금_callback을_즉시_실행한다();
        doAnswer(
                        invocation -> {
                            Consumer<TransactionStatus> callback = invocation.getArgument(0);
                            TransactionSynchronizationManager.initSynchronization();
                            try {
                                callback.accept(null);
                                throw new IllegalStateException("commit 결과 불명");
                            } finally {
                                TransactionSynchronizationManager.clearSynchronization();
                            }
                        })
                .when(transactionTemplate)
                .executeWithoutResult(any());
        적재_잠금_callback을_즉시_실행한다();
        var service = service();

        assertThrows(IllegalStateException.class, service::importContent);

        var order = inOrder(pageWriter, preparedBatch);
        order.verify(pageWriter).write(batch);
        order.verify(preparedBatch).publishWhileLocked();
        verify(preparedBatch, never()).rollbackPublicationWhileLocked();
        assertThat(output.getAll())
                .contains(
                        "WARN",
                        "DB commit 결과가 불명확해 콘텐츠 파일을 보존합니다",
                        "manifestSha256=" + batch.manifestSha256(),
                        "finalDirectory=" + finalDirectory);
    }

    @Test
    void DB_rollback이_확정되면_커밋_직전_공개한_파일을_되돌린다() {
        ContentBatch batch = ContentBatchTestFixture.demoPageCountBatch();
        when(converter.prepare()).thenReturn(preparedBatch);
        when(preparedBatch.manifest())
                .thenReturn(new InitialContentManifest("initial-v1", List.of()));
        when(preparedBatch.batch()).thenReturn(batch);
        잠금_callback을_즉시_실행한다();
        doAnswer(
                        invocation -> {
                            Consumer<TransactionStatus> callback = invocation.getArgument(0);
                            TransactionSynchronizationManager.initSynchronization();
                            try {
                                callback.accept(null);
                                트랜잭션_완료를_알린다(
                                        TransactionSynchronization.STATUS_ROLLED_BACK);
                                throw new UnexpectedRollbackException("rollback 확정");
                            } finally {
                                TransactionSynchronizationManager.clearSynchronization();
                            }
                        })
                .when(transactionTemplate)
                .executeWithoutResult(any());
        적재_잠금_callback을_즉시_실행한다();
        var service = service();

        assertThrows(UnexpectedRollbackException.class, service::importContent);

        var order = inOrder(pageWriter, preparedBatch);
        order.verify(pageWriter).write(batch);
        order.verify(preparedBatch).publishWhileLocked();
        order.verify(preparedBatch).rollbackPublicationWhileLocked();
    }

    @Test
    void 파일_게시가_실패하면_DB_commit_전에_게시_상태를_되돌린다() {
        ContentBatch batch = ContentBatchTestFixture.demoPageCountBatch();
        when(converter.prepare()).thenReturn(preparedBatch);
        when(preparedBatch.manifest())
                .thenReturn(new InitialContentManifest("initial-v1", List.of()));
        when(preparedBatch.batch()).thenReturn(batch);
        잠금_callback을_즉시_실행한다();
        트랜잭션_callback을_즉시_실행한다();
        적재_잠금_callback을_즉시_실행한다();
        doThrow(new IllegalStateException("게시 실패"))
                .when(preparedBatch)
                .publishWhileLocked();
        var service = service();

        assertThrows(IllegalStateException.class, service::importContent);

        var order = inOrder(pageWriter, preparedBatch);
        order.verify(pageWriter).write(batch);
        order.verify(preparedBatch).publishWhileLocked();
        order.verify(preparedBatch).rollbackPublicationWhileLocked();
    }

    private ContentImportService service() {
        return new ContentImportService(
                converter, pageWriter, aiRoutePreparer, importLock, transactionTemplate);
    }

    private ContentBatch aiBatch() {
        return new ContentBatch(
                "ai-route-v2",
                "b".repeat(64),
                List.of(
                        new ConvertedBook(
                                41,
                                "a".repeat(64),
                                1,
                                List.of(
                                        new ConvertedPage(
                                                41,
                                                1,
                                                BookPageContentType.TEXT,
                                                "본문",
                                                null,
                                                null)))));
    }

    private ValidatedAiRouteContent validated() {
        return new ValidatedAiRouteContent(
                "ai-route-v2",
                "OPENAI_DEFAULT_RETENTION_V1",
                "text-embedding-3-small",
                3,
                List.of(
                        new ValidatedAiRouteContent.ValidatedBook(
                                41,
                                "도서 41",
                                1,
                                true,
                                true,
                                List.of(
                                        new ValidatedAiRouteContent.ValidatedPage(
                                                1,
                                                true,
                                                "분석",
                                                "공개 주제",
                                                60,
                                                List.of())),
                                List.of())));
    }

    private EmbeddedAiRouteContent embedded() {
        return new EmbeddedAiRouteContent(
                "ai-route-v2",
                "text-embedding-3-small",
                3,
                Map.of(
                        new EmbeddedAiRouteContent.PageKey(41, 1, "ai-route-v2"),
                        List.of(0.1, 0.2, 0.3)));
    }
}
