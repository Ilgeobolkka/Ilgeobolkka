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
 * 소장 → 활성 대여 → 잠금 → 재확인 → 신규 대여·차감 순서는
 * {@code docs/prd/product-policy.md#페이지-열기-처리-순서와-원자성}을 따른다.
 *
 * <p>두 진입점은 {@link Isolation#READ_COMMITTED}로 연다. InnoDB 기본값인 REPEATABLE READ에서는
 * 잠금 전 첫 확인이 고정한 스냅샷을 잠금 뒤 재확인도 그대로 읽어, 먼저 커밋한 동시 요청이 만든
 * 대여가 보이지 않고 중복 차감된다. 재확인이 매번 최신 커밋을 읽어야 정책의 "잠금 뒤 다시 확인해
 * 한 번만 차감한다"가 성립한다.
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
        return openPage(readerId, page, viewerSessionId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OpenPageResponse movePage(long readerId, UUID viewerSessionId, int pageNumber) {
        ReadingSession currentSession =
                readingSessionService.getCurrentSession(readerId, viewerSessionId);
        BookPage page = bookService.findPage(currentSession.getBookId(), pageNumber);
        return openPage(readerId, page, viewerSessionId);
    }

    /**
     * 잠금을 얻은 뒤 서버 시각을 다시 읽는다. 잠금 대기는 앞선 요청이 끝날 때까지 이어지므로, 잠금
     * 전에 읽은 시각으로 대여를 만들면 대기한 만큼 30일이 줄고 {@code rentedAt}과 잉크 원장 시각이
     * 실제 차감보다 앞선다. 정책의 "대여 기간은 차감이 완료된 서버 시각부터 30일"을 지키려면 재확인과
     * 대여·차감이 같은 시각을 써야 한다.
     */
    private OpenPageResponse openPage(long readerId, BookPage page, UUID viewerSessionId) {
        Optional<OpenPageResponse> freeAccess =
                tryFreeAccess(readerId, page, viewerSessionId, clock.instant());
        if (freeAccess.isPresent()) {
            return freeAccess.get();
        }

        inkService.lockAccount(readerId);
        Instant chargedAt = clock.instant();
        freeAccess = tryFreeAccess(readerId, page, viewerSessionId, chargedAt);
        if (freeAccess.isPresent()) {
            return freeAccess.get();
        }

        return chargeNewRental(readerId, page, viewerSessionId, chargedAt);
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
