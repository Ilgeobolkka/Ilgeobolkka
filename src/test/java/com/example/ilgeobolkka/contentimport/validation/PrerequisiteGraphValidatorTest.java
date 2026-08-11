package com.example.ilgeobolkka.contentimport.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ilgeobolkka.contentimport.manifest.AiRouteContentManifest;
import java.util.List;
import org.junit.jupiter.api.Test;

class PrerequisiteGraphValidatorTest {

    private static final long BOOK_ID = 41L;

    private final PrerequisiteGraphValidator validator = new PrerequisiteGraphValidator();

    @Test
    void 선수가_앞에_오는_위상_순서를_만든다() {
        List<AiRouteContentManifest.Page> pages =
                List.of(
                        page(1, List.of()),
                        page(2, List.of(1)),
                        page(3, List.of(1)),
                        page(4, List.of(2, 3)));

        List<Integer> order = validator.topologicalOrder(BOOK_ID, pages);

        assertEquals(List.of(1, 2, 3, 4), order);
    }

    @Test
    void root가_여럿이고_선수가_비어도_허용한다() {
        List<AiRouteContentManifest.Page> pages =
                List.of(page(1, List.of()), page(2, List.of()), page(3, List.of(2)));

        List<Integer> order = validator.topologicalOrder(BOOK_ID, pages);

        assertEquals(List.of(1, 2, 3), order);
    }

    @Test
    void 같은_입력은_항상_같은_순서를_낸다() {
        List<AiRouteContentManifest.Page> pages =
                List.of(page(1, List.of()), page(2, List.of()), page(3, List.of()));

        assertEquals(
                validator.topologicalOrder(BOOK_ID, pages),
                validator.topologicalOrder(BOOK_ID, pages));
    }

    @Test
    void 존재하지_않는_페이지를_선수로_가리키면_실패한다() {
        List<AiRouteContentManifest.Page> pages = List.of(page(1, List.of()), page(2, List.of(99)));

        AiRouteContentValidationException exception =
                assertThrows(
                        AiRouteContentValidationException.class,
                        () -> validator.topologicalOrder(BOOK_ID, pages));

        assertTrue(exception.getMessage().contains("같은 도서에 없는 페이지"));
    }

    @Test
    void 자기_자신을_선수로_가리키면_실패한다() {
        List<AiRouteContentManifest.Page> pages = List.of(page(1, List.of()), page(2, List.of(2)));

        AiRouteContentValidationException exception =
                assertThrows(
                        AiRouteContentValidationException.class,
                        () -> validator.topologicalOrder(BOOK_ID, pages));

        assertTrue(exception.getMessage().contains("자기 자신"));
    }

    @Test
    void 같은_선수를_두_번_가리키면_실패한다() {
        List<AiRouteContentManifest.Page> pages =
                List.of(page(1, List.of()), page(2, List.of(1, 1)));

        AiRouteContentValidationException exception =
                assertThrows(
                        AiRouteContentValidationException.class,
                        () -> validator.topologicalOrder(BOOK_ID, pages));

        assertTrue(exception.getMessage().contains("중복"));
    }

    @Test
    void 두_페이지가_서로를_가리키는_순환은_실패한다() {
        List<AiRouteContentManifest.Page> pages = List.of(page(1, List.of(2)), page(2, List.of(1)));

        AiRouteContentValidationException exception =
                assertThrows(
                        AiRouteContentValidationException.class,
                        () -> validator.topologicalOrder(BOOK_ID, pages));

        assertTrue(exception.getMessage().contains("순환"));
    }

    @Test
    void 세_페이지를_도는_순환도_실패한다() {
        List<AiRouteContentManifest.Page> pages =
                List.of(page(1, List.of(3)), page(2, List.of(1)), page(3, List.of(2)));

        AiRouteContentValidationException exception =
                assertThrows(
                        AiRouteContentValidationException.class,
                        () -> validator.topologicalOrder(BOOK_ID, pages));

        assertTrue(exception.getMessage().contains("순환"));
    }

    @Test
    void 후보가_아닌_페이지를_선수로_가리키면_실패한다() {
        List<AiRouteContentManifest.Page> pages =
                List.of(frontMatterPage(1), page(2, List.of(1)));

        AiRouteContentValidationException exception =
                assertThrows(
                        AiRouteContentValidationException.class,
                        () -> validator.topologicalOrder(BOOK_ID, pages));

        assertTrue(exception.getMessage().contains("후보가 아닌 페이지"));
    }

    private AiRouteContentManifest.Page page(int pageNumber, List<Integer> prerequisites) {
        return AiRouteContentPages.candidate(pageNumber, "1장", prerequisites);
    }

    private AiRouteContentManifest.Page frontMatterPage(int pageNumber) {
        return AiRouteContentPages.frontMatter(pageNumber);
    }
}
