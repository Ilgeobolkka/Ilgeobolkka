package com.example.ilgeobolkka.global.config;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ContentStoragePropertiesTest {

    @Test
    void 설정이_없으면_기존_콘텐츠_루트를_사용한다() {
        ContentStorageProperties properties = new ContentStorageProperties();

        assertEquals(Path.of("var/content/pages"), properties.root());
    }

    @Test
    void null과_빈_콘텐츠_루트는_거부한다() {
        ContentStorageProperties properties = new ContentStorageProperties();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> properties.setRoot(null)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> properties.setRoot(Path.of(""))));
    }
}
