package com.example.ilgeobolkka.contentimport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("content-import")
class PopplerPdfTool implements PdfTool {

    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(1);
    private static final Pattern VERSION_PATTERN = Pattern.compile("version (\\d+\\.\\d+\\.\\d+)");

    private final String pdftotextCommand;
    private final String pdftoppmCommand;

    PopplerPdfTool(ContentImportProperties properties) {
        this.pdftotextCommand = properties.pdftotextCommand();
        this.pdftoppmCommand = properties.pdftoppmCommand();
    }

    @Override
    public String pdftotextVersion() {
        return readVersion(pdftotextCommand);
    }

    @Override
    public String pdftoppmVersion() {
        return readVersion(pdftoppmCommand);
    }

    @Override
    public String extractText(Path pdfPath, int pageNumber) {
        CommandResult result =
                run(
                        pdftotextCommand,
                        "-f",
                        String.valueOf(pageNumber),
                        "-l",
                        String.valueOf(pageNumber),
                        "-enc",
                        "UTF-8",
                        "-eol",
                        "unix",
                        pdfPath.toString(),
                        "-");
        requireSuccess("pdftotext", result);
        return normalizeText(result.stdout());
    }

    @Override
    public boolean pageExists(Path pdfPath, int pageNumber) {
        CommandResult result =
                run(
                        pdftotextCommand,
                        "-f",
                        String.valueOf(pageNumber),
                        "-l",
                        String.valueOf(pageNumber),
                        "-enc",
                        "UTF-8",
                        pdfPath.toString(),
                        "-");
        if (result.exitCode() == 0) {
            return true;
        }
        if (result.stderr().contains("Wrong page range")) {
            return false;
        }
        requireSuccess("pdftotext", result);
        return false;
    }

    @Override
    public void renderJpeg(Path pdfPath, int pageNumber, Path outputPrefix) {
        CommandResult result =
                run(
                        pdftoppmCommand,
                        "-f",
                        String.valueOf(pageNumber),
                        "-l",
                        String.valueOf(pageNumber),
                        "-singlefile",
                        "-r",
                        "150",
                        "-jpeg",
                        "-jpegopt",
                        "quality=85,optimize=y,progressive=y",
                        pdfPath.toString(),
                        outputPrefix.toString());
        requireSuccess("pdftoppm", result);
    }

    static String normalizeText(String rawText) {
        return rawText.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    private String readVersion(String command) {
        CommandResult result = run(command, "-v");
        requireSuccess(command, result);
        Matcher matcher = VERSION_PATTERN.matcher(result.stdout() + "\n" + result.stderr());
        if (!matcher.find()) {
            throw new IllegalStateException(command + " 버전을 확인할 수 없습니다.");
        }
        return matcher.group(1);
    }

    private CommandResult run(String command, String... arguments) {
        List<String> commandLine = new ArrayList<>(arguments.length + 1);
        commandLine.add(command);
        commandLine.addAll(List.of(arguments));

        Process process;
        try {
            process = new ProcessBuilder(commandLine).start();
        } catch (IOException exception) {
            throw new IllegalStateException("외부 명령을 시작할 수 없습니다: " + command, exception);
        }

        CompletableFuture<byte[]> stdout =
                CompletableFuture.supplyAsync(() -> readAllBytes(process.getInputStream()));
        CompletableFuture<byte[]> stderr =
                CompletableFuture.supplyAsync(() -> readAllBytes(process.getErrorStream()));
        try {
            if (!process.waitFor(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("외부 명령이 제한 시간 안에 끝나지 않았습니다: " + command);
            }
            return new CommandResult(
                    process.exitValue(),
                    new String(stdout.join(), StandardCharsets.UTF_8),
                    new String(stderr.join(), StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException("외부 명령 대기가 중단되었습니다: " + command, exception);
        }
    }

    private byte[] readAllBytes(java.io.InputStream inputStream) {
        try (inputStream) {
            return inputStream.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("외부 명령 출력을 읽을 수 없습니다.", exception);
        }
    }

    private void requireSuccess(String command, CommandResult result) {
        if (result.exitCode() != 0) {
            throw new IllegalStateException(
                    command
                            + " 실행이 실패했습니다. 원문 오류: "
                            + result.stderr().strip());
        }
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {}
}
