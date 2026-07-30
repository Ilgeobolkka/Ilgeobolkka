package com.example.ilgeobolkka.ownership.repository;

import com.example.ilgeobolkka.ownership.entity.OwnershipPayment;
import com.example.ilgeobolkka.ownership.entity.OwnershipPaymentStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnershipPaymentRepository extends JpaRepository<OwnershipPayment, Long> {

    Optional<OwnershipPayment> findFirstByReaderIdAndBookIdAndStatusOrderByIdDesc(
            long readerId,
            long bookId,
            OwnershipPaymentStatus status);
}
