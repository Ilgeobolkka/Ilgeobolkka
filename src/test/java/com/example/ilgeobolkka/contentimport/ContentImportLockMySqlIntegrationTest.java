package com.example.ilgeobolkka.contentimport;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.testfixture.database.DedicatedTestDatabaseInitializer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = DedicatedTestDatabaseInitializer.class)
class ContentImportLockMySqlIntegrationTest {

    private final ContentImportLock importLock;

    @Autowired
    ContentImportLockMySqlIntegrationTest(ContentImportLock importLock) {
        this.importLock = importLock;
    }

    @Test
    void 같은_DB의_다른_콘텐츠_적재가_실행_중이면_즉시_거부하고_종료_후에는_허용한다()
            throws Exception {
        CountDownLatch firstAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> firstRun =
                    executor.submit(
                            () ->
                                    importLock.executeLocked(
                                            () -> {
                                                firstAcquired.countDown();
                                                await(releaseFirst);
                                                return null;
                                            }));
            assertTrue(firstAcquired.await(1, TimeUnit.SECONDS));

            AtomicBoolean secondRan = new AtomicBoolean();
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            importLock.executeLocked(
                                    () -> {
                                        secondRan.set(true);
                                        return null;
                                    }));
            assertFalse(secondRan.get());

            releaseFirst.countDown();
            firstRun.get(1, TimeUnit.SECONDS);

            assertDoesNotThrow(() -> importLock.executeLocked(() -> null));
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void 적재가_실패해도_advisory_lock을_해제한다() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        importLock.executeLocked(
                                () -> {
                                    throw new IllegalStateException("적재 실패");
                                }));

        assertDoesNotThrow(() -> importLock.executeLocked(() -> null));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시 실행 종료를 기다리다 시간 초과했습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시 실행 대기가 중단됐습니다.", exception);
        }
    }
}
