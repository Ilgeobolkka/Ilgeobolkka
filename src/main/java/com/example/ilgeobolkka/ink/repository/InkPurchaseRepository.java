package com.example.ilgeobolkka.ink.repository;

import com.example.ilgeobolkka.ink.entity.InkPurchase;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InkPurchaseRepository extends JpaRepository<InkPurchase, Long> {

    Optional<InkPurchase> findByIdAndReaderId(long id, long readerId);
}
