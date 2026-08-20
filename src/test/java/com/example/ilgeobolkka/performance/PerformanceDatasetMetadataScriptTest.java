package com.example.ilgeobolkka.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PerformanceDatasetMetadataScriptTest {

    private static final Path SCRIPT = Path.of(
                    System.getProperty("user.dir"),
                    "performance",
                    "scripts",
                    "render-dataset-metadata.sh")
            .toAbsolutePath();

    @Test
    void mvp_metadata는_고정_데이터셋을_기록한다() throws Exception {
        ScriptResult result = 스크립트를_실행한다("mvp", "");

        assertEquals(0, result.exitCode(), result.stderr());
        assertTrue(result.stdout().contains("\"name\": \"mvp\""));
        assertTrue(result.stdout().contains("\"pageRentals\": 333"));
        assertTrue(result.stdout().contains("\"inkLedgerEntries\": 1333"));
        assertTrue(result.stdout().contains("\"libraryEntries\": 666"));
    }

    @Test
    void history_heavy_metadata는_검증한_행_수를_기록한다() throws Exception {
        ScriptResult result = 스크립트를_실행한다(
                "history-heavy",
                """
                ink_purchase\t6000
                page_rental\t500333
                ink_ledger\t506333
                reader\t1000
                book\t100
                book_page\t400
                library_entry\t666
                """);

        assertEquals(0, result.exitCode(), result.stderr());
        assertTrue(result.stdout().contains("\"name\": \"history-heavy\""));
        assertTrue(result.stdout().contains("\"pageRentals\": 500333"));
        assertTrue(result.stdout().contains("\"inkLedgerEntries\": 506333"));
        assertTrue(result.stdout().contains("\"libraryEntries\": 666"));
    }

    @Test
    void history_heavy_행_수가_다르면_metadata_생성을_거부한다() throws Exception {
        ScriptResult result = 스크립트를_실행한다(
                "history-heavy",
                """
                ink_purchase\t6000
                page_rental\t500332
                ink_ledger\t506333
                reader\t1000
                book\t100
                book_page\t400
                library_entry\t666
                """);

        assertNotEquals(0, result.exitCode());
        assertTrue(result.stderr().contains("history-heavy 행 수가 예상과 다릅니다."));
    }

    @Test
    void 알_수_없는_데이터셋은_거부한다() throws Exception {
        ScriptResult result = 스크립트를_실행한다("unknown", "");

        assertNotEquals(0, result.exitCode());
        assertTrue(result.stderr().contains("알 수 없는 성능 데이터셋입니다: unknown"));
    }

    private ScriptResult 스크립트를_실행한다(String dataset, String stdin)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder("sh", SCRIPT.toString(), dataset).start();
        process.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().close();

        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        return new ScriptResult(exitCode, stdout, stderr);
    }

    private record ScriptResult(int exitCode, String stdout, String stderr) {}
}
