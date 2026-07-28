package com.example.ilgeobolkka.ownership.repository;

import com.example.ilgeobolkka.ownership.entity.BookOwnership;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookOwnershipRepository extends JpaRepository<BookOwnership, Long> {

    boolean existsByReaderIdAndBookId(long readerId, long bookId);
}
