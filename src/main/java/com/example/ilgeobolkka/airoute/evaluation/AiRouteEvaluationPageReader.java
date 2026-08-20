package com.example.ilgeobolkka.airoute.evaluation;

import com.example.ilgeobolkka.book.entity.BookPage;
import com.example.ilgeobolkka.book.repository.BookPageRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 평가의 페이지 번호 권한 사본을 운영 DB의 페이지 식별자로 바꾸는 읽기 전용 경계다. */
@Component
@Profile("evaluation")
class AiRouteEvaluationPageReader {

    private final BookPageRepository bookPageRepository;

    AiRouteEvaluationPageReader(BookPageRepository bookPageRepository) {
        this.bookPageRepository = bookPageRepository;
    }

    Set<Long> activeRentalPageIds(long bookId, List<Integer> pageNumbers) {
        if (pageNumbers.isEmpty()) {
            return Set.of();
        }
        Map<Integer, BookPage> pagesByNumber = new HashMap<>();
        for (BookPage page : bookPageRepository.findAllByBookIdOrderByPageNumber(bookId)) {
            pagesByNumber.put(page.getPageNumber(), page);
        }

        Set<Long> pageIds = new HashSet<>();
        for (Integer pageNumber : pageNumbers) {
            BookPage page = pagesByNumber.get(pageNumber);
            if (page == null || !page.isAiRouteCandidate() || page.getId() == null) {
                throw new IllegalArgumentException(
                        "평가 활성 대여 페이지가 DB 후보 페이지가 아닙니다: bookId=%d, pageNumber=%d"
                                .formatted(bookId, pageNumber));
            }
            pageIds.add(page.getId());
        }
        return Set.copyOf(pageIds);
    }
}
