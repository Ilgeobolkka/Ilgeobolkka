package com.example.ilgeobolkka.reading.service;

import com.example.ilgeobolkka.book.entity.BookPage;
import com.example.ilgeobolkka.reading.entity.ReadingSession;
import com.example.ilgeobolkka.reading.exception.ReadingSessionNotFoundException;
import com.example.ilgeobolkka.reading.exception.ViewerSessionReplacedException;
import com.example.ilgeobolkka.reading.repository.ReadingSessionRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReadingSessionService {

    private final ReadingSessionRepository readingSessionRepository;

    public void invalidateCurrentSession(long readerId) {
        readingSessionRepository.deleteByReaderId(readerId);
    }

    /**
     * 헤더로 전달된 뷰어 세션이 독자의 현재 세션과 일치할 때만 반환한다. 세션 레코드 자체가 없으면
     * {@link ReadingSessionNotFoundException}(404), 레코드는 있으나 다른 뷰어 세션으로 이미
     * 교체되었으면 {@link ViewerSessionReplacedException}(409)을 던져 두 실패 원인을 구분한다.
     */
    public ReadingSession findCurrentSession(long readerId, UUID viewerSessionId) {
        ReadingSession session = readingSessionRepository
                .findByReaderId(readerId)
                .orElseThrow(() -> new ReadingSessionNotFoundException(readerId));
        if (!session.getViewerSessionId().equals(viewerSessionId)) {
            throw new ViewerSessionReplacedException(readerId);
        }
        return session;
    }

    /** 새 뷰어를 열며 독자당 하나인 현재 {@link ReadingSession}을 교체한다. */
    public ReadingSession openNewSession(long readerId, BookPage page, Instant openedAt) {
        readingSessionRepository.deleteByReaderId(readerId);
        return readingSessionRepository.save(
                ReadingSession.open(readerId, page, UUID.randomUUID(), openedAt));
    }

    /** 같은 뷰어 세션에서 현재 페이지 위치만 이동한다. */
    public ReadingSession moveCurrentSession(ReadingSession session, BookPage page, Instant movedAt) {
        session.moveTo(page, movedAt);
        return session;
    }
}
