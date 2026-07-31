package com.example.ilgeobolkka.reading.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.ilgeobolkka.book.entity.BookPage;
import com.example.ilgeobolkka.book.entity.BookPageContentType;
import com.example.ilgeobolkka.reading.dto.PageContent;
import com.example.ilgeobolkka.reading.exception.PageContentNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.BeanUtils;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;

class PageContentServiceTest {

    private static final byte[] JPEG_BYTES = {
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00
    };
    private static final byte[] PNG_BYTES = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00
    };

    @TempDir
    Path tempDirectory;

    private Path contentRoot;
    private PageContentService pageContentService;

    @BeforeEach
    void setUp() throws IOException {
        contentRoot = Files.createDirectories(tempDirectory.resolve("var/content/pages"));
        pageContentService = new PageContentService(contentRoot);
    }

    @Test
    void TEXT는_UTF_8_본문과_정확한_Content_Type을_반환한다() {
        BookPage page = 텍스트_페이지("첫 문단\n둘째 문단");

        PageContent content = pageContentService.read(page);

        assertArrayEquals(
                "첫 문단\n둘째 문단".getBytes(StandardCharsets.UTF_8), content.body());
        assertEquals(
                MediaType.parseMediaType("text/plain;charset=UTF-8"), content.mediaType());
    }

    @Test
    void IMAGE는_확장자가_아니라_실제_바이트로_JPEG와_PNG를_판정한다() throws IOException {
        Path jpegPath = 이미지_파일을_만든다("page.png", JPEG_BYTES);
        Path pngPath = 이미지_파일을_만든다("page.jpg", PNG_BYTES);

        PageContent jpeg = pageContentService.read(이미지_페이지(jpegPath));
        PageContent png = pageContentService.read(이미지_페이지(pngPath));

        assertArrayEquals(JPEG_BYTES, jpeg.body());
        assertEquals(MediaType.IMAGE_JPEG, jpeg.mediaType());
        assertArrayEquals(PNG_BYTES, png.body());
        assertEquals(MediaType.IMAGE_PNG, png.mediaType());
    }

    @Test
    void 콘텐츠_루트_밖의_IMAGE_경로는_노출하지_않고_거부한다() throws IOException {
        Path outside = tempDirectory.resolve("outside.jpg");
        Files.write(outside, JPEG_BYTES);

        PageContentNotFoundException exception = assertThrows(
                PageContentNotFoundException.class,
                () -> pageContentService.read(이미지_페이지(outside)));

        assertFalse(exception.getMessage().contains(outside.toString()));
    }

    @Test
    void 콘텐츠_루트_안의_심볼릭_링크가_밖을_가리키면_거부한다() throws IOException {
        Path outside = tempDirectory.resolve("outside.jpg");
        Files.write(outside, JPEG_BYTES);
        Path symbolicLink = contentRoot.resolve("linked.jpg");
        Files.createSymbolicLink(symbolicLink, outside);

        assertThrows(
                PageContentNotFoundException.class,
                () -> pageContentService.read(이미지_페이지(symbolicLink)));
    }

    @Test
    void IMAGE_파일이_없거나_지원_형식이_아니면_404_대상_예외다() throws IOException {
        Path missing = contentRoot.resolve("missing.jpg");
        Path unsupported = 이미지_파일을_만든다("page.gif", "not-image".getBytes(StandardCharsets.UTF_8));

        assertThrows(
                PageContentNotFoundException.class,
                () -> pageContentService.read(이미지_페이지(missing)));
        assertThrows(
                PageContentNotFoundException.class,
                () -> pageContentService.read(이미지_페이지(unsupported)));
    }

    private Path 이미지_파일을_만든다(String fileName, byte[] content) throws IOException {
        Path image = contentRoot.resolve("batch/book-001").resolve(fileName);
        Files.createDirectories(image.getParent());
        return Files.write(image, content);
    }

    private BookPage 텍스트_페이지(String text) {
        BookPage page = BeanUtils.instantiateClass(BookPage.class);
        ReflectionTestUtils.setField(page, "id", 1L);
        ReflectionTestUtils.setField(page, "contentType", BookPageContentType.TEXT);
        ReflectionTestUtils.setField(page, "textContent", text);
        return page;
    }

    private BookPage 이미지_페이지(Path path) {
        BookPage page = BeanUtils.instantiateClass(BookPage.class);
        ReflectionTestUtils.setField(page, "id", 2L);
        ReflectionTestUtils.setField(page, "contentType", BookPageContentType.IMAGE);
        ReflectionTestUtils.setField(page, "imagePath", path.toString());
        return page;
    }
}
