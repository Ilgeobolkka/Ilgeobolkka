package com.example.ilgeobolkka.rental.repository;

import com.example.ilgeobolkka.rental.entity.PageRental;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PageRentalRepository extends JpaRepository<PageRental, Long> {

    Optional<PageRental> findFirstByReaderIdAndBookPageIdOrderByRentedAtDesc(
            long readerId, long bookPageId);
}
