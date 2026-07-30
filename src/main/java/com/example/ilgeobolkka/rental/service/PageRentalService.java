package com.example.ilgeobolkka.rental.service;

import com.example.ilgeobolkka.rental.entity.PageRental;
import com.example.ilgeobolkka.rental.repository.PageRentalRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PageRentalService {

    private final PageRentalRepository pageRentalRepository;

    /**
     * INV-006 경계({@code rentedAt <= now < expiresAt})를 만족하는 활성 대여를 조회한다. 같은
     * 순간에는 최대 하나만 활성이어야 하는 업무 규칙을 전제로, 여러 건이 조회되면 가장 늦게 만료되는
     * 대여를 반환한다.
     */
    public Optional<PageRental> findActive(long readerId, long bookPageId, Instant now) {
        return pageRentalRepository.findActive(readerId, bookPageId, now).stream().findFirst();
    }

    /**
     * 1잉크 차감보다 먼저 저장해 {@code id}를 확보해야 한다(ink_ledger의 page_rental_id FK).
     *
     * <p>{@code MANDATORY}로 호출자의 트랜잭션 합류를 강제한다. 애노테이션이 없으면 트랜잭션
     * 밖에서 호출될 때 이 {@code save}가 자기 트랜잭션으로 개별 커밋되어, 뒤따르는 차감이
     * 실패해도 대여만 남는다. INV-003(차감과 대여의 원자성)이 조용히 깨지는 경로다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public PageRental rent(long readerId, long bookPageId, Instant rentedAt) {
        return pageRentalRepository.save(PageRental.rent(readerId, bookPageId, rentedAt));
    }
}
