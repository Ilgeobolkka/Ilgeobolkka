package com.example.ilgeobolkka.ink.service;

import com.example.ilgeobolkka.ink.entity.InkAccount;
import com.example.ilgeobolkka.ink.entity.InkLedger;
import com.example.ilgeobolkka.ink.exception.InkAccountNotFoundException;
import com.example.ilgeobolkka.ink.repository.InkAccountRepository;
import com.example.ilgeobolkka.ink.repository.InkLedgerRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InkService {

    private final InkAccountRepository inkAccountRepository;
    private final InkLedgerRepository inkLedgerRepository;

    public void createInkAccount(long readerId) {
        inkAccountRepository.save(InkAccount.create(readerId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int grant(long readerId, long inkPurchaseId, Instant occurredAt) {
        InkAccount account = findAccountForUpdate(readerId);
        if (inkLedgerRepository.existsByInkPurchaseId(inkPurchaseId)) {
            return account.getBalance();
        }

        account.grant();
        inkLedgerRepository.save(
                InkLedger.grant(readerId, inkPurchaseId, account.getBalance(), occurredAt));
        return account.getBalance();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deduct(long readerId, long pageRentalId, Instant occurredAt) {
        InkAccount account = findAccountForUpdate(readerId);
        if (inkLedgerRepository.existsByPageRentalId(pageRentalId)) {
            return;
        }

        account.deduct();
        inkLedgerRepository.save(
                InkLedger.deduction(readerId, pageRentalId, account.getBalance(), occurredAt));
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public int getBalance(long readerId) {
        return inkAccountRepository
                .findByReaderId(readerId)
                .orElseThrow(() -> new InkAccountNotFoundException(readerId))
                .getBalance();
    }

    private InkAccount findAccountForUpdate(long readerId) {
        return inkAccountRepository
                .findByReaderIdForUpdate(readerId)
                .orElseThrow(() -> new InkAccountNotFoundException(readerId));
    }
}
