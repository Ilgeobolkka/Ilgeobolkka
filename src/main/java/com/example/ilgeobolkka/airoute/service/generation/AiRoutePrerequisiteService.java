package com.example.ilgeobolkka.airoute.service.generation;

import com.example.ilgeobolkka.airoute.repository.AiRoutePrerequisiteRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 생성 snapshot에 필요한 현재 도서의 직접 선수 관계를 읽는다. */
@Service
@RequiredArgsConstructor
public class AiRoutePrerequisiteService {

    private final AiRoutePrerequisiteRepository prerequisiteRepository;

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<PrerequisiteEdge> findByBookId(long bookId) {
        return prerequisiteRepository
                .findAllByBookIdOrderByDependentPageNumberAscPrerequisitePageNumberAsc(bookId)
                .stream()
                .map(edge -> new PrerequisiteEdge(
                        edge.getPrerequisitePageNumber(), edge.getDependentPageNumber()))
                .toList();
    }

    public record PrerequisiteEdge(int prerequisitePageNumber, int dependentPageNumber) {}
}
