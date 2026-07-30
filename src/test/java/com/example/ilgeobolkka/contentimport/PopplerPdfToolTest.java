package com.example.ilgeobolkka.contentimport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PopplerPdfToolTest {

    @Test
    void 줄바꿈을_LF로_통일하고_마지막_form_feed와_바깥_공백만_제거한다() {
        String rawText = " \r\n첫 문단\r\n\r\n둘째 문단\r\n\f";

        String normalized = PopplerPdfTool.normalizeText(rawText);

        assertEquals("첫 문단\n\n둘째 문단", normalized);
    }
}
