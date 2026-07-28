package com.example.ilgeobolkka.reading.service;

import com.example.ilgeobolkka.reading.repository.ReadingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReadingSessionService {

    private final ReadingSessionRepository readingSessionRepository;

    public void invalidateCurrentSession(long readerId) {
        readingSessionRepository.deleteByReaderId(readerId);
    }
}
