package com.example.ilgeobolkka.ink.repository;

import com.example.ilgeobolkka.ink.entity.InkLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InkLedgerRepository extends JpaRepository<InkLedger, Long> {

    boolean existsByInkPurchaseId(long inkPurchaseId);

    boolean existsByPageRentalId(long pageRentalId);
}
