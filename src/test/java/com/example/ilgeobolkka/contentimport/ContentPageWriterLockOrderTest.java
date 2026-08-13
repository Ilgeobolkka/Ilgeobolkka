package com.example.ilgeobolkka.contentimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.example.ilgeobolkka.book.entity.BookPageContentType;
import com.example.ilgeobolkka.contentimport.embedding.EmbeddedAiRouteContent;
import com.example.ilgeobolkka.contentimport.validation.ValidatedAiRouteContent;
import com.example.ilgeobolkka.demo.DemoBookCatalog;
import com.example.ilgeobolkka.demo.DemoBookWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

/**
 * manifest가 도서를 어떤 순서로 나열하든 {@code book}·{@code ai_route_prerequisite} 행을 항상 bookId
 * 오름차순으로 잠가야 한다. 겹치는 도서를 다른 순서로 나열한 manifest를 동시에 적재할 때 InnoDB 데드락이
 * 나는 것을 막는 계약이라 DB 최종 상태로는 확인할 수 없고 문장 순서로 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class ContentPageWriterLockOrderTest {

    private static final String VERSION = "ai-route-v2";
    private static final String POLICY = "OPENAI_DEFAULT_RETENTION_V1";
    private static final String MODEL = "text-embedding-3-small";
    private static final int DIMENSIONS = 3;
    private static final long FIRST_ID = 1L;
    private static final long SECOND_ID = 41L;

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private DemoBookCatalog demoBookCatalog;
    @Mock private DemoBookWriter demoBookWriter;

    private final List<Object[]> bookInserts = new ArrayList<>();
    private final Map<String, List<Object[]>> batchesBySql = new LinkedHashMap<>();
    private final List<Object> prerequisiteDeletes = new ArrayList<>();

    @Test
    void manifest가_도서를_역순으로_나열해도_book_행을_bookId_오름차순으로_잠근다() {
        stubJdbcTemplate();
        ContentPageWriter writer =
                new ContentPageWriter(
                        jdbcTemplate, demoBookCatalog, demoBookWriter, new ObjectMapper());

        writer.write(command());

        assertEquals(
                List.of(FIRST_ID, SECOND_ID),
                bookIdsOf(batchBySqlFragment("UPDATE book"), 5),
                "book UPDATE는 manifest 순서가 아니라 bookId 오름차순이어야 합니다.");
    }

    @Test
    void manifest가_도서를_역순으로_나열해도_도서_생성과_선수_관계_삭제를_bookId_오름차순으로_잠근다() {
        stubJdbcTemplate();
        ContentPageWriter writer =
                new ContentPageWriter(
                        jdbcTemplate, demoBookCatalog, demoBookWriter, new ObjectMapper());

        writer.write(command());

        assertEquals(
                List.of(FIRST_ID, SECOND_ID),
                bookInserts.stream().map(row -> (Object) row[0]).toList(),
                "book INSERT는 bookId 오름차순이어야 합니다.");
        assertEquals(
                List.of(FIRST_ID, SECOND_ID),
                prerequisiteDeletes,
                "선수 관계 DELETE는 bookId 오름차순이어야 합니다.");
    }

    private void stubJdbcTemplate() {
        when(demoBookCatalog.books()).thenReturn(List.of(seed(FIRST_ID), seed(SECOND_ID)));
        // 기존 도서·페이지가 없는 DB로 두면 생성·삽입 경로가 모두 지나간다.
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<Object>>any()))
                .thenReturn(List.of());
        doAnswer(
                        invocation -> {
                            List<DemoBookCatalog.BookSeed> seeds = invocation.getArgument(1);
                            seeds.forEach(seed -> bookInserts.add(new Object[] {seed.id()}));
                            return new int[][] {filledWithOne(seeds.size())};
                        })
                .when(jdbcTemplate)
                .batchUpdate(anyString(), anyList(), anyInt(), any());
        doAnswer(
                        invocation -> {
                            String sql = invocation.getArgument(0);
                            List<Object[]> rows = invocation.getArgument(1);
                            batchesBySql.put(sql, List.copyOf(rows));
                            return filledWithOne(rows.size());
                        })
                .when(jdbcTemplate)
                .batchUpdate(anyString(), anyList());
        doAnswer(
                        invocation -> {
                            prerequisiteDeletes.add(invocation.getArgument(1));
                            return 1;
                        })
                .when(jdbcTemplate)
                .update(anyString(), any(Object.class));
    }

    private List<Object[]> batchBySqlFragment(String fragment) {
        return batchesBySql.entrySet().stream()
                .filter(entry -> entry.getKey().contains(fragment))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError(fragment + " batch를 찾지 못했습니다."));
    }

    private List<Object> bookIdsOf(List<Object[]> rows, int bookIdIndex) {
        return rows.stream().map(row -> row[bookIdIndex]).toList();
    }

    private int[] filledWithOne(int size) {
        int[] counts = new int[size];
        Arrays.fill(counts, 1);
        return counts;
    }

    private DemoBookCatalog.BookSeed seed(long bookId) {
        return new DemoBookCatalog.BookSeed(
                bookId, "소설", "제목 " + bookId, "저자", "설명", "cover.jpg", 2, 10_000);
    }

    /** 검증 결과의 도서를 bookId 내림차순으로 담아 manifest 순서와 잠금 순서를 갈라놓는다. */
    private AiRouteContentImportCommand command() {
        return AiRouteContentImportCommand.create(batch(), content(), embedded());
    }

    private ContentBatch batch() {
        return new ContentBatch(
                VERSION,
                "b".repeat(64),
                List.of(convertedBook(FIRST_ID), convertedBook(SECOND_ID)));
    }

    private ConvertedBook convertedBook(long bookId) {
        List<ConvertedPage> pages =
                List.of(
                        new ConvertedPage(
                                bookId, 1, BookPageContentType.TEXT, "본문 1", null, null),
                        new ConvertedPage(
                                bookId, 2, BookPageContentType.TEXT, "본문 2", null, null));
        return new ConvertedBook(bookId, "a".repeat(64), 2, pages);
    }

    private ValidatedAiRouteContent content() {
        return new ValidatedAiRouteContent(
                VERSION,
                POLICY,
                MODEL,
                DIMENSIONS,
                List.of(candidateBook(SECOND_ID), nonCandidateBook(FIRST_ID)));
    }

    private ValidatedAiRouteContent.ValidatedBook candidateBook(long bookId) {
        return new ValidatedAiRouteContent.ValidatedBook(
                bookId,
                "제목 " + bookId,
                2,
                true,
                true,
                List.of(page(1, true), page(2, false)),
                List.of(new ValidatedAiRouteContent.PrerequisiteEdge(1, 2)));
    }

    private ValidatedAiRouteContent.ValidatedBook nonCandidateBook(long bookId) {
        return new ValidatedAiRouteContent.ValidatedBook(
                bookId, "제목 " + bookId, 2, false, false, List.of(), List.of());
    }

    private ValidatedAiRouteContent.ValidatedPage page(int pageNumber, boolean candidate) {
        return new ValidatedAiRouteContent.ValidatedPage(
                pageNumber,
                candidate,
                "분석 " + pageNumber,
                "공개 주제 " + pageNumber,
                40,
                candidate ? List.of("same-concept") : List.of());
    }

    private EmbeddedAiRouteContent embedded() {
        Map<EmbeddedAiRouteContent.PageKey, List<Double>> vectors = new LinkedHashMap<>();
        vectors.put(
                new EmbeddedAiRouteContent.PageKey(SECOND_ID, 1, VERSION),
                List.of(0.1, 0.2, 0.3));
        return new EmbeddedAiRouteContent(VERSION, MODEL, DIMENSIONS, vectors);
    }
}
