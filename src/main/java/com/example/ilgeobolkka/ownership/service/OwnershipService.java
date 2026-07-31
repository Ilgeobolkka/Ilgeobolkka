package com.example.ilgeobolkka.ownership.service;

import com.example.ilgeobolkka.ownership.entity.OwnershipPaymentStatus;
import com.example.ilgeobolkka.ownership.repository.BookOwnershipRepository;
import com.example.ilgeobolkka.ownership.repository.OwnershipPaymentEntryProjection;
import com.example.ilgeobolkka.ownership.repository.OwnershipPaymentRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OwnershipService {

    private static final int HISTORY_PAGE_SIZE = 10;

    private final BookOwnershipRepository bookOwnershipRepository;
    private final OwnershipPaymentRepository ownershipPaymentRepository;

    public boolean isOwned(long readerId, long bookId) {
        return bookOwnershipRepository.existsByReaderIdAndBookId(readerId, bookId);
    }

    /** 완료된 소장 결제 내역은 조회 전용이라 PortOne 연동 활성화 여부와 무관하게 제공한다. */
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
}
