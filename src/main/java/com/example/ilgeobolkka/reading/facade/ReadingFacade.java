package com.example.ilgeobolkka.reading.facade;

import com.example.ilgeobolkka.book.entity.BookPage;
import com.example.ilgeobolkka.book.service.BookService;
import com.example.ilgeobolkka.ink.service.InkService;
import com.example.ilgeobolkka.ownership.service.OwnershipService;
import com.example.ilgeobolkka.reading.dto.OpenPageResponse;
import com.example.ilgeobolkka.reading.entity.ReadingSession;
import com.example.ilgeobolkka.reading.service.ReadingSessionService;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션 생성(POST)과 페이지 이동(PATCH)이 공유하는 페이지 열기 유스케이스의 단일 진입점.
 *
 * <p>정책상 처리 순서(docs/prd/product-policy.md "페이지 열기 처리 순서와 원자성")의 1~2단계(도서·페이지
 * 유효성, 온라인 소장은 즉시 제공)만 이번 서브태스크에서 구현한다. 3~7단계(활성 대여 확인, 잠금, 1잉크
 * 차감, {@code PageRental}/{@code LibraryEntry} 저장)는 뒤따르는 서브태스크의 몫이다.
 */
@Service
@RequiredArgsConstructor
public class ReadingFacade {

    private final BookService bookService;
    private final OwnershipService ownershipService;
    private final ReadingSessionService readingSessionService;
    private final InkService inkService;
    private final Clock clock;

    @Transactional
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
        ReadingSession session = resolvedViewer.currentSession() == null
                ? readingSessionService.openNewSession(readerId, page, clock.instant())
                : readingSessionService.moveCurrentSession(
                        resolvedViewer.currentSession(), page, clock.instant());
        int inkBalance = inkService.getBalance(readerId);

        return OpenPageResponse.owned(session, page, inkBalance);
    }

    /**
     * 활성 대여 확인, 잠금, 1잉크 차감, {@code PageRental}/{@code LibraryEntry} 저장(정책 4~7단계)은
     * 뒤따르는 서브태스크에서 구현한다. 지금은 소장하지 않은 도서의 페이지 열기를 처리하지 않는다.
     */
    private OpenPageResponse provideRentedPage(
            long readerId, ResolvedViewer resolvedViewer, BookPage page) {
        throw new UnsupportedOperationException("페이지 대여 처리는 아직 구현되지 않았습니다.");
    }

    private record ResolvedViewer(long bookId, ReadingSession currentSession) {}
}
