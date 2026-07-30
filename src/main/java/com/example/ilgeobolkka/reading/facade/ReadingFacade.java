package com.example.ilgeobolkka.reading.facade;

import com.example.ilgeobolkka.book.entity.BookPage;
import com.example.ilgeobolkka.book.service.BookService;
import com.example.ilgeobolkka.ink.service.InkService;
import com.example.ilgeobolkka.ownership.service.OwnershipService;
import com.example.ilgeobolkka.reading.dto.OpenPageResponse;
import com.example.ilgeobolkka.reading.entity.ReadingSession;
import com.example.ilgeobolkka.reading.service.ReadingSessionService;
import com.example.ilgeobolkka.rental.entity.PageRental;
import com.example.ilgeobolkka.rental.service.PageRentalService;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션 생성(POST)과 페이지 이동(PATCH)이 공유하는 페이지 열기 유스케이스의 단일 진입점.
 *
 * <p>정책상 처리 순서(docs/prd/product-policy.md "페이지 열기 처리 순서와 원자성")의 1~4단계(도서·페이지
 * 유효성, 온라인 소장은 즉시 제공, 활성 대여 확인, 잠금 뒤 소장·활성 대여 재확인)까지 이번 서브태스크에서
 * 구현한다. 5~7단계(잉크 잔액 확인, 1잉크 차감, {@code PageRental}/{@code LibraryEntry} 저장)는
 * 뒤따르는 서브태스크의 몫이다.
 *
 * <p>MySQL InnoDB의 기본 격리 수준(REPEATABLE READ)에서는 잠금 획득(3단계) 뒤에도 일반 조회가
 * 트랜잭션 시작 시점의 스냅숏을 그대로 읽어 재확인(4단계)이 방금 커밋된 소장·대여를 놓칠 수 있다.
 * 이 트랜잭션만 {@link Isolation#READ_COMMITTED}로 낮춰 잠금 해제 직후의 재확인 조회가 항상 최신
 * 커밋 데이터를 읽게 한다. 새 잠금 쿼리를 추가하지 않고 기존 {@code InkAccountRepository
 * .findByReaderIdForUpdate} 하나만으로 재확인이 정확히 동작하도록 하는 최소 변경이다.
 */
@Service
@RequiredArgsConstructor
public class ReadingFacade {

    private final BookService bookService;
    private final OwnershipService ownershipService;
    private final ReadingSessionService readingSessionService;
    private final InkService inkService;
    private final PageRentalService pageRentalService;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OpenPageResponse openPage(long readerId, OpenPageViewer viewer, int pageNumber) {
        ResolvedViewer resolvedViewer = resolveViewer(readerId, viewer);
        BookPage page = bookService.findPage(resolvedViewer.bookId(), pageNumber);

        if (!ownershipService.isOwned(readerId, resolvedViewer.bookId())) {
            return provideRentedPage(readerId, resolvedViewer, page);
        }

        return provideOwnedPage(readerId, resolvedViewer, page);
    }

    private ResolvedViewer resolveViewer(long readerId, OpenPageViewer viewer) {
        return switch (viewer) {
            case OpenPageViewer.NewViewer newViewer ->
                    new ResolvedViewer(newViewer.bookId(), null);
            case OpenPageViewer.ExistingViewer existingViewer -> {
                ReadingSession session = readingSessionService.findCurrentSession(
                        readerId, existingViewer.viewerSessionId());
                yield new ResolvedViewer(session.getBookId(), session);
            }
        };
    }

    private OpenPageResponse provideOwnedPage(
            long readerId, ResolvedViewer resolvedViewer, BookPage page) {
        ReadingSession session = openOrMoveSession(readerId, resolvedViewer, page);
        int inkBalance = inkService.getBalance(readerId);

        return OpenPageResponse.owned(session, page, inkBalance);
    }

    /**
     * 활성 대여 확인(3단계) → 없으면 {@code InkAccount} 잠금 → 잠금 뒤 소장·활성 대여 재확인(4단계)까지
     * 처리한다(INV-002, INV-012). 재확인 후에도 권한이 없으면 잉크 잔액 확인·1잉크 차감·{@code
     * PageRental} 저장(정책 5~7단계)은 뒤따르는 서브태스크의 몫이므로 스텁 예외를 유지한다.
     */
    private OpenPageResponse provideRentedPage(
            long readerId, ResolvedViewer resolvedViewer, BookPage page) {
        Instant now = clock.instant();

        Optional<PageRental> activeRental =
                pageRentalService.findActive(readerId, page.getId(), now);
        if (activeRental.isPresent()) {
            return provideAlreadyRentedPage(readerId, resolvedViewer, page, activeRental.get());
        }

        inkService.lockAccount(readerId);

        if (ownershipService.isOwned(readerId, resolvedViewer.bookId())) {
            return provideOwnedPage(readerId, resolvedViewer, page);
        }

        Optional<PageRental> reconfirmedRental =
                pageRentalService.findActive(readerId, page.getId(), now);
        if (reconfirmedRental.isPresent()) {
            return provideAlreadyRentedPage(
                    readerId, resolvedViewer, page, reconfirmedRental.get());
        }

        throw new UnsupportedOperationException(
                "잉크 잔액 확인과 새 대여 저장(정책 5~7단계)은 아직 구현되지 않았습니다.");
    }

    private OpenPageResponse provideAlreadyRentedPage(
            long readerId, ResolvedViewer resolvedViewer, BookPage page, PageRental rental) {
        ReadingSession session = openOrMoveSession(readerId, resolvedViewer, page);
        int inkBalance = inkService.getBalance(readerId);

        return OpenPageResponse.rented(
                session, page, inkBalance, rental.getRentedAt(), rental.getExpiresAt());
    }

    private ReadingSession openOrMoveSession(
            long readerId, ResolvedViewer resolvedViewer, BookPage page) {
        return resolvedViewer.currentSession() == null
                ? readingSessionService.openNewSession(readerId, page, clock.instant())
                : readingSessionService.moveCurrentSession(
                        resolvedViewer.currentSession(), page, clock.instant());
    }

    private record ResolvedViewer(long bookId, ReadingSession currentSession) {}
}
