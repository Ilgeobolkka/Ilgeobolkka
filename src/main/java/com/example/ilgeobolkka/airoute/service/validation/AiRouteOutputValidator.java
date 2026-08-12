package com.example.ilgeobolkka.airoute.service.validation;

import com.example.ilgeobolkka.airoute.entity.AiRouteItemRelevance;
import com.example.ilgeobolkka.airoute.entity.AiRouteItemRole;
import com.example.ilgeobolkka.airoute.service.candidate.AiRouteCandidate;
import com.example.ilgeobolkka.airoute.service.candidate.AiRouteCandidatePage;
import com.example.ilgeobolkka.airoute.service.candidate.AiRouteCandidatePolicy;
import com.example.ilgeobolkka.airoute.service.validation.AiRouteInvalidOutputException.Failure;
import com.example.ilgeobolkka.airoute.service.validation.ValidatedRouteProposal.ValidatedRouteItem;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway.ModelRouteItem;
import com.example.ilgeobolkka.infra.openai.OpenAiRouteGateway.ModelRouteProposal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AiRouteOutputValidator {

    public ValidatedRouteProposal validate(
            long bookId,
            String contentVersion,
            List<AiRouteCandidatePage> pages,
            List<AiRouteCandidate> candidates,
            ModelRouteProposal proposal) {
        Map<Integer, AiRouteCandidatePage> pagesByNumber =
                requireConsistentContext(bookId, contentVersion, pages, candidates);

        Map<Integer, Set<Integer>> closureMemo = new HashMap<>();
        Map<Integer, Set<Integer>> closureByCandidate = new LinkedHashMap<>();
        Set<Integer> allowedPageNumbers = new LinkedHashSet<>();
        Set<Integer> serverPrerequisitePageNumbers = new LinkedHashSet<>();

        for (AiRouteCandidate candidate : candidates) {
            Set<Integer> closure =
                    prerequisiteClosure(candidate.pageNumber(), pagesByNumber, closureMemo, new HashSet<>());
            closureByCandidate.put(candidate.pageNumber(), closure);
            allowedPageNumbers.add(candidate.pageNumber());
            allowedPageNumbers.addAll(closure);
            serverPrerequisitePageNumbers.addAll(closure);
        }

        List<ModelRouteItem> proposalItems = requireSemanticItems(
                proposal, pagesByNumber, allowedPageNumbers);
        requireCandidateIncluded(proposalItems, closureByCandidate.keySet());
        Map<Integer, Integer> positionByPageNumber = positionsOf(proposalItems);
        requirePrerequisitesBeforeDependents(
                proposalItems, positionByPageNumber, pagesByNumber, closureMemo);

        List<ValidatedRouteItem> validatedItems = new ArrayList<>();

        for (int index = 0; index < proposalItems.size(); index++) {
            ModelRouteItem item = proposalItems.get(index);
            AiRouteCandidatePage page = pagesByNumber.get(item.pageNumber());
            validatedItems.add(new ValidatedRouteItem(
                    page.pageId(),
                    item.pageNumber(),
                    index + 1,
                    toDomainRelevance(item),
                    serverPrerequisitePageNumbers.contains(item.pageNumber()),
                    toDomainRole(item)));
        }

        return new ValidatedRouteProposal(
                validatedItems, closureByCandidate, allowedPageNumbers);
    }

    private Map<Integer, AiRouteCandidatePage> requireConsistentContext(
            long bookId,
            String contentVersion,
            List<AiRouteCandidatePage> pages,
            List<AiRouteCandidate> candidates) {
        if (bookId <= 0
                || contentVersion == null
                || contentVersion.isBlank()
                || pages == null
                || candidates == null
                || candidates.isEmpty()
                || candidates.size() > AiRouteCandidatePolicy.MAXIMUM_CANDIDATES) {
            throw fail(Failure.CONTEXT_MISMATCH);
        }

        Map<Integer, AiRouteCandidatePage> pagesByNumber = new HashMap<>();

        for (AiRouteCandidatePage page : pages) {
            if (page == null
                    || page.bookId() != bookId
                    || !contentVersion.equals(page.contentVersion())
                    || pagesByNumber.putIfAbsent(page.pageNumber(), page) != null) {
                throw fail(Failure.CONTEXT_MISMATCH);
            }
        }

        for (AiRouteCandidatePage page : pages) {
            for (Integer prerequisite : page.prerequisitePageNumbers()) {
                if (prerequisite == null || !pagesByNumber.containsKey(prerequisite)) {
                    throw fail(Failure.CONTEXT_MISMATCH);
                }
            }
        }

        Set<Integer> candidatePageNumbers = new HashSet<>();

        for (AiRouteCandidate candidate : candidates) {
            if (candidate == null || !candidatePageNumbers.add(candidate.pageNumber())) {
                throw fail(Failure.CONTEXT_MISMATCH);
            }
            AiRouteCandidatePage page = pagesByNumber.get(candidate.pageNumber());

            if (page == null
                    || page.pageId() != candidate.pageId()
                    || !page.prerequisitePageNumbers().equals(candidate.prerequisitePageNumbers())) {
                throw fail(Failure.CONTEXT_MISMATCH);
            }
        }

        return pagesByNumber;
    }

    private Set<Integer> prerequisiteClosure(
            int pageNumber,
            Map<Integer, AiRouteCandidatePage> pagesByNumber,
            Map<Integer, Set<Integer>> closureMemo,
            Set<Integer> visiting) {
        Set<Integer> cached = closureMemo.get(pageNumber);

        if (cached != null) {
            return cached;
        }

        if (!visiting.add(pageNumber)) {
            throw fail(Failure.CONTEXT_MISMATCH);
        }

        AiRouteCandidatePage page = pagesByNumber.get(pageNumber);

        if (page == null) {
            throw fail(Failure.CONTEXT_MISMATCH);
        }

        Set<Integer> closure = new LinkedHashSet<>();

        for (Integer prerequisite : page.prerequisitePageNumbers()) {
            closure.addAll(prerequisiteClosure(
                    prerequisite, pagesByNumber, closureMemo, visiting));
            closure.add(prerequisite);
        }

        visiting.remove(pageNumber);

        Set<Integer> immutable =
                Collections.unmodifiableSet(new LinkedHashSet<>(closure));
        closureMemo.put(pageNumber, immutable);

        return immutable;
    }

    private List<ModelRouteItem> requireSemanticItems(
            ModelRouteProposal proposal,
            Map<Integer, AiRouteCandidatePage> pagesByNumber,
            Set<Integer> allowedPageNumbers) {
        if (proposal == null || proposal.items().isEmpty()) {
            throw fail(Failure.EMPTY_PROPOSAL);
        }

        Set<Integer> seenPageNumbers = new HashSet<>();

        for (ModelRouteItem item : proposal.items()) {
            if (item == null || item.relevance() == null || item.role() == null) {
                throw fail(Failure.INVALID_ENUM);
            }

            if (!pagesByNumber.containsKey(item.pageNumber())) {
                throw fail(Failure.PAGE_NOT_FOUND);
            }

            if (!allowedPageNumbers.contains(item.pageNumber())) {
                throw fail(Failure.PAGE_OUTSIDE_ALLOWED_SET);
            }

            if (!seenPageNumbers.add(item.pageNumber())) {
                throw fail(Failure.DUPLICATE_PAGE);
            }
        }

        return proposal.items();
    }

    private void requireCandidateIncluded(
            List<ModelRouteItem> items, Set<Integer> candidatePageNumbers) {
        boolean candidateIncluded = items.stream()
                .map(ModelRouteItem::pageNumber)
                .anyMatch(candidatePageNumbers::contains);
        if (!candidateIncluded) {
            throw fail(Failure.MISSING_CANDIDATE);
        }
    }

    private Map<Integer, Integer> positionsOf(List<ModelRouteItem> items) {
        Map<Integer, Integer> positions = new HashMap<>();

        for (int index = 0; index < items.size(); index++) {
            positions.put(items.get(index).pageNumber(), index + 1);
        }

        return positions;
    }

    private void requirePrerequisitesBeforeDependents(
            List<ModelRouteItem> items,
            Map<Integer, Integer> positionByPageNumber,
            Map<Integer, AiRouteCandidatePage> pagesByNumber,
            Map<Integer, Set<Integer>> closureMemo) {
        for (ModelRouteItem item : items) {
            int dependentPosition = positionByPageNumber.get(item.pageNumber());
            Set<Integer> prerequisites = prerequisiteClosure(
                    item.pageNumber(), pagesByNumber, closureMemo, new HashSet<>());

            for (Integer prerequisite : prerequisites) {
                Integer prerequisitePosition = positionByPageNumber.get(prerequisite);

                if (prerequisitePosition == null) {
                    throw fail(Failure.MISSING_PREREQUISITE);
                }

                if (prerequisitePosition >= dependentPosition) {
                    throw fail(Failure.INVALID_PREREQUISITE_ORDER);
                }
            }
        }
    }

    private AiRouteItemRelevance toDomainRelevance(ModelRouteItem item) {
        try {
            return AiRouteItemRelevance.valueOf(item.relevance().name());
        } catch (IllegalArgumentException exception) {
            throw fail(Failure.INVALID_ENUM);
        }
    }

    private AiRouteItemRole toDomainRole(ModelRouteItem item) {
        try {
            return AiRouteItemRole.valueOf(item.role().name());
        } catch (IllegalArgumentException exception) {
            throw fail(Failure.INVALID_ENUM);
        }
    }

    private AiRouteInvalidOutputException fail(Failure failure) {
        return new AiRouteInvalidOutputException(failure);
    }
}
