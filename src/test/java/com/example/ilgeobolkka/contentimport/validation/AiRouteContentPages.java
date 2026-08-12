package com.example.ilgeobolkka.contentimport.validation;

import com.example.ilgeobolkka.contentimport.manifest.AiRouteContentManifest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** 검증 테스트가 쓰는 페이지·도서 조립 헬퍼. */
final class AiRouteContentPages {

    private AiRouteContentPages() {}

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static AiRouteContentManifest.Page candidate(
            int pageNumber, String chapter, List<Integer> prerequisites) {
        return page(
                pageNumber,
                chapter,
                AiRouteContentManifest.ContentRole.CORE,
                true,
                prerequisites);
    }

    static AiRouteContentManifest.Page frontMatter(int pageNumber) {
        return page(
                pageNumber,
                "앞부분",
                AiRouteContentManifest.ContentRole.FRONT_MATTER,
                false,
                List.of());
    }

    static AiRouteContentManifest.Page page(
            int pageNumber,
            String chapter,
            AiRouteContentManifest.ContentRole role,
            boolean candidatePage,
            List<Integer> prerequisites) {
        String analysisText = "p%d 분석 텍스트".formatted(pageNumber);
        return new AiRouteContentManifest.Page(
                pageNumber,
                chapter,
                "%d절".formatted(pageNumber),
                List.of("개념 %d".formatted(pageNumber)),
                List.of(),
                role,
                candidatePage,
                analysisText,
                sha256(analysisText.getBytes(StandardCharsets.UTF_8)),
                "공개 주제 %d".formatted(pageNumber),
                60,
                prerequisites,
                List.of());
    }

    /**
     * 계약을 만족하는 최소 도서. 1페이지는 목차이고 2페이지부터 여섯 장에 나눠 담으며, 각 장의 첫
     * 페이지만 앞 장을 선수로 갖는 얕은 사슬을 만든다.
     */
    static AiRouteContentManifest.Book candidateBook(long bookId, String pdfPath, String pdfSha256) {
        int pageCount = 48;
        List<AiRouteContentManifest.Page> pages = new java.util.ArrayList<>();
        pages.add(frontMatter(1));
        Integer previousChapterHead = null;
        int perChapter = (pageCount - 1) / 6;
        for (int pageNumber = 2; pageNumber <= pageCount; pageNumber++) {
            int chapterIndex = Math.min(6, (pageNumber - 2) / perChapter + 1);
            boolean chapterHead = pageNumber == 2 || (pageNumber - 2) % perChapter == 0;
            List<Integer> prerequisites;
            if (chapterHead && previousChapterHead != null && chapterIndex <= 6) {
                prerequisites = List.of(previousChapterHead);
            } else if (chapterHead) {
                prerequisites = List.of();
            } else {
                prerequisites = List.of(pageNumber - 1);
            }
            if (chapterHead) {
                previousChapterHead = pageNumber;
            }
            pages.add(candidate(pageNumber, "%d장".formatted(chapterIndex), prerequisites));
        }
        return new AiRouteContentManifest.Book(
                bookId, "도서 %d".formatted(bookId), pdfPath, pdfSha256, pageCount, true, true, pages);
    }
}
