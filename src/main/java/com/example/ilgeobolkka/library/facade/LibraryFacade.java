package com.example.ilgeobolkka.library.facade;

import com.example.ilgeobolkka.infra.openai.AiRouteFeatureProperties;
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
    private final AiRouteFeatureProperties aiRouteFeatureProperties;

    @Transactional(readOnly = true)
    public FindLibraryResponse findLibrary(long readerId) {
        boolean aiRouteEnabled = aiRouteFeatureProperties.enabled();
        return FindLibraryResponse.from(
                libraryService.findEntries(readerId, aiRouteEnabled),
                clock.instant(),
                aiRouteEnabled);
    }
}
