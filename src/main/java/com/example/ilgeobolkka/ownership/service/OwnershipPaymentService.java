package com.example.ilgeobolkka.ownership.service;

import com.example.ilgeobolkka.global.exception.ErrorCode;
import com.example.ilgeobolkka.infra.portone.PortOnePayment;
import com.example.ilgeobolkka.infra.portone.PortOnePaymentProperties;
import com.example.ilgeobolkka.infra.portone.PortOnePaymentStatus;
import com.example.ilgeobolkka.ink.exception.PaymentStateConflictException;
import com.example.ilgeobolkka.ownership.dto.CompleteOwnershipPaymentResponse;
import com.example.ilgeobolkka.ownership.entity.BookOwnership;
import com.example.ilgeobolkka.ownership.entity.OwnershipPayment;
import com.example.ilgeobolkka.ownership.entity.OwnershipPaymentStatus;
import com.example.ilgeobolkka.ownership.exception.BookAlreadyOwnedException;
import com.example.ilgeobolkka.ownership.exception.OwnershipPaymentNotFoundException;
import com.example.ilgeobolkka.ownership.repository.BookOwnershipRepository;
import com.example.ilgeobolkka.ownership.repository.OwnershipPaymentEntryProjection;
import com.example.ilgeobolkka.ownership.repository.OwnershipPaymentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "portone.payment", name = "enabled", havingValue = "true")
@Profile("!prod")
public class OwnershipPaymentService {

    public static final String ORDER_NAME = "읽어볼까 도서 소장";
    private static final String SERVER_CURRENCY = "KRW";
    private static final String PORTONE_VERSION = "V2";
    private static final int HISTORY_PAGE_SIZE = 10;

    private final OwnershipService ownershipService;
    private final BookOwnershipRepository bookOwnershipRepository;
    private final OwnershipPaymentRepository ownershipPaymentRepository;
    private final PortOnePaymentProperties paymentProperties;

    @Transactional
    public PaymentPreparation preparePayment(
            long readerId,
            long bookId,
            UUID paymentId,
            int amountWon,
            Instant createdAt) {
        if (ownershipService.isOwned(readerId, bookId)) {
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

    @Transactional(readOnly = true)
    public boolean existsByPaymentId(UUID paymentId) {
        return ownershipPaymentRepository.existsByPaymentId(paymentId);
    }

    @Transactional(readOnly = true)
    public Optional<CompleteOwnershipPaymentResponse> findCachedCompletion(
            long readerId, UUID paymentId) {
        OwnershipPayment payment = findPayment(paymentId, readerId);
        if (payment.getStatus() == OwnershipPaymentStatus.FAILED) {
            throw new PaymentStateConflictException();
        }
        if (payment.getStatus() == OwnershipPaymentStatus.PAID) {
            return Optional.of(CompleteOwnershipPaymentResponse.paid(payment));
        }
        return Optional.empty();
    }

    @Transactional
    public CompletionResult applyPaymentResult(
            long readerId,
            UUID paymentId,
            PortOnePayment portOnePayment) {
        OwnershipPayment payment = findPaymentForUpdate(paymentId, readerId);
        if (payment.getStatus() == OwnershipPaymentStatus.PAID) {
            return CompletionResult.success(CompleteOwnershipPaymentResponse.paid(payment));
        }
        if (payment.getStatus() == OwnershipPaymentStatus.FAILED) {
            throw new PaymentStateConflictException();
        }

        return applyPendingPaymentResult(payment, portOnePayment);
    }

    @Transactional
    public void applyWebhookPaymentResult(UUID paymentId, PortOnePayment portOnePayment) {
        OwnershipPayment payment = findPaymentForUpdate(paymentId);
        if (payment.getStatus() != OwnershipPaymentStatus.PENDING) {
            return;
        }
        applyPendingPaymentResult(payment, portOnePayment);
    }

    @Transactional(readOnly = true)
    public Page<OwnershipPaymentEntryProjection> getHistory(long readerId, int page) {
        PageRequest pageable = PageRequest.of(page - 1, HISTORY_PAGE_SIZE);

        if (pageable.getOffset() > Integer.MAX_VALUE) {
            return new PageImpl<>(
                    List.of(),
                    pageable,
                    ownershipPaymentRepository.countByReaderIdAndStatus(
                            readerId, OwnershipPaymentStatus.PAID));
        }

        return ownershipPaymentRepository.findEntriesByReaderIdAndStatus(
                readerId, OwnershipPaymentStatus.PAID, pageable);
    }

    private CompletionResult applyPendingPaymentResult(
            OwnershipPayment payment, PortOnePayment portOnePayment) {
        if (portOnePayment.status() == PortOnePaymentStatus.NOT_FOUND) {
            return CompletionResult.success(CompleteOwnershipPaymentResponse.pending(payment));
        }

        ErrorCode verificationError = verifyPayment(payment, portOnePayment);
        if (verificationError != null) {
            payment.markFailed();
            return CompletionResult.failure(verificationError);
        }

        if (portOnePayment.status() == PortOnePaymentStatus.PENDING) {
            return CompletionResult.success(CompleteOwnershipPaymentResponse.pending(payment));
        }
        if (portOnePayment.status() == PortOnePaymentStatus.FAILED) {
            payment.markFailed();
            return CompletionResult.failure(ErrorCode.PAYMENT_VERIFICATION_FAILED);
        }
        if (portOnePayment.paidAt() == null) {
            payment.markFailed();
            return CompletionResult.failure(ErrorCode.PAYMENT_VERIFICATION_FAILED);
        }

        payment.markPaid(portOnePayment.paidAt());
        bookOwnershipRepository.save(
                BookOwnership.create(
                        payment.getReaderId(),
                        payment.getBookId(),
                        payment.getId(),
                        portOnePayment.paidAt()));
        return CompletionResult.success(CompleteOwnershipPaymentResponse.paid(payment));
    }

    private ErrorCode verifyPayment(OwnershipPayment payment, PortOnePayment portOnePayment) {
        if (portOnePayment.totalAmount() != payment.getAmountWon()) {
            return ErrorCode.INVALID_PAYMENT_AMOUNT;
        }
        if (!Objects.equals(portOnePayment.paymentId(), payment.getPaymentId().toString())
                || !Objects.equals(portOnePayment.currency(), SERVER_CURRENCY)
                || !Objects.equals(portOnePayment.storeId(), paymentProperties.storeId())
                || !Objects.equals(portOnePayment.orderName(), ORDER_NAME)
                || !Objects.equals(portOnePayment.version(), PORTONE_VERSION)) {
            return ErrorCode.PAYMENT_VERIFICATION_FAILED;
        }
        if (portOnePayment.channelKey() == null) {
            return portOnePayment.status() == PortOnePaymentStatus.PENDING
                    ? null
                    : ErrorCode.PAYMENT_VERIFICATION_FAILED;
        }
        return Objects.equals(portOnePayment.channelKey(), paymentProperties.channelKey())
                ? null
                : ErrorCode.PAYMENT_VERIFICATION_FAILED;
    }

    private OwnershipPayment findPayment(UUID paymentId, long readerId) {
        return ownershipPaymentRepository
                .findByPaymentIdAndReaderId(paymentId, readerId)
                .orElseThrow(() -> new OwnershipPaymentNotFoundException(paymentId));
    }

    private OwnershipPayment findPaymentForUpdate(UUID paymentId, long readerId) {
        return ownershipPaymentRepository
                .findByPaymentIdAndReaderIdForUpdate(paymentId, readerId)
                .orElseThrow(() -> new OwnershipPaymentNotFoundException(paymentId));
    }

    private OwnershipPayment findPaymentForUpdate(UUID paymentId) {
        return ownershipPaymentRepository
                .findByPaymentIdForUpdate(paymentId)
                .orElseThrow(() -> new OwnershipPaymentNotFoundException(paymentId));
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

    public record CompletionResult(
            CompleteOwnershipPaymentResponse response,
            ErrorCode errorCode) {

        private static CompletionResult success(CompleteOwnershipPaymentResponse response) {
            return new CompletionResult(response, null);
        }

        private static CompletionResult failure(ErrorCode errorCode) {
            return new CompletionResult(null, errorCode);
        }
    }
}
