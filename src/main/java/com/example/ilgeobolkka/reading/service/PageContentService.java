package com.example.ilgeobolkka.reading.service;

import com.example.ilgeobolkka.book.entity.BookPage;
import com.example.ilgeobolkka.reading.dto.PageContent;
import com.example.ilgeobolkka.reading.exception.PageContentNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Service
public class PageContentService {

    private static final Path DEFAULT_CONTENT_ROOT = Path.of("var/content/pages");
    private static final MediaType TEXT_PLAIN_UTF_8 =
            new MediaType("text", "plain", StandardCharsets.UTF_8);
    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private final Path contentRoot;

    public PageContentService() {
        this(DEFAULT_CONTENT_ROOT);
    }

    PageContentService(Path contentRoot) {
        this.contentRoot = contentRoot.toAbsolutePath().normalize();
    }

    public PageContent read(BookPage page) {
        return switch (page.getContentType()) {
            case TEXT -> new PageContent(
                    page.getTextContent().getBytes(StandardCharsets.UTF_8), TEXT_PLAIN_UTF_8);
            case IMAGE -> readImage(page.getImagePath());
        };
    }

    private PageContent readImage(String storedPath) {
        try {
            Path imagePath = Path.of(storedPath).toAbsolutePath().normalize();
            if (!imagePath.startsWith(contentRoot)) {
                throw new PageContentNotFoundException();
            }

            Path realRoot = contentRoot.toRealPath();
            Path realImagePath = imagePath.toRealPath();
            if (!realImagePath.startsWith(realRoot) || !Files.isRegularFile(realImagePath)) {
                throw new PageContentNotFoundException();
            }

            byte[] body = Files.readAllBytes(realImagePath);
            return new PageContent(body, detectImageType(body));
        } catch (IOException | InvalidPathException exception) {
            throw new PageContentNotFoundException();
        }
    }

    private MediaType detectImageType(byte[] body) {
        if (body.length >= 3
                && body[0] == (byte) 0xFF
                && body[1] == (byte) 0xD8
                && body[2] == (byte) 0xFF) {
            return MediaType.IMAGE_JPEG;
        }
        if (startsWith(body, PNG_SIGNATURE)) {
            return MediaType.IMAGE_PNG;
        }
        throw new PageContentNotFoundException();
    }

    private boolean startsWith(byte[] body, byte[] signature) {
        if (body.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (body[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }
}
