package com.example.ilgeobolkka.ink.facade;

import com.example.ilgeobolkka.ink.dto.FindInkBalanceResponse;
import com.example.ilgeobolkka.ink.dto.FindInkLedgerResponse;
import com.example.ilgeobolkka.ink.service.InkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InkFacade {

    private final InkService inkService;

    @Transactional(readOnly = true)
    public FindInkBalanceResponse findBalance(long readerId) {
        return new FindInkBalanceResponse(inkService.getBalance(readerId));
    }

    @Transactional(readOnly = true)
    public FindInkLedgerResponse findLedger(long readerId, int page) {
        return FindInkLedgerResponse.from(inkService.getLedger(readerId, page), page);
    }
}
