package com.example.ilgeobolkka.ink.service;

import com.example.ilgeobolkka.ink.entity.InkAccount;
import com.example.ilgeobolkka.ink.entity.InkLedger;
import com.example.ilgeobolkka.ink.entity.InkOperationClaim;
import com.example.ilgeobolkka.ink.entity.InkPurchase;
import com.example.ilgeobolkka.ink.exception.InkAccountNotFoundException;
import com.example.ilgeobolkka.ink.exception.InkPurchaseNotFoundException;
import com.example.ilgeobolkka.ink.exception.InkPurchaseStateConflictException;
import com.example.ilgeobolkka.ink.exception.InvalidInkLedgerException;
import com.example.ilgeobolkka.ink.repository.InkAccountRepository;
import com.example.ilgeobolkka.ink.repository.InkLedgerEntryQuery;
import com.example.ilgeobolkka.ink.repository.InkLedgerRepository;
import com.example.ilgeobolkka.ink.repository.InkOperationClaimRepository;
import com.example.ilgeobolkka.ink.repository.InkPurchaseRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InkService {

    private static final int PAGE_SIZE = 10;

    private final InkAccountRepository inkAccountRepository;
    private final InkLedgerRepository inkLedgerRepository;
    private final InkOperationClaimRepository inkOperationClaimRepository;
    private final InkPurchaseRepository inkPurchaseRepository;
    private final Clock clock;

    public void createInkAccount(long readerId) {
        inkAccountRepository.save(InkAccount.create(readerId));
    }

    public int findBalance(long readerId) {
        return findAccount(readerId).getBalance();
    }

    public Page<InkLedgerEntryQuery> findLedgerEntries(long readerId, int page) {
        return inkLedgerRepository.findEntriesByReaderId(
                readerId,
                PageRequest.of(page - 1, PAGE_SIZE));
    }

    public void grantInk(long readerId, long inkPurchaseId) {
        InkPurchase purchase = inkPurchaseRepository
                .findByIdAndReaderId(inkPurchaseId, readerId)
                .orElseThrow(InkPurchaseNotFoundException::new);

        if (!purchase.isPaid()) {
            throw new InkPurchaseStateConflictException();
        }

        if (!claimGrant(readerId, inkPurchaseId)) {
            return;
        }

        InkAccount account = findAccountForUpdate(readerId);
        Instant occurredAt = clock.instant();
        int balanceAfter = account.grantPurchaseInk();
        inkLedgerRepository.save(
                InkLedger.grant(readerId, inkPurchaseId, balanceAfter, occurredAt));
    }

    public void deductInk(long readerId, long pageRentalId) {
        long rentalReaderId = inkLedgerRepository.findPageRentalReaderId(pageRentalId)
                .orElseThrow(() -> new InvalidInkLedgerException("페이지 대여를 찾을 수 없습니다."));

        if (rentalReaderId != readerId) {
            throw new InvalidInkLedgerException("다른 독자의 페이지 대여 내역입니다.");
        }

        if (!claimDeduction(readerId, pageRentalId)) {
            return;
        }

        InkAccount account = findAccountForUpdate(readerId);
        Instant occurredAt = clock.instant();
        int balanceAfter = account.deductPageRentalInk();
        inkLedgerRepository.save(
                InkLedger.deduct(readerId, pageRentalId, balanceAfter, occurredAt));
    }

    private boolean claimGrant(long readerId, long inkPurchaseId) {
        UUID claimToken = UUID.randomUUID();
        inkOperationClaimRepository.insertGrantClaim(
                readerId,
                inkPurchaseId,
                claimToken.toString());
        InkOperationClaim claim = inkOperationClaimRepository
                .findGrantClaimForUpdate(inkPurchaseId)
                .orElseThrow(() -> new InvalidInkLedgerException("잉크 지급 선점 결과를 찾을 수 없습니다."));

        return claim.isOwnedBy(claimToken);
    }

    private boolean claimDeduction(long readerId, long pageRentalId) {
        UUID claimToken = UUID.randomUUID();
        inkOperationClaimRepository.insertDeductionClaim(
                readerId,
                pageRentalId,
                claimToken.toString());
        InkOperationClaim claim = inkOperationClaimRepository
                .findDeductionClaimForUpdate(pageRentalId)
                .orElseThrow(() -> new InvalidInkLedgerException("잉크 차감 선점 결과를 찾을 수 없습니다."));

        return claim.isOwnedBy(claimToken);
    }

    private InkAccount findAccount(long readerId) {
        return inkAccountRepository.findByReaderId(readerId)
                .orElseThrow(InkAccountNotFoundException::new);
    }

    private InkAccount findAccountForUpdate(long readerId) {
        return inkAccountRepository.findByReaderIdForUpdate(readerId)
                .orElseThrow(InkAccountNotFoundException::new);
    }
}
