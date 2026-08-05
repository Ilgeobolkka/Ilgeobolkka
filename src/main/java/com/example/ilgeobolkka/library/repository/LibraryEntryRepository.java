package com.example.ilgeobolkka.library.repository;

import com.example.ilgeobolkka.library.entity.LibraryEntry;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LibraryEntryRepository extends JpaRepository<LibraryEntry, Long> {

    @Query(
            value =
                    """
                    SELECT entry.book_id AS bookId,
                           book.cover_image_path AS coverImagePath,
                           book.title AS title,
                           book.category AS category,
                           entry.last_page_number AS lastPageNumber,
                           rental.rented_at AS rentedAt,
                           rental.expires_at AS expiresAt,
                           ownership.id AS ownershipId
                    FROM library_entry entry
                    JOIN book_page last_page
                      ON last_page.book_id = entry.book_id
                     AND last_page.page_number = entry.last_page_number
                    JOIN book ON book.id = entry.book_id
                    LEFT JOIN book_ownership ownership
                      ON ownership.reader_id = entry.reader_id
                     AND ownership.book_id = entry.book_id
                    LEFT JOIN page_rental rental ON rental.id = (
                        SELECT latest_rental.id
                        FROM page_rental latest_rental
                        WHERE latest_rental.reader_id = entry.reader_id
                          AND latest_rental.book_page_id = last_page.id
                        ORDER BY latest_rental.rented_at DESC, latest_rental.id DESC
                        LIMIT 1
                    )
                    WHERE entry.reader_id = :readerId
                    ORDER BY entry.updated_at DESC, entry.id DESC
                    """,
            nativeQuery = true)
    List<LibraryEntryView> findEntriesByReaderId(@Param("readerId") long readerId);

    /**
     * 마지막 열람 위치를 만들거나 옮긴다. 소장·활성 대여로 페이지를 여는 경로는 잉크 계정을 잠그지
     * 않으므로 같은 독자의 동시 요청이 겹칠 수 있다. 조회 후 저장으로 나누면 두 요청이 나란히
     * INSERT를 시도해 {@code uk_library_entry_reader_book}이 깨지므로 한 문장으로 처리한다.
     */
    @Modifying(flushAutomatically = true)
    @Query(
            value =
                    """
                    INSERT INTO library_entry (reader_id, book_id, last_page_number, updated_at)
                    VALUES (:readerId, :bookId, :pageNumber, :updatedAt)
                    AS incoming
                    ON DUPLICATE KEY UPDATE
                        last_page_number = incoming.last_page_number,
                        updated_at = incoming.updated_at
                    """,
            nativeQuery = true)
    void upsertLastReadPage(
            @Param("readerId") long readerId,
            @Param("bookId") long bookId,
            @Param("pageNumber") int pageNumber,
            @Param("updatedAt") Instant updatedAt);

    @Modifying(flushAutomatically = true)
    @Query(
            value =
                    """
                    INSERT INTO library_entry (reader_id, book_id, last_page_number, updated_at)
                    VALUES (:readerId, :bookId, 1, :updatedAt)
                    AS incoming
                    ON DUPLICATE KEY UPDATE
                        book_id = incoming.book_id
                    """,
            nativeQuery = true)
    void insertOwnedBookIfAbsent(
            @Param("readerId") long readerId,
            @Param("bookId") long bookId,
            @Param("updatedAt") Instant updatedAt);
}
