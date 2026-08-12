package com.example.ilgeobolkka.airoute.service.validation;

import com.example.ilgeobolkka.airoute.entity.AiRouteItemRelevance;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRole;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ValidatedRouteProposal(
        List<ValidatedRouteItem> items,
        Map<Integer, Set<Integer>> prerequisiteClosureByCandidate,
        Set<Integer> allowedPageNumbers) {

    public ValidatedRouteProposal {
        items = List.copyOf(items);

        Map<Integer, Set<Integer>> closureCopy = new LinkedHashMap<>();
        prerequisiteClosureByCandidate.forEach((pageNumber, closure) -> closureCopy.put(
                pageNumber,
                Collections.unmodifiableSet(new LinkedHashSet<>(closure))));
        prerequisiteClosureByCandidate = Collections.unmodifiableMap(closureCopy);
        allowedPageNumbers =
                Collections.unmodifiableSet(new LinkedHashSet<>(allowedPageNumbers));
    }

    public record ValidatedRouteItem(
            long pageId,
            int pageNumber,
            int position,
            AiRouteItemRelevance relevance,
            boolean prerequisite,
            AiRouteItemRole role) {}
}
