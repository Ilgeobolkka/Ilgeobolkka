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
public class RentalService {

    private final PageRentalRepository pageRentalRepository;

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<PageRental> findActiveRental(long readerId, long bookPageId, Instant now) {
        return pageRentalRepository
                .findFirstByReaderIdAndBookPageIdOrderByRentedAtDesc(readerId, bookPageId)
                .filter(rental -> rental.isActive(now));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PageRental startRental(long readerId, long bookPageId, Instant rentedAt) {
        return pageRentalRepository.save(PageRental.start(readerId, bookPageId, rentedAt));
    }
}
