package com.example.ilgeobolkka.airoute.service.save;

import com.example.ilgeobolkka.airoute.AiRouteRequestType;
import com.example.ilgeobolkka.airoute.entity.AiReadingRoute;
import com.example.ilgeobolkka.airoute.entity.AiReadingRouteItem;
import com.example.ilgeobolkka.airoute.entity.AiRouteGeneration;
import com.example.ilgeobolkka.airoute.entity.AiRouteGenerationItem;
import com.example.ilgeobolkka.airoute.entity.AiRouteGenerationStatus;
import com.example.ilgeobolkka.airoute.exception.AiRouteContentChangedException;
import com.example.ilgeobolkka.airoute.exception.AiRouteEntitlementChangedException;
import com.example.ilgeobolkka.airoute.exception.AiRouteGenerationConsumedException;
import com.example.ilgeobolkka.airoute.exception.AiRouteGenerationNotFoundException;
import com.example.ilgeobolkka.airoute.repository.AiReadingRouteItemRepository;
import com.example.ilgeobolkka.airoute.repository.AiReadingRouteRepository;
import com.example.ilgeobolkka.airoute.repository.AiRouteCurrentRepository;
import com.example.ilgeobolkka.airoute.repository.AiRouteGenerationItemRepository;
import com.example.ilgeobolkka.airoute.repository.AiRouteGenerationRepository;
import com.example.ilgeobolkka.airoute.service.generation.AiRouteGenerationLifecycleService;
import com.example.ilgeobolkka.book.service.BookService;
import com.example.ilgeobolkka.ownership.service.OwnershipService;
import com.example.ilgeobolkka.rental.service.RentalService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유효한 {@code ROUTE} 생성 결과를 저장 경로로 한 번만 옮기고 같은 transaction 에서 현재 경로로 지정한다.
 *
 * <p>클라이언트는 {@code generationId} 만 보낸다. 도서·콘텐츠 버전·목적·페이지 구성은 모두 서버가 저장해 둔
 * 값에서만 읽으며 요청 바디로 받지 않는다.
 *
 * <p>같은 생성의 동시 저장은 첫 문장인 생성 행 잠금이 직렬화한다. 진 쪽은 잠금을 얻은 시점에 최신 commit
 * 을 읽어 {@code SAVED} 를 보고 이미 만들어진 경로를 그대로 돌려준다. 그래서 경로 unique key 위반을 잡아
 * 수렴시키는 경로가 따로 필요 없다.
 */
@Service
@RequiredArgsConstructor
public class AiRouteSaveService {

    private final AiRouteGenerationRepository generationRepository;
    private final AiRouteGenerationItemRepository generationItemRepository;
    private final AiReadingRouteRepository routeRepository;
    private final AiReadingRouteItemRepository routeItemRepository;
    private final AiRouteCurrentRepository currentRepository;
    private final AiRouteGenerationLifecycleService lifecycleService;
    private final BookService bookService;
    private final OwnershipService ownershipService;
    private final RentalService rentalService;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public SavedAiRoute save(long readerId, UUID generationId) {
        Instant now = clock.instant();
        AiRouteGeneration generation =
                generationRepository
                        .findOwnedNotExpiredForUpdate(generationId, readerId, now)
                        .orElseThrow(() -> new AiRouteGenerationNotFoundException(generationId));

        // 저장할 것이 없는 상태를 먼저 걸러 낸다. 재시도와 소비 완료만 저장과 다른 응답을 갖는다.
        if (generation.getStatus() == AiRouteGenerationStatus.SAVED) {
            return SavedAiRoute.existing(generation.getSavedRouteId());
        }
        if (generation.getStatus() == AiRouteGenerationStatus.CONSUMED) {
            throw new AiRouteGenerationConsumedException(generationId);
        }
        if (generation.getStatus() != AiRouteGenerationStatus.ROUTE) {
            throw new AiRouteGenerationNotFoundException(generationId);
        }

        List<AiRouteGenerationItem> generationItems =
                generationItemRepository.findAllOrderedByGenerationId(generationId);
        requireSameContentVersion(generation);
        requireEntitlementWithinBudget(generation, generationItems, now);

        AiReadingRoute route = routeRepository.saveAndFlush(newRoute(generation, now));
        routeItemRepository.saveAll(
                generationItems.stream().map(item -> toRouteItem(route, item)).toList());

        // 상태 전이는 G06 만 한다. 임시 목적·입력·항목도 여기서 함께 사라진다.
        lifecycleService.markSaved(generationId, route);

        currentRepository.selectAsCurrent(
                generation.getReaderId(), generation.getBookId(), route.getId(), now);

        return SavedAiRoute.created(route.getId());
    }

    /**
     * 저장은 생성 시점의 페이지 구성을 그대로 옮기는 작업이라, 그 구성이 가리키는 콘텐츠가 바뀌었으면
     * 진행하지 않는다.
     *
     * <p>2차 MVP 는 사용자 기록이 있는 환경에서 {@code contentVersion} 을 재발급하지 않으므로 도달 가능한
     * 사용자 경로가 아니다. 불변식 방어 검사로 남긴다.
     */
    private void requireSameContentVersion(AiRouteGeneration generation) {
        String currentContentVersion =
                bookService.findBook(generation.getBookId()).getContentVersion();
        if (!currentContentVersion.equals(generation.getContentVersion())) {
            throw new AiRouteContentChangedException(generation.getGenerationId());
        }
    }

    /**
     * 지금 권한으로 추가 잉크를 다시 계산해 생성 예산과 비교한다. 넘으면 아무것도 만들지 않고 거부하므로
     * 생성은 소비되지 않고 원래 만료 시각까지 {@code ROUTE} 로 남는다.
     *
     * <p>최초 거부를 기록하는 상태를 두지 않는다. 매 요청이 그 시점 권한으로 다시 계산하므로 권한이
     * 그대로인 재시도는 같은 오류를 받고, 만료 전에 권한이 예산 이하로 돌아오면 같은 {@code generationId}
     * 저장이 다시 가능하다.
     *
     * <p>소장 도서 요청({@code OWNED_DEPTH})에는 예산 열이 없다. 소장은 도서 전체를 덮고 취소되지 않아
     * 재계산 비용이 0 이므로 예산을 0 으로 본다. 소장이 사라져야만 거부에 닿는데 그런 전이가 없어
     * 콘텐츠 버전 검사와 같은 성격의 불변식 방어다.
     */
    private void requireEntitlementWithinBudget(
            AiRouteGeneration generation, List<AiRouteGenerationItem> items, Instant now) {
        int budget =
                generation.getRequestType() == AiRouteRequestType.INK_BUDGET
                        ? generation.getMaxAdditionalInk()
                        : 0;

        if (additionalInk(generation.getReaderId(), generation.getBookId(), items, now) > budget) {
            throw new AiRouteEntitlementChangedException(generation.getGenerationId());
        }
    }

    /**
     * 권한 없는 페이지 수가 곧 추가 잉크다. 페이지 열기가 권한 없는 페이지에만 1 잉크를 쓰기 때문이다.
     *
     * <p>판정 순서와 기준은 저장 경로 상세의 {@code additionalCostStatus} 와 같다. 소장은 도서 단위라 한 번,
     * 대여는 페이지 단위라 항목마다 확인한다.
     *
     * <p>이 계산은 아무것도 바꾸지 않는다. 대여를 새로 만들거나 잉크를 미리 잡아 두지 않으며, 저장 자체도
     * 페이지 접근권한을 만들지 않는다.
     */
    private int additionalInk(
            long readerId, long bookId, List<AiRouteGenerationItem> items, Instant now) {
        if (ownershipService.isOwned(readerId, bookId)) {
            return 0;
        }

        return (int)
                items.stream()
                        .filter(item ->
                                rentalService
                                        .findActiveRental(readerId, item.getBookPageId(), now)
                                        .isEmpty())
                        .count();
    }

    /**
     * 생성이 들고 있던 목적·요청 입력을 그대로 옮긴다. 경로를 먼저 만들어야 하는데,
     * {@link AiRouteGenerationLifecycleService#markSaved} 가 두 값을 대조한 뒤 생성 쪽에서 지우기 때문이다.
     */
    private AiReadingRoute newRoute(AiRouteGeneration generation, Instant now) {
        if (generation.getRequestType() == AiRouteRequestType.INK_BUDGET) {
            return AiReadingRoute.createWithInkBudget(
                    generation.getGenerationId(),
                    generation.getReaderId(),
                    generation.getBookId(),
                    generation.getContentVersion(),
                    generation.getNormalizedPurpose(),
                    generation.getMaxAdditionalInk(),
                    now);
        }

        return AiReadingRoute.createWithOwnedDepth(
                generation.getGenerationId(),
                generation.getReaderId(),
                generation.getBookId(),
                generation.getContentVersion(),
                generation.getNormalizedPurpose(),
                generation.getDepth(),
                now);
    }

    private AiReadingRouteItem toRouteItem(AiReadingRoute route, AiRouteGenerationItem item) {
        return AiReadingRouteItem.create(
                route.getId(),
                route.getBookId(),
                item.getBookPageId(),
                item.getPosition(),
                item.getRelevance(),
                item.isPrerequisite(),
                item.getRole());
    }
}
