package com.example.ilgeobolkka.contentimport.validation;

import com.example.ilgeobolkka.contentimport.manifest.AiRouteContentManifest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 도서 한 권의 선수 관계를 {@code 선수 -> 의존} 방향 그래프로 검증하고 위상 순서를 만든다.
 *
 * <p>순환이 있으면 경로 생성이 끝나지 않는 순서를 만들 수 있으므로 적재 전에 막는다. 후보가 아닌
 * 페이지를 선수로 걸면 임베딩이 없어 도달할 수 없는 선수가 되므로 함께 막는다.
 */
public final class PrerequisiteGraphValidator {

    /**
     * @return 선수 페이지가 항상 앞에 오는 페이지 번호 순서. 같은 단계는 페이지 번호 오름차순이라
     *     같은 입력이 항상 같은 순서를 낸다.
     */
    public List<Integer> topologicalOrder(long bookId, List<AiRouteContentManifest.Page> pages) {
        Map<Integer, AiRouteContentManifest.Page> byNumber = new HashMap<>();
        for (AiRouteContentManifest.Page page : pages) {
            byNumber.put(page.pageNumber(), page);
        }
        Set<Integer> nonCandidates = new HashSet<>();
        for (AiRouteContentManifest.Page page : pages) {
            if (!page.aiRouteCandidatePage()) {
                nonCandidates.add(page.pageNumber());
            }
        }

        Map<Integer, List<Integer>> dependents = new TreeMap<>();
        Map<Integer, Integer> inDegree = new TreeMap<>();
        for (AiRouteContentManifest.Page page : pages) {
            dependents.put(page.pageNumber(), new ArrayList<>());
            inDegree.put(page.pageNumber(), 0);
        }

        for (AiRouteContentManifest.Page page : pages) {
            int dependent = page.pageNumber();
            Set<Integer> seen = new LinkedHashSet<>();
            for (Integer before : page.prerequisitePageNumbers()) {
                if (!byNumber.containsKey(before)) {
                    throw fail(bookId, dependent, "선수 %d는 같은 도서에 없는 페이지입니다.".formatted(before));
                }
                if (before == dependent) {
                    throw fail(bookId, dependent, "자기 자신을 선수로 가질 수 없습니다.");
                }
                if (!seen.add(before)) {
                    throw fail(bookId, dependent, "선수 %d가 중복됩니다.".formatted(before));
                }
                if (nonCandidates.contains(before)) {
                    throw fail(
                            bookId,
                            dependent,
                            "선수 %d는 후보가 아닌 페이지라 도달할 수 없습니다.".formatted(before));
                }
                dependents.get(before).add(dependent);
                inDegree.merge(dependent, 1, Integer::sum);
            }
        }

        Deque<Integer> ready = new ArrayDeque<>();
        inDegree.forEach(
                (pageNumber, degree) -> {
                    if (degree == 0) {
                        ready.add(pageNumber);
                    }
                });
        List<Integer> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            int current = ready.poll();
            order.add(current);
            for (Integer dependent : dependents.get(current)) {
                if (inDegree.merge(dependent, -1, Integer::sum) == 0) {
                    ready.add(dependent);
                }
            }
        }
        if (order.size() != pages.size()) {
            List<Integer> unresolved =
                    inDegree.entrySet().stream()
                            .filter(entry -> entry.getValue() > 0)
                            .map(Map.Entry::getKey)
                            .toList();
            throw new AiRouteContentValidationException(
                    "book %d 선수 관계에 순환이 있습니다: 방문하지 못한 페이지 %s"
                            .formatted(bookId, unresolved));
        }
        return List.copyOf(order);
    }

    private AiRouteContentValidationException fail(long bookId, int pageNumber, String message) {
        return new AiRouteContentValidationException(
                "book %d p%d 선수 관계 오류: %s".formatted(bookId, pageNumber, message));
    }
}
