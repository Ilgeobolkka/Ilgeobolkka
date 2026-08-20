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
        Map<Integer, Set<Integer>> allClosureByCandidate = new LinkedHashMap<>();
        Set<Integer> allowedPageNumbers = new LinkedHashSet<>();

        for (AiRouteCandidate candidate : candidates) {
            Set<Integer> closure =
                    prerequisiteClosure(candidate.pageNumber(), pagesByNumber, closureMemo, new HashSet<>());
            allClosureByCandidate.put(candidate.pageNumber(), closure);
            allowedPageNumbers.add(candidate.pageNumber());
            allowedPageNumbers.addAll(closure);
        }

        List<ModelRouteItem> proposalItems = requireSemanticItems(
                proposal,
                pagesByNumber,
                allClosureByCandidate.keySet());
        Map<Integer, ModelRouteItem> proposalItemsByPageNumber = new LinkedHashMap<>();
        Map<Integer, Set<Integer>> selectedClosureByCandidate = new LinkedHashMap<>();
        Set<Integer> serverPrerequisitePageNumbers = new LinkedHashSet<>();
        for (ModelRouteItem item : proposalItems) {
            proposalItemsByPageNumber.put(item.pageNumber(), item);
            Set<Integer> closure = allClosureByCandidate.get(item.pageNumber());
            selectedClosureByCandidate.put(item.pageNumber(), closure);
            serverPrerequisitePageNumbers.addAll(closure);
        }

        List<Integer> orderedPageNumbers = new ArrayList<>();
        Set<Integer> emittedPageNumbers = new HashSet<>();
        for (ModelRouteItem item : proposalItems) {
            appendWithPrerequisites(
                    item.pageNumber(),
                    pagesByNumber,
                    emittedPageNumbers,
                    new HashSet<>(),
                    orderedPageNumbers);
        }

        List<ValidatedRouteItem> validatedItems = new ArrayList<>();

        for (int index = 0; index < orderedPageNumbers.size(); index++) {
            int pageNumber = orderedPageNumbers.get(index);
            ModelRouteItem item = proposalItemsByPageNumber.get(pageNumber);
            AiRouteCandidatePage page = pagesByNumber.get(pageNumber);
            validatedItems.add(new ValidatedRouteItem(
                    page.pageId(),
                    pageNumber,
                    index + 1,
                    item == null ? AiRouteItemRelevance.MEDIUM : toDomainRelevance(item),
                    serverPrerequisitePageNumbers.contains(pageNumber),
                    item == null ? AiRouteItemRole.PREREQUISITE : toDomainRole(item)));
        }

        return new ValidatedRouteProposal(
                validatedItems, selectedClosureByCandidate, allowedPageNumbers);
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
            Set<Integer> candidatePageNumbers) {
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

            if (!candidatePageNumbers.contains(item.pageNumber())) {
                throw fail(Failure.PAGE_OUTSIDE_ALLOWED_SET);
            }

            if (!seenPageNumbers.add(item.pageNumber())) {
                throw fail(Failure.DUPLICATE_PAGE);
            }
        }

        return proposal.items();
    }

    private void appendWithPrerequisites(
            int pageNumber,
            Map<Integer, AiRouteCandidatePage> pagesByNumber,
            Set<Integer> emittedPageNumbers,
            Set<Integer> visiting,
            List<Integer> orderedPageNumbers) {
        if (emittedPageNumbers.contains(pageNumber)) {
            return;
        }
        if (!visiting.add(pageNumber)) {
            throw fail(Failure.CONTEXT_MISMATCH);
        }

        AiRouteCandidatePage page = pagesByNumber.get(pageNumber);
        if (page == null) {
            throw fail(Failure.CONTEXT_MISMATCH);
        }
        for (Integer prerequisite : page.prerequisitePageNumbers()) {
            appendWithPrerequisites(
                    prerequisite,
                    pagesByNumber,
                    emittedPageNumbers,
                    visiting,
                    orderedPageNumbers);
        }
        visiting.remove(pageNumber);
        emittedPageNumbers.add(pageNumber);
        orderedPageNumbers.add(pageNumber);
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
