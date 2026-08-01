package com.example.ilgeobolkka.library.facade;

import com.example.ilgeobolkka.library.dto.FindLibraryResponse;
import com.example.ilgeobolkka.library.service.LibraryService;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LibraryFacade {

    private final LibraryService libraryService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public FindLibraryResponse findLibrary(long readerId) {
        return FindLibraryResponse.from(libraryService.findEntries(readerId), clock.instant());
    }
}
