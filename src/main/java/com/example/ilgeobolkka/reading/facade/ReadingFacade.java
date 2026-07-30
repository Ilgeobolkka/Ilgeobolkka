package com.example.ilgeobolkka.reading.facade;

import com.example.ilgeobolkka.book.entity.BookPage;
import com.example.ilgeobolkka.book.service.BookService;
import com.example.ilgeobolkka.ink.service.InkService;
import com.example.ilgeobolkka.library.service.LibraryService;
import com.example.ilgeobolkka.ownership.service.OwnershipService;
import com.example.ilgeobolkka.reading.dto.OpenPageResponse;
import com.example.ilgeobolkka.reading.entity.ReadingSession;
import com.example.ilgeobolkka.reading.service.ReadingSessionService;
import com.example.ilgeobolkka.rental.entity.PageRental;
import com.example.ilgeobolkka.rental.service.RentalService;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션 생성(POST)과 페이지 이동(PATCH)이 공유하는 원자적 페이지 열기 유스케이스.
 * 잠금 → 소장 → 활성 대여 → 신규 대여·차감 순서는
 * {@code docs/prd/product-policy.md#페이지-열기-처리-순서와-원자성}을 따른다.
 *
 * <p>독자당 하나뿐인 {@code InkAccount} 행 잠금이 같은 독자의 페이지 열기를 직렬화하는 유일한
 * 지점이다. 소장·활성 대여처럼 차감이 없는 경로도 {@code ReadingSession}과 {@code LibraryEntry}를
 * 쓰므로, 잠금 밖에서 처리하면 같은 독자의 동시 요청이 두 테이블의 유니크 제약을 깨뜨린다.
 *
 * <p>두 진입점은 {@link Isolation#READ_COMMITTED}로 연다. InnoDB 기본값인 REPEATABLE READ에서는
 * 잠금보다 먼저 실행하는 도서·세션 조회가 트랜잭션 스냅샷을 고정해, 잠금 뒤 확인도 그 스냅샷을
 * 읽는다. 그러면 먼저 커밋한 동시 요청이 만든 대여가 보이지 않아 1잉크가 두 번 차감된다.
 */
@Service
@RequiredArgsConstructor
public class ReadingFacade {

    private final BookService bookService;
    private final OwnershipService ownershipService;
    private final RentalService rentalService;
    private final InkService inkService;
    private final ReadingSessionService readingSessionService;
    private final LibraryService libraryService;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OpenPageResponse openNewSession(long readerId, long bookId, int pageNumber) {
        BookPage page = bookService.findPage(bookId, pageNumber);
        UUID viewerSessionId = UUID.randomUUID();
        return openPage(readerId, page, viewerSessionId, clock.instant());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OpenPageResponse movePage(long readerId, UUID viewerSessionId, int pageNumber) {
        ReadingSession currentSession =
                readingSessionService.getCurrentSession(readerId, viewerSessionId);
        BookPage page = bookService.findPage(currentSession.getBookId(), pageNumber);
        return openPage(readerId, page, viewerSessionId, clock.instant());
    }

    private OpenPageResponse openPage(
            long readerId, BookPage page, UUID viewerSessionId, Instant now) {
        inkService.lockAccount(readerId);
        return tryFreeAccess(readerId, page, viewerSessionId, now)
                .orElseGet(() -> chargeNewRental(readerId, page, viewerSessionId, now));
    }

    private Optional<OpenPageResponse> tryFreeAccess(
            long readerId, BookPage page, UUID viewerSessionId, Instant now) {
        if (ownershipService.isOwned(readerId, page.getBookId())) {
            recordVisit(readerId, page, viewerSessionId, now);
            int balance = inkService.getBalance(readerId);
            return Optional.of(OpenPageResponse.owned(viewerSessionId, page, balance));
        }

        return rentalService
                .findActiveRental(readerId, page.getId(), now)
                .map(
                        rental -> {
                            recordVisit(readerId, page, viewerSessionId, now);
                            int balance = inkService.getBalance(readerId);
                            return OpenPageResponse.activeRental(
                                    viewerSessionId, page, rental, balance);
                        });
    }

    private OpenPageResponse chargeNewRental(
            long readerId, BookPage page, UUID viewerSessionId, Instant now) {
        PageRental rental = rentalService.startRental(readerId, page.getId(), now);
        inkService.deduct(readerId, rental.getId(), now);
        int balance = inkService.getBalance(readerId);
        recordVisit(readerId, page, viewerSessionId, now);
        return OpenPageResponse.newRental(viewerSessionId, page, rental, balance);
    }

    private void recordVisit(long readerId, BookPage page, UUID viewerSessionId, Instant now) {
        readingSessionService.openOrReplace(
                readerId, page.getBookId(), page.getPageNumber(), viewerSessionId, now);
        libraryService.recordVisit(readerId, page.getBookId(), page.getPageNumber(), now);
    }
}
