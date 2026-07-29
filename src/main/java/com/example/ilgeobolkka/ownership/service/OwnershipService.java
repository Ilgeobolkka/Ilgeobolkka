package com.example.ilgeobolkka.ownership.service;

import com.example.ilgeobolkka.ownership.repository.BookOwnershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OwnershipService {

    private final BookOwnershipRepository bookOwnershipRepository;

    public boolean isOwned(long readerId, long bookId) {
        return bookOwnershipRepository.existsByReaderIdAndBookId(readerId, bookId);
    }
}
