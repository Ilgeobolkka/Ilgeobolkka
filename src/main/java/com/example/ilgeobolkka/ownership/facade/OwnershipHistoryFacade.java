package com.example.ilgeobolkka.ownership.facade;

import com.example.ilgeobolkka.ownership.dto.FindOwnershipPaymentsResponse;
import com.example.ilgeobolkka.ownership.service.OwnershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 소장 조회 유스케이스. 결제 준비·완료는 PortOne 연동 활성화에 묶여 있지만 조회는 그렇지 않으므로,
 * 잉크의 {@code InkFacade}·{@code InkPurchaseFacade}와 같은 방식으로 분리한다.
 */
@Service
@RequiredArgsConstructor
public class OwnershipHistoryFacade {

    private final OwnershipService ownershipService;

    public FindOwnershipPaymentsResponse findHistory(long readerId, int page) {
        return FindOwnershipPaymentsResponse.from(ownershipService.getHistory(readerId, page), page);
    }
}
