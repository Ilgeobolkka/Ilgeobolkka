package com.example.ilgeobolkka.airoute.service.assembly;

import java.util.Set;

/**
 * 경로 생성 한 번에 사용하는 권한 사본. 조립기는 DB를 조회하지 않고 이 값만으로 추가 비용을 계산한다.
 *
 * <p>소장 도서는 잉크 잔액과 활성 대여가 비용에 영향을 주지 않는다. 비소장 도서만 생성 시점의 잔액과
 * 활성 대여 페이지 번호를 함께 보존한다.
 */
public record AiRouteEntitlementSnapshot(
        boolean owned, int inkBalance, Set<Integer> activeRentalPageNumbers) {

    public AiRouteEntitlementSnapshot {
        if (inkBalance < 0) {
            throw new IllegalArgumentException("잉크 잔액은 0 이상이어야 합니다.");
        }
        if (activeRentalPageNumbers == null) {
            throw new IllegalArgumentException("활성 대여 페이지 목록은 null일 수 없습니다.");
        }
        if (owned && (inkBalance != 0 || !activeRentalPageNumbers.isEmpty())) {
            throw new IllegalArgumentException("소장 권한 사본에는 잉크 잔액이나 활성 대여를 넣지 않습니다.");
        }
        if (activeRentalPageNumbers.stream()
                .anyMatch(pageNumber -> pageNumber == null || pageNumber <= 0)) {
            throw new IllegalArgumentException("활성 대여 페이지 번호는 양수여야 합니다.");
        }
        activeRentalPageNumbers = Set.copyOf(activeRentalPageNumbers);
    }

    public static AiRouteEntitlementSnapshot forOwned() {
        return new AiRouteEntitlementSnapshot(true, 0, Set.of());
    }

    public static AiRouteEntitlementSnapshot forNonOwned(
            int inkBalance, Set<Integer> activeRentalPageNumbers) {
        return new AiRouteEntitlementSnapshot(false, inkBalance, activeRentalPageNumbers);
    }
}
