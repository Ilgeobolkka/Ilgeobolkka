package com.example.ilgeobolkka.library.service;

import com.example.ilgeobolkka.book.entity.BookPage;
import com.example.ilgeobolkka.library.entity.LibraryEntry;
import com.example.ilgeobolkka.library.repository.LibraryEntryRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LibraryEntryService {

    private final LibraryEntryRepository libraryEntryRepository;

    /**
     * 독자·도서 조합의 마지막 열람 위치를 기록한다. 이미 있으면 위치를 갱신하고, 없으면 새로
     * 만든다(uk_library_entry_reader_book이 독자당 도서 하나의 행만 허용).
     *
     * <p>{@code MANDATORY}로 호출자의 트랜잭션 합류를 강제한다. 갱신 경로는 {@code moveTo}의
     * 더티 체킹에 의존하므로, 트랜잭션 밖에서 호출되면 영속성 컨텍스트가 즉시 닫혀 **갱신이
     * 예외 없이 유실된다.** 새로 만드는 경로만 저장되고 갱신은 조용히 사라지는 비대칭을 막는다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordLastPosition(long readerId, BookPage page, Instant occurredAt) {
        libraryEntryRepository
                .findByReaderIdAndBookId(readerId, page.getBookId())
                .ifPresentOrElse(
                        entry -> entry.moveTo(page, occurredAt),
                        () -> libraryEntryRepository.save(
                                LibraryEntry.create(readerId, page, occurredAt)));
    }
}
