package com.example.ilgeobolkka.rental.service;

import com.example.ilgeobolkka.rental.entity.PageRental;
import com.example.ilgeobolkka.rental.repository.PageRentalRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}
