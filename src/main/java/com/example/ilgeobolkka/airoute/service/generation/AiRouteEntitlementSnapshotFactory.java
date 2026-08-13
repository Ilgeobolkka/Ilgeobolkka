package com.example.ilgeobolkka.airoute.service.generation;

import com.example.ilgeobolkka.airoute.AiRouteGenerationCommand;
import com.example.ilgeobolkka.airoute.AiRouteRequestType;
import com.example.ilgeobolkka.airoute.exception.InvalidAiRouteGenerationInputException;
import com.example.ilgeobolkka.airoute.service.assembly.AiRouteEntitlementSnapshot;
import java.util.Set;

/** 조회가 끝난 권한 값과 생성 입력의 조합을 검증해 서버 계산 전용 snapshot으로 만든다. */
public final class AiRouteEntitlementSnapshotFactory {

    public AiRouteEntitlementSnapshot create(
            AiRouteGenerationCommand command,
            boolean owned,
            int inkBalance,
            Set<Long> activeRentalPageIds) {
        if (command == null || activeRentalPageIds == null) {
            throw new IllegalArgumentException("생성 명령과 활성 대여 목록이 필요합니다.");
        }
        if (owned) {
            if (command.requestType() != AiRouteRequestType.OWNED_DEPTH) {
                throw invalid("소장 도서는 깊이로 생성해야 합니다.");
            }
            return AiRouteEntitlementSnapshot.forOwned();
        }
        if (command.requestType() != AiRouteRequestType.INK_BUDGET) {
            throw invalid("비소장 도서는 잉크 예산으로 생성해야 합니다.");
        }
        if (command.maxAdditionalInk() > inkBalance) {
            throw invalid("선택 예산이 현재 잉크 잔액을 초과합니다.");
        }
        return AiRouteEntitlementSnapshot.forNonOwned(inkBalance, activeRentalPageIds);
    }

    private InvalidAiRouteGenerationInputException invalid(String reason) {
        return new InvalidAiRouteGenerationInputException(reason);
    }
}
