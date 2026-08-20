package com.example.ilgeobolkka.contentimport;

import static com.example.ilgeobolkka.contentimport.manifest.ContentManifest.AI_ROUTE_CONTENT_VERSION;

import com.example.ilgeobolkka.contentimport.embedding.EmbeddedAiRouteContent;
import com.example.ilgeobolkka.contentimport.validation.ValidatedAiRouteContent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 변환 결과·C02 검증 결과·C03 vector가 정확히 맞물린 경우에만 만드는 DB 적재 명령. */
final class AiRouteContentImportCommand {

    private final ContentBatch batch;
    private final ValidatedAiRouteContent content;
    private final EmbeddedAiRouteContent embedded;

    private AiRouteContentImportCommand(
            ContentBatch batch,
            ValidatedAiRouteContent content,
            EmbeddedAiRouteContent embedded) {
        this.batch = batch;
        this.content = content;
        this.embedded = embedded;
    }

    static AiRouteContentImportCommand create(
            ContentBatch batch,
            ValidatedAiRouteContent content,
            EmbeddedAiRouteContent embedded) {
        require(batch != null, "변환된 콘텐츠가 필요합니다.");
        require(content != null, "검증된 AI 콘텐츠가 필요합니다.");
        require(embedded != null, "embedding batch가 필요합니다.");
        require(
                AI_ROUTE_CONTENT_VERSION.equals(batch.contentVersion())
                        && batch.contentVersion().equals(content.contentVersion())
                        && content.contentVersion().equals(embedded.contentVersion()),
                "변환·검증·embedding의 콘텐츠 버전이 모두 ai-route-v2여야 합니다.");
        require(
                content.dataPolicyVersion() != null
                        && !content.dataPolicyVersion().isBlank()
                        && content.embeddingModel() != null
                        && !content.embeddingModel().isBlank()
                        && content.embeddingDimensions() > 0,
                "AI 콘텐츠 정책·embedding 모델·차원이 필요합니다.");
        require(
                content.embeddingModel().equals(embedded.embeddingModel())
                        && content.embeddingDimensions() == embedded.embeddingDimensions(),
                "검증 결과와 embedding batch의 모델·차원이 다릅니다.");

        Map<Long, ConvertedBook> convertedByBookId = convertedBooks(batch);
        Set<Long> validatedBookIds = new HashSet<>();
        int expectedVectorCount = 0;
        for (ValidatedAiRouteContent.ValidatedBook book : content.books()) {
            require(book != null, "검증 결과에 null 도서가 있습니다.");
            require(
                    validatedBookIds.add(book.bookId()),
                    "검증 결과의 bookId가 중복됩니다: " + book.bookId());
            ConvertedBook converted = convertedByBookId.get(book.bookId());
            require(converted != null, "검증된 도서의 변환 결과가 없습니다: " + book.bookId());
            require(
                    book.totalPageCount() > 0
                            && book.totalPageCount() == converted.totalPageCount()
                            && converted.pages() != null
                            && converted.pages().size() == converted.totalPageCount(),
                    "book %d의 변환 페이지 수가 검증 결과와 다릅니다.".formatted(book.bookId()));

            if (!book.aiRouteCandidate()) {
                require(
                        book.pages().isEmpty(),
                        "비후보 book %d의 검증 페이지는 비어 있어야 합니다.".formatted(book.bookId()));
                require(
                        book.prerequisiteEdges().isEmpty(),
                        "비후보 book %d의 선수 관계는 비어 있어야 합니다.".formatted(book.bookId()));
                continue;
            }
            require(
                    book.aiExternalTransferAllowed(),
                    "book %d는 외부 전송 권리가 없어 적재할 수 없습니다.".formatted(book.bookId()));

            Set<Integer> convertedPageNumbers = new HashSet<>();
            for (ConvertedPage page : converted.pages()) {
                require(
                        page != null
                                && page.bookId() == book.bookId()
                                && convertedPageNumbers.add(page.pageNumber()),
                        "book %d의 변환 페이지 key가 올바르지 않습니다.".formatted(book.bookId()));
            }
            Set<Integer> validatedPageNumbers = new HashSet<>();
            Set<Integer> candidatePageNumbers = new HashSet<>();
            for (ValidatedAiRouteContent.ValidatedPage page : book.pages()) {
                require(
                        page != null && validatedPageNumbers.add(page.pageNumber()),
                        "book %d의 검증 페이지 key가 중복됩니다.".formatted(book.bookId()));
                List<Double> vector = embedded.vectorOf(book.bookId(), page.pageNumber());
                if (!page.aiRouteCandidatePage()) {
                    require(
                            vector == null,
                            "book %d p%d는 후보가 아닌데 vector가 있습니다."
                                    .formatted(book.bookId(), page.pageNumber()));
                    continue;
                }
                candidatePageNumbers.add(page.pageNumber());
                require(
                        vector != null,
                        "book %d p%d는 후보인데 vector가 없습니다."
                                .formatted(book.bookId(), page.pageNumber()));
                requireValidVector(book.bookId(), page.pageNumber(), vector, content.embeddingDimensions());
                expectedVectorCount++;
            }
            requireValidEdges(
                    book.bookId(),
                    validatedPageNumbers,
                    candidatePageNumbers,
                    book.prerequisiteEdges());
            require(
                    convertedPageNumbers.equals(validatedPageNumbers),
                    "book %d의 변환 페이지와 검증 페이지 key가 다릅니다.".formatted(book.bookId()));
        }
        require(
                convertedByBookId.keySet().equals(validatedBookIds),
                "변환된 도서와 검증된 도서 key가 다릅니다.");
        require(
                embedded.vectors().size() == expectedVectorCount,
                "embedding batch에 콘텐츠와 짝이 맞지 않는 vector가 있습니다: batch %d개, 후보 %d개"
                        .formatted(embedded.vectors().size(), expectedVectorCount));
        return new AiRouteContentImportCommand(batch, content, embedded);
    }

    ContentBatch batch() {
        return batch;
    }

    ValidatedAiRouteContent content() {
        return content;
    }

    EmbeddedAiRouteContent embedded() {
        return embedded;
    }

    private static void requireValidEdges(
            long bookId,
            Set<Integer> pageNumbers,
            Set<Integer> candidatePageNumbers,
            List<ValidatedAiRouteContent.PrerequisiteEdge> edges) {
        Map<Integer, List<Integer>> dependents = new HashMap<>();
        Map<Integer, Integer> inDegree = new HashMap<>();
        for (Integer pageNumber : pageNumbers) {
            dependents.put(pageNumber, new ArrayList<>());
            inDegree.put(pageNumber, 0);
        }

        Set<ValidatedAiRouteContent.PrerequisiteEdge> uniqueEdges = new HashSet<>();
        for (ValidatedAiRouteContent.PrerequisiteEdge edge : edges) {
            require(edge != null, "book %d의 선수 관계에 null이 있습니다.".formatted(bookId));
            int before = edge.beforePageNumber();
            int after = edge.afterPageNumber();
            require(
                    pageNumbers.contains(before) && pageNumbers.contains(after),
                    "book %d의 선수 관계 %d -> %d에 없는 페이지가 있습니다."
                            .formatted(bookId, before, after));
            require(
                    before != after,
                    "book %d p%d는 자기 자신을 선수로 가질 수 없습니다."
                            .formatted(bookId, after));
            require(
                    uniqueEdges.add(edge),
                    "book %d의 선수 관계 %d -> %d가 중복됩니다."
                            .formatted(bookId, before, after));
            require(
                    candidatePageNumbers.contains(before),
                    "book %d의 선수 페이지 %d는 후보 페이지여야 합니다."
                            .formatted(bookId, before));
            dependents.get(before).add(after);
            inDegree.merge(after, 1, Integer::sum);
        }

        Deque<Integer> ready = new ArrayDeque<>();
        inDegree.forEach(
                (pageNumber, degree) -> {
                    if (degree == 0) {
                        ready.add(pageNumber);
                    }
                });
        int visited = 0;
        while (!ready.isEmpty()) {
            int current = ready.removeFirst();
            visited++;
            for (Integer dependent : dependents.get(current)) {
                if (inDegree.merge(dependent, -1, Integer::sum) == 0) {
                    ready.add(dependent);
                }
            }
        }
        require(visited == pageNumbers.size(), "book %d의 선수 관계에 순환이 있습니다.".formatted(bookId));
    }

    private static Map<Long, ConvertedBook> convertedBooks(ContentBatch batch) {
        require(batch.books() != null, "변환된 도서 목록이 필요합니다.");
        Map<Long, ConvertedBook> convertedByBookId = new HashMap<>();
        for (ConvertedBook book : batch.books()) {
            require(book != null, "변환 결과에 null 도서가 있습니다.");
            require(
                    convertedByBookId.put(book.bookId(), book) == null,
                    "변환 결과의 bookId가 중복됩니다: " + book.bookId());
        }
        return convertedByBookId;
    }

    private static void requireValidVector(
            long bookId, int pageNumber, List<Double> vector, int dimensions) {
        require(
                vector.size() == dimensions,
                "book %d p%d vector 길이가 %d가 아니라 %d입니다."
                        .formatted(bookId, pageNumber, dimensions, vector.size()));
        for (Double value : vector) {
            require(
                    value != null && Double.isFinite(value),
                    "book %d p%d vector에 유한하지 않은 값이 있습니다."
                            .formatted(bookId, pageNumber));
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AiRouteContentImportException(message);
        }
    }
}
