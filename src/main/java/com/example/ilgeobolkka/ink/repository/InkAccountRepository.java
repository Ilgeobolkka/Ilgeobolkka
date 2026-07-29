package com.example.ilgeobolkka.ink.repository;

import com.example.ilgeobolkka.ink.entity.InkAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InkAccountRepository extends JpaRepository<InkAccount, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT account FROM InkAccount account WHERE account.readerId = :readerId")
    Optional<InkAccount> findByReaderIdForUpdate(@Param("readerId") long readerId);
}
