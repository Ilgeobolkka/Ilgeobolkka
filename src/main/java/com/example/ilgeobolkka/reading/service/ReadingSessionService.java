package com.example.ilgeobolkka.reading.service;

import com.example.ilgeobolkka.reading.entity.ReadingSession;
import com.example.ilgeobolkka.reading.exception.ReadingSessionNotFoundException;
import com.example.ilgeobolkka.reading.exception.ViewerSessionReplacedException;
import com.example.ilgeobolkka.reading.repository.ReadingSessionRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReadingSessionService {

    private final ReadingSessionRepository readingSessionRepository;

    public void invalidateCurrentSession(long readerId) {
        readingSessionRepository.deleteByReaderId(readerId);
    }

    /** 현재 세션이 없으면 404, 요청한 뷰어가 현재 세션과 다르면 409에 해당하는 예외를 던진다. */
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public ReadingSession getCurrentSession(long readerId, UUID viewerSessionId) {
        ReadingSession session =
                readingSessionRepository
                        .findByReaderId(readerId)
                        .orElseThrow(() -> new ReadingSessionNotFoundException(readerId));
        if (!session.matchesViewer(viewerSessionId)) {
            throw new ViewerSessionReplacedException(readerId);
        }
        return session;
    }

    /** 독자당 하나뿐인 현재 세션을 새 책·페이지·뷰어로 발급하거나 교체한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void openOrReplace(
            long readerId, long bookId, int pageNumber, UUID viewerSessionId, Instant now) {
        readingSessionRepository.upsertCurrentSession(
                readerId, bookId, pageNumber, viewerSessionId.toString(), now);
    }
}
