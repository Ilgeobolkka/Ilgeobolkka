package com.example.ilgeobolkka.library.service;

import com.example.ilgeobolkka.library.repository.LibraryEntryRepository;
import com.example.ilgeobolkka.library.repository.LibraryEntryView;
import java.time.Instant;
import java.util.List;
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

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordOwnership(long readerId, long bookId, Instant ownedAt) {
        libraryEntryRepository.insertOwnedBookIfAbsent(readerId, bookId, ownedAt);
    }

    public List<LibraryEntryView> findEntries(long readerId, boolean aiRouteEnabled) {
        return libraryEntryRepository.findEntriesByReaderId(readerId, aiRouteEnabled);
    }
}
