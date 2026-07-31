package com.example.ilgeobolkka.library.service;

import com.example.ilgeobolkka.library.repository.LibraryEntryRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LibraryService {

    private final LibraryEntryRepository libraryEntryRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordVisit(long readerId, long bookId, int pageNumber, Instant visitedAt) {
        libraryEntryRepository.upsertLastReadPage(readerId, bookId, pageNumber, visitedAt);
    }
}
