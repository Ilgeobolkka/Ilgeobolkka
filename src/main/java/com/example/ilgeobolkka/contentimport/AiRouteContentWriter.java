package com.example.ilgeobolkka.contentimport;

import com.example.ilgeobolkka.contentimport.embedding.EmbeddedAiRouteContent;
import com.example.ilgeobolkka.contentimport.validation.ValidatedAiRouteContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 검증·embedding이 끝난 `ai-route-v2`의 AI 메타데이터와 선수 관계를 한 트랜잭션으로 반영한다.
 *
 * <p>지원 상태는 건드리지 않는다. manifest는 후보만 정의하고 `ai_route_supported=true`는 품질 평가를
 * 통과한 뒤 별도 경로가 올린다.
 *
 * <p>페이지 본문(TEXT/IMAGE)은 이 writer가 만들지 않는다. 이미 있는 `book_page` 행에 AI 필드를 채우므로,
 * 본문 변환·적재가 먼저 끝나 있어야 한다.
 */
@Component
@Profile("!prod & (content-import | test)")
public class AiRouteContentWriter {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    AiRouteContentWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 이 contentVersion 이 이미 적재된 DB 인지 본다.
     *
     * <p>재적재는 지원 범위가 아니라 두 번째 실행은 실패해야 하는데, 그대로 두면 Embeddings 를 모두
     * 호출하고 본문까지 쓴 뒤에야 선수 관계 고유 제약에 걸린다. rollback 되므로 DB 는 그대로지만
     * 외부 호출은 이미 나간 뒤이고, 남는 것은 원인을 알려 주지 않는 중복 키 오류다.
     *
     * <p>판정을 {@code book.content_version} 으로 하는 것은 이 writer 가 올리는 값이 그것이기
     * 때문이다. 후보 도서가 없는 manifest 를 적재해도 이 값은 올라간다.
     */
    public boolean alreadyImported(String contentVersion) {
        Integer imported =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM book WHERE content_version = ?",
                        Integer.class,
                        contentVersion);
        return imported != null && imported > 0;
    }

    /**
     * @throws AiRouteContentImportException 트랜잭션을 열기 전 입력이 어긋난 경우. 이때 DB는 그대로다.
     */
    @Transactional
    public void write(ValidatedAiRouteContent content, EmbeddedAiRouteContent embedded) {
        requireConsistentVectors(content, embedded);

        for (ValidatedAiRouteContent.ValidatedBook book : content.books()) {
            updateBook(content, book);
            if (!book.aiRouteCandidate()) {
                continue;
            }
            Map<Integer, Long> pageIds = loadPageIds(book.bookId());
            updatePages(content, embedded, book, pageIds);
            insertPrerequisites(book);
        }
    }

    /**
     * 후보 페이지에 vector가 없거나 후보가 아닌 페이지에 vector가 있으면 실패한다. DB의
     * {@code ck_book_page_candidate_metadata}와 같은 계약을 트랜잭션 전에 먼저 확인해, 부분 적재 뒤
     * 제약에 걸려 rollback되는 대신 아무것도 시작하지 않는다.
     */
    private void requireConsistentVectors(
            ValidatedAiRouteContent content, EmbeddedAiRouteContent embedded) {
        require(content != null, "검증된 콘텐츠가 필요합니다.");
        require(embedded != null, "embedding batch가 필요합니다.");
        require(
                content.contentVersion().equals(embedded.contentVersion()),
                "콘텐츠 버전(%s)과 embedding batch 버전(%s)이 다릅니다."
                        .formatted(content.contentVersion(), embedded.contentVersion()));
        require(
                content.embeddingModel().equals(embedded.embeddingModel())
                        && content.embeddingDimensions() == embedded.embeddingDimensions(),
                "manifest와 embedding batch의 모델·차원이 다릅니다.");

        int expectedVectors = 0;
        for (ValidatedAiRouteContent.ValidatedBook book : content.books()) {
            for (ValidatedAiRouteContent.ValidatedPage page : book.pages()) {
                List<Double> vector = embedded.vectorOf(book.bookId(), page.pageNumber());
                if (page.aiRouteCandidatePage()) {
                    require(
                            vector != null,
                            "book %d p%d는 후보인데 vector가 없습니다."
                                    .formatted(book.bookId(), page.pageNumber()));
                    expectedVectors++;
                } else {
                    require(
                            vector == null,
                            "book %d p%d는 후보가 아닌데 vector가 있습니다."
                                    .formatted(book.bookId(), page.pageNumber()));
                }
            }
        }
        require(
                embedded.vectors().size() == expectedVectors,
                "embedding batch에 콘텐츠와 짝이 맞지 않는 vector가 있습니다: batch %d개, 후보 %d개"
                        .formatted(embedded.vectors().size(), expectedVectors));
    }

    private void updateBook(
            ValidatedAiRouteContent content, ValidatedAiRouteContent.ValidatedBook book) {
        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE book
                        SET content_version = ?,
                            ai_external_transfer_allowed = ?,
                            ai_data_policy_version = ?,
                            ai_route_supported = FALSE
                        WHERE id = ?
                        """,
                        content.contentVersion(),
                        book.aiRouteCandidate(),
                        content.dataPolicyVersion(),
                        book.bookId());
        require(updated == 1, "book %d를 찾지 못했습니다.".formatted(book.bookId()));
    }

    private Map<Integer, Long> loadPageIds(long bookId) {
        Map<Integer, Long> pageIds = new HashMap<>();
        jdbcTemplate
                .query(
                        "SELECT id, page_number FROM book_page WHERE book_id = ?",
                        (resultSet, rowNumber) ->
                                Map.entry(
                                        resultSet.getInt("page_number"),
                                        resultSet.getLong("id")),
                        bookId)
                .forEach(entry -> pageIds.put(entry.getKey(), entry.getValue()));
        return pageIds;
    }

    private void updatePages(
            ValidatedAiRouteContent content,
            EmbeddedAiRouteContent embedded,
            ValidatedAiRouteContent.ValidatedBook book,
            Map<Integer, Long> pageIds) {
        List<Object[]> updates = new ArrayList<>();
        for (ValidatedAiRouteContent.ValidatedPage page : book.pages()) {
            Long pageId = pageIds.get(page.pageNumber());
            require(
                    pageId != null,
                    "book %d p%d 행이 없습니다. 본문 적재가 먼저 끝나야 합니다."
                            .formatted(book.bookId(), page.pageNumber()));
            List<Double> vector = embedded.vectorOf(book.bookId(), page.pageNumber());
            boolean candidate = page.aiRouteCandidatePage();
            // 후보와 임베딩을 한 문장에 함께 쓴다. 행을 먼저 넣고 나중에 채우면 CHECK 제약에 걸린다.
            updates.add(
                    new Object[] {
                        page.analysisText(),
                        page.publicGuideTopic(),
                        page.estimatedReadingSeconds(),
                        candidate ? content.embeddingModel() : null,
                        candidate ? content.embeddingDimensions() : null,
                        candidate ? writeJson(vector) : null,
                        writeJson(page.duplicateGroupKeys()),
                        candidate,
                        pageId
                    });
        }
        int[] counts =
                jdbcTemplate.batchUpdate(
                        """
                        UPDATE book_page
                        SET ai_analysis_text = ?,
                            ai_public_guide_topic = ?,
                            estimated_reading_seconds = ?,
                            embedding_model = ?,
                            embedding_dimensions = ?,
                            embedding_json = ?,
                            duplicate_group_keys = ?,
                            ai_route_candidate = ?
                        WHERE id = ?
                        """,
                        updates);
        requireSingleRowChanges(counts, "페이지 AI 메타데이터");
    }

    private void insertPrerequisites(ValidatedAiRouteContent.ValidatedBook book) {
        List<Object[]> inserts = new ArrayList<>();
        for (ValidatedAiRouteContent.PrerequisiteEdge edge : book.prerequisiteEdges()) {
            inserts.add(
                    new Object[] {
                        book.bookId(), edge.beforePageNumber(), edge.afterPageNumber()
                    });
        }
        int[] counts =
                jdbcTemplate.batchUpdate(
                        """
                        INSERT INTO ai_route_prerequisite
                            (book_id, prerequisite_page_number, dependent_page_number)
                        VALUES (?, ?, ?)
                        """,
                        inserts);
        requireSingleRowChanges(counts, "선수 관계");
    }

    private String writeJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private void requireSingleRowChanges(int[] counts, String what) {
        for (int count : counts) {
            require(count == 1, "%s 적재 중 예상과 다른 행 수가 변경됐습니다.".formatted(what));
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AiRouteContentImportException(message);
        }
    }
}
