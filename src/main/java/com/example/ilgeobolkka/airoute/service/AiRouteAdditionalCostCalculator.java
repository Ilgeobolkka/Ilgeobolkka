package com.example.ilgeobolkka.airoute.service;

import com.example.ilgeobolkka.airoute.AiRouteAdditionalCostStatus;
import com.example.ilgeobolkka.ownership.service.OwnershipService;
import com.example.ilgeobolkka.rental.service.RentalService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 지금 권한으로 항목의 추가 비용을 판정한다. 저장은 생성 예산과 비교하려고, 상세 조회는 응답의
 * {@code additionalCostStatus} 를 채우려고 같은 판정을 쓴다.
 *
 * <p>한곳에 두는 이유는 두 경로가 한 요청 안에서 잇달아 돌기 때문이다. 규칙이 갈라지면 저장은 예산 안이라
 * 통과했는데 같은 요청이 돌려준 항목은 유료로 표시되는 조합이 생긴다.
 *
 * <p>판정 순서는 소장 → 활성 대여 → 나머지다. 소장은 도서 단위라 경로마다 한 번, 대여는 페이지 단위라
 * 항목마다 확인한다. {@code ONE_INK} 한 건이 곧 추가 잉크 1 인데, 페이지 열기가 권한 없는 페이지에만
 * 1 잉크를 쓰기 때문이다.
 *
 * <p>이 계산은 아무것도 바꾸지 않는다. 대여를 새로 만들거나 잉크를 미리 잡아 두지 않는다.
 *
 * <p>생성 시점 권한 사본을 쓰는 G04 조립기와 공통 순수 판정 규칙을 추출하는 작업은 SCRUM-487에서
 * 추적한다.
 */
@Service
@RequiredArgsConstructor
public class AiRouteAdditionalCostCalculator {

    private final OwnershipService ownershipService;
    private final RentalService rentalService;

    /** 소장은 도서 단위라 항목마다 확인하지 않고 호출자가 한 번 확인해 {@link #status} 에 넘긴다. */
    public boolean isOwned(long readerId, long bookId) {
        return ownershipService.isOwned(readerId, bookId);
    }

    public AiRouteAdditionalCostStatus status(
            long readerId, long bookPageId, boolean owned, Instant now) {
        if (owned) {
            return AiRouteAdditionalCostStatus.OWNED;
        }

        return rentalService.findActiveRental(readerId, bookPageId, now).isPresent()
                ? AiRouteAdditionalCostStatus.ACTIVE_RENTAL
                : AiRouteAdditionalCostStatus.ONE_INK;
    }

    /**
     * 페이지 묶음 전체의 추가 잉크. 권한 없는 페이지 수가 곧 추가 잉크다.
     *
     * <p>항목마다 대여 조회가 나가고, 저장은 같은 transaction 에서 상세 조회가 같은 조합을 한 번 더 돈다.
     * 한 요청이 schema 상한인 item 72 개를 두 벌 조회하는 셈이다. 유계라 이번 범위에서는 두었고, 일괄
     * 조회는 {@link RentalService} 를 고쳐야 해서 별도 과제로 넘긴다.
     */
    public int additionalInk(long readerId, long bookId, List<Long> bookPageIds, Instant now) {
        if (isOwned(readerId, bookId)) {
            return 0;
        }

        return (int)
                bookPageIds.stream()
                        .filter(bookPageId ->
                                status(readerId, bookPageId, false, now)
                                        == AiRouteAdditionalCostStatus.ONE_INK)
                        .count();
    }
}
