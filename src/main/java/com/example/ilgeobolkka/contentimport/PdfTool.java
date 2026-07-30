package com.example.ilgeobolkka.contentimport;

import java.nio.file.Path;

interface PdfTool {

    String pdftotextVersion();

    String pdftoppmVersion();

    String extractText(Path pdfPath, int pageNumber);

    boolean pageExists(Path pdfPath, int pageNumber);

    void renderJpeg(Path pdfPath, int pageNumber, Path outputPrefix);
}
