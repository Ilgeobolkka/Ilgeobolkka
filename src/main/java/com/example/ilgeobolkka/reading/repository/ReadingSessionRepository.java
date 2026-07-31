package com.example.ilgeobolkka.reading.repository;

import com.example.ilgeobolkka.reading.entity.ReadingSession;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReadingSessionRepository extends JpaRepository<ReadingSession, Long> {

    Optional<ReadingSession> findByReaderId(long readerId);

    /**
     * 독자당 하나인 현재 세션을 발급하거나 새 뷰어로 교체한다. 소장·활성 대여로 페이지를 여는 경로는
     * 잉크 계정을 잠그지 않으므로 같은 독자의 동시 요청이 겹칠 수 있다. 조회 후 저장으로 나누면 두
     * 요청이 나란히 INSERT를 시도해 {@code uk_reading_session_reader}가 깨지므로 한 문장으로 처리한다.
     */
    @Modifying(flushAutomatically = true)
    @Query(
            value =
                    """
                    INSERT INTO reading_session
                        (reader_id, book_id, current_page_number, viewer_session_id, updated_at)
                    VALUES (:readerId, :bookId, :pageNumber, :viewerSessionId, :updatedAt)
                    AS incoming
                    ON DUPLICATE KEY UPDATE
                        book_id = incoming.book_id,
                        current_page_number = incoming.current_page_number,
                        viewer_session_id = incoming.viewer_session_id,
                        updated_at = incoming.updated_at
                    """,
            nativeQuery = true)
    void upsertCurrentSession(
            @Param("readerId") long readerId,
            @Param("bookId") long bookId,
            @Param("pageNumber") int pageNumber,
            @Param("viewerSessionId") String viewerSessionId,
            @Param("updatedAt") Instant updatedAt);

    /**
     * 요청한 뷰어가 아직 현재 세션일 때만 위치를 옮기고 옮긴 행 수를 돌려준다. 뷰어 확인과 갱신을
     * 한 문장에 두어야, 확인을 통과한 뒤 새 뷰어가 세션을 교체한 지연 요청이 이전 뷰어를 다시
     * 현재 세션으로 되살리지 못한다.
     */
    @Modifying(flushAutomatically = true)
    @Query(
            value =
                    """
                    UPDATE reading_session
                    SET book_id = :bookId,
                        current_page_number = :pageNumber,
                        updated_at = :updatedAt
                    WHERE reader_id = :readerId AND viewer_session_id = :viewerSessionId
                    """,
            nativeQuery = true)
    int moveCurrentViewer(
            @Param("readerId") long readerId,
            @Param("bookId") long bookId,
            @Param("pageNumber") int pageNumber,
            @Param("viewerSessionId") String viewerSessionId,
            @Param("updatedAt") Instant updatedAt);

    @Modifying
    @Query("delete from ReadingSession readingSession where readingSession.readerId = :readerId")
    int deleteByReaderId(@Param("readerId") long readerId);
}
