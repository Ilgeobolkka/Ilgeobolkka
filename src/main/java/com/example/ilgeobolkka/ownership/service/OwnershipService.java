package com.example.ilgeobolkka.ownership.service;

import com.example.ilgeobolkka.ownership.entity.OwnershipPayment;
import com.example.ilgeobolkka.ownership.entity.OwnershipPaymentStatus;
import com.example.ilgeobolkka.ownership.exception.BookAlreadyOwnedException;
import com.example.ilgeobolkka.ownership.repository.BookOwnershipRepository;
import com.example.ilgeobolkka.ownership.repository.OwnershipPaymentRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OwnershipService {

    private final BookOwnershipRepository bookOwnershipRepository;
    private final OwnershipPaymentRepository ownershipPaymentRepository;

    public boolean isOwned(long readerId, long bookId) {
        return bookOwnershipRepository.existsByReaderIdAndBookId(readerId, bookId);
    }

    public PaymentPreparation preparePayment(
            long readerId,
            long bookId,
            UUID paymentId,
            int amountWon,
            Instant createdAt) {
        if (isOwned(readerId, bookId)) {
            throw new BookAlreadyOwnedException(readerId, bookId);
        }

        var pendingPayment = ownershipPaymentRepository
                .findFirstByReaderIdAndBookIdAndStatusOrderByIdDesc(
                        readerId,
                        bookId,
                        OwnershipPaymentStatus.PENDING);
        if (pendingPayment.isPresent()) {
            return PaymentPreparation.reused(pendingPayment.get());
        }

        OwnershipPayment payment = ownershipPaymentRepository.save(
                OwnershipPayment.create(readerId, bookId, paymentId, amountWon, createdAt));
        return PaymentPreparation.created(payment);
    }

    public record PaymentPreparation(
            OwnershipPayment payment,
            boolean created) {

        private static PaymentPreparation created(OwnershipPayment payment) {
            return new PaymentPreparation(payment, true);
        }

        private static PaymentPreparation reused(OwnershipPayment payment) {
            return new PaymentPreparation(payment, false);
        }
    }
}
