package com.example.ilgeobolkka.airoute.facade;

import com.example.ilgeobolkka.airoute.entity.AiReadingRoute;
import com.example.ilgeobolkka.airoute.entity.AiReadingRouteItem;
import com.example.ilgeobolkka.airoute.exception.AiRouteNotFoundException;
import com.example.ilgeobolkka.airoute.repository.AiReadingRouteItemRepository;
import com.example.ilgeobolkka.airoute.repository.AiReadingRouteRepository;
import com.example.ilgeobolkka.ownership.service.OwnershipService;
import com.example.ilgeobolkka.reading.dto.PageContent;
import com.example.ilgeobolkka.reading.entity.ReadingSession;
import com.example.ilgeobolkka.reading.service.PageContentService;
import com.example.ilgeobolkka.reading.service.ReadingSessionService;
import com.example.ilgeobolkka.rental.service.RentalService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기존 페이지 열기가 만든 현재 세션과 권한을 다시 확인한 뒤 저장 경로 콘텐츠와 진행을 함께 제공한다.
 *
 * <p>경로 행을 먼저 잠그고 항목 행을 잠근다. 같은 경로의 서로 다른 마지막 항목 요청도 이 순서로 직렬화해야
 * 한 요청만 {@code completedAt}을 기록한다. 현재 지정·삭제·피드백도 경로 행을 먼저 잠그므로 잠금 순서가
 * 같다.
 *
 * <p>콘텐츠 파일 읽기도 transaction 안에서 끝낸다. 읽기가 실패하면 {@code openedAt}과
 * {@code completedAt}을 남기지 않는 S04 원자성 계약 때문이다. 콘텐츠 바이트를 메모리에 확보한 뒤에만
 * 진행을 기록한다.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai-route", name = "enabled", havingValue = "true")
public class AiRouteContentFacade {

    private final AiReadingRouteRepository aiReadingRouteRepository;
    private final AiReadingRouteItemRepository aiReadingRouteItemRepository;
    private final ReadingSessionService readingSessionService;
    private final OwnershipService ownershipService;
    private final RentalService rentalService;
    private final PageContentService pageContentService;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PageContent provideContent(
            long readerId, long routeId, int pageNumber, UUID viewerSessionId) {
        AiReadingRoute route = aiReadingRouteRepository
                .findOwnedByIdForUpdate(readerId, routeId)
                .orElseThrow(() -> new AiRouteNotFoundException(routeId));
        AiReadingRouteItem item = aiReadingRouteItemRepository
                .findOwnedItemForUpdate(readerId, routeId, pageNumber)
                .orElseThrow(() -> new AiRouteNotFoundException(routeId));

        validateCurrentPage(readerId, route, pageNumber, viewerSessionId);

        Instant now = clock.instant();
        if (!canRead(readerId, route, item, now)) {
            throw new AccessDeniedException("페이지 콘텐츠 접근 권한이 없습니다.");
        }

        PageContent content = pageContentService.read(item.getBookPage());
        recordProgress(route, item, now);
        return content;
    }

    private void validateCurrentPage(
            long readerId,
            AiReadingRoute route,
            int pageNumber,
            UUID viewerSessionId) {
        ReadingSession session = readingSessionService.getCurrentSession(readerId, viewerSessionId);
        if (!session.getBookId().equals(route.getBookId())
                || session.getCurrentPageNumber() != pageNumber) {
            throw new AccessDeniedException("현재 열람 페이지와 경로 페이지가 다릅니다.");
        }
    }

    private boolean canRead(
            long readerId, AiReadingRoute route, AiReadingRouteItem item, Instant now) {
        return ownershipService.isOwned(readerId, route.getBookId())
                || rentalService.findActiveRental(readerId, item.getBookPageId(), now).isPresent();
    }

    private void recordProgress(
            AiReadingRoute route, AiReadingRouteItem item, Instant openedAt) {
        if (item.getOpenedAt() != null) {
            return;
        }

        item.markOpened(openedAt);
        aiReadingRouteItemRepository.flush();
        if (route.getCompletedAt() == null
                && aiReadingRouteItemRepository.countByRouteIdAndOpenedAtIsNull(route.getId())
                        == 0) {
            route.complete(openedAt);
        }
    }
}
