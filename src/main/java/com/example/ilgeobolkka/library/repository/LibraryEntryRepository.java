package com.example.ilgeobolkka.library.repository;

import com.example.ilgeobolkka.library.entity.LibraryEntry;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibraryEntryRepository extends JpaRepository<LibraryEntry, Long> {

    Optional<LibraryEntry> findByReaderIdAndBookId(long readerId, long bookId);
}
