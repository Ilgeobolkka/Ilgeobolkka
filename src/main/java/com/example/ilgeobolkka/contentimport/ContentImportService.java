package com.example.ilgeobolkka.contentimport;

import com.example.ilgeobolkka.contentimport.embedding.EmbeddedAiRouteContent;
import com.example.ilgeobolkka.contentimport.manifest.AiRouteContentManifest;
import com.example.ilgeobolkka.contentimport.manifest.InitialContentManifest;
import com.example.ilgeobolkka.contentimport.validation.ValidatedAiRouteContent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("content-import")
class ContentImportService {

    private static final Logger log = LoggerFactory.getLogger(ContentImportService.class);

    private final ContentBatchConverter converter;
    private final ContentPageWriter pageWriter;
    private final AiRouteContentImportPreparer aiRoutePreparer;
    private final ContentImportLock importLock;
    private final TransactionTemplate transactionTemplate;

    ContentImportService(
            ContentBatchConverter converter,
            ContentPageWriter pageWriter,
            AiRouteContentImportPreparer aiRoutePreparer,
            ContentImportLock importLock,
            TransactionTemplate transactionTemplate) {
        this.converter = converter;
        this.pageWriter = pageWriter;
        this.aiRoutePreparer = aiRoutePreparer;
        this.importLock = importLock;
        this.transactionTemplate = transactionTemplate;
    }

    ContentBatch importContent() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException(
                    "콘텐츠 적재는 기존 transaction 안에서 시작할 수 없습니다.");
        }
        return importLock.executeLocked(this::importContentWhileLocked);
    }

    private ContentBatch importContentWhileLocked() {
        try (ContentBatchConverter.PreparedBatch prepared = converter.prepare()) {
            if (prepared.manifest() instanceof InitialContentManifest) {
                writeAndPublish(prepared, () -> pageWriter.write(prepared.batch()));
                return prepared.batch();
            }
            if (prepared.manifest() instanceof AiRouteContentManifest aiManifest) {
                ValidatedAiRouteContent content = aiRoutePreparer.validate(aiManifest);
                EmbeddedAiRouteContent embedded = aiRoutePreparer.embed(content);
                AiRouteContentImportCommand command =
                        AiRouteContentImportCommand.create(prepared.batch(), content, embedded);
                prepared.writeAiResultManifest(command);
                writeAndPublish(prepared, () -> pageWriter.write(command));
                return prepared.batch();
            }
            throw new IllegalStateException("지원하지 않는 콘텐츠 manifest 타입입니다.");
        }
    }

    private void writeAndPublish(
            ContentBatchConverter.PreparedBatch prepared, Runnable databaseWrite) {
        prepared.withPublicationLock(
                () -> {
                    AtomicBoolean publicationCompleted = new AtomicBoolean();
                    AtomicInteger completionStatus =
                            new AtomicInteger(TransactionSynchronization.STATUS_UNKNOWN);
                    try {
                        transactionTemplate.executeWithoutResult(
                                ignored -> {
                                    TransactionSynchronizationManager.registerSynchronization(
                                            new TransactionSynchronization() {
                                                @Override
                                                public void afterCompletion(int status) {
                                                    completionStatus.set(status);
                                                }
                                            });
                                    databaseWrite.run();
                                    // SQL은 끝났지만 아직 commit 전이라 다른 transaction은 경로를 보지 못한다.
                                    prepared.publishWhileLocked();
                                    publicationCompleted.set(true);
                                });
                    } catch (RuntimeException | Error failure) {
                        // 게시 전 실패나 확정 rollback은 파일을 되돌린다. commit 결과가 불명확하거나
                        // 이미 commit됐다면 DB가 참조할 수 있으므로 공개 파일을 보존한다.
                        if (!publicationCompleted.get()
                                || completionStatus.get()
                                        == TransactionSynchronization.STATUS_ROLLED_BACK) {
                            rollbackPublication(prepared, failure);
                        } else if (completionStatus.get()
                                == TransactionSynchronization.STATUS_UNKNOWN) {
                            log.warn(
                                    "DB commit 결과가 불명확해 콘텐츠 파일을 보존합니다: manifestSha256={}, finalDirectory={}",
                                    prepared.batch().manifestSha256(),
                                    prepared.finalDirectory());
                        }
                        throw failure;
                    }
                });
    }

    private void rollbackPublication(
            ContentBatchConverter.PreparedBatch prepared, Throwable failure) {
        try {
            prepared.rollbackPublicationWhileLocked();
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }
}
