package com.example.ilgeobolkka.airoute.service.assembly;

import com.example.ilgeobolkka.airoute.AiRouteAdditionalCostStatus;
import com.example.ilgeobolkka.airoute.AiRouteGenerationCommand;
import com.example.ilgeobolkka.airoute.AiRouteRequestType;
import com.example.ilgeobolkka.airoute.service.assembly.AiRouteGenerationResult.Item;
import com.example.ilgeobolkka.airoute.service.validation.ValidatedRouteProposal;
import com.example.ilgeobolkka.airoute.service.validation.ValidatedRouteProposal.ValidatedRouteItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 검증된 proposal을 생성 시점의 권한·비용 사본으로 조립한다. */
public final class AiRouteAssembler {

    private final AiRouteGuideFactory guideFactory;

    public AiRouteAssembler(AiRouteGuideFactory guideFactory) {
        if (guideFactory == null) {
            throw new IllegalArgumentException("가이드 팩토리가 필요합니다.");
        }
        this.guideFactory = guideFactory;
    }

    /** 후보 검색 결과가 비었을 때 Responses 호출 없이 만드는 정상 NO_ROUTE 결과다. */
    public AiRouteGenerationResult noRelevantPages() {
        return AiRouteGenerationResult.noRelevantPages();
    }

    public AiRouteGenerationResult assemble(
            ValidatedRouteProposal proposal,
            AiRouteGenerationCommand command,
            AiRouteEntitlementSnapshot entitlement,
            List<AiRouteAssemblyPage> pages) {
        requireInputs(proposal, command, entitlement, pages);

        Map<Integer, AiRouteAssemblyPage> pagesByNumber = pagesByNumber(pages);
        Map<Integer, ValidatedRouteItem> itemsByPageNumber = itemsByPageNumber(proposal, pagesByNumber);
        List<Integer> proposalCandidatePageNumbers = proposal.items().stream()
                .map(ValidatedRouteItem::pageNumber)
                .filter(proposal.prerequisiteClosureByCandidate()::containsKey)
                .toList();

        if (proposalCandidatePageNumbers.isEmpty()) {
            throw new IllegalArgumentException("검증된 경로에 관련 후보 페이지가 없습니다.");
        }

        Set<Integer> selectedPageNumbers = new LinkedHashSet<>();
        Set<String> selectedDuplicateGroups = new HashSet<>();
        int selectedAdditionalInk = 0;
        int pageLimit = pageLimit(command);

        for (Integer candidatePageNumber : proposalCandidatePageNumbers) {
            List<Integer> requiredPageNumbers = requiredPageNumbers(
                    candidatePageNumber, proposal, itemsByPageNumber);
            List<Integer> missingPageNumbers = requiredPageNumbers.stream()
                    .filter(pageNumber -> !selectedPageNumbers.contains(pageNumber))
                    .toList();

            if (hasDuplicateConflict(
                    missingPageNumbers, pagesByNumber, selectedDuplicateGroups)) {
                continue;
            }

            int additionalInk = additionalInk(
                    missingPageNumbers, entitlement);
            if (!fits(
                    command,
                    selectedPageNumbers.size(),
                    missingPageNumbers.size(),
                    selectedAdditionalInk,
                    additionalInk,
                    pageLimit)) {
                continue;
            }

            selectedPageNumbers.addAll(missingPageNumbers);
            selectedAdditionalInk += additionalInk;
            for (Integer pageNumber : missingPageNumbers) {
                selectedDuplicateGroups.addAll(
                        pagesByNumber.get(pageNumber).duplicateGroupKeys());
            }
        }

        if (selectedPageNumbers.isEmpty()) {
            List<List<Integer>> duplicateFreeRequiredPageNumbers = proposalCandidatePageNumbers.stream()
                    .map(candidatePageNumber -> requiredPageNumbers(
                            candidatePageNumber, proposal, itemsByPageNumber))
                    .filter(requiredPageNumbers -> !hasInternalDuplicate(requiredPageNumbers, pagesByNumber))
                    .toList();

            if (duplicateFreeRequiredPageNumbers.isEmpty()) {
                throw new IllegalStateException("중복 없이 완성할 수 있는 관련 후보 묶음이 없습니다.");
            }

            if (command.requestType() == AiRouteRequestType.OWNED_DEPTH) {
                throw new IllegalStateException("소장 경로를 선택한 깊이 상한 안에서 완성할 수 없습니다.");
            }

            int minimumRequiredInk = duplicateFreeRequiredPageNumbers.stream()
                    .mapToInt(requiredPageNumbers -> additionalInk(requiredPageNumbers, entitlement))
                    .min()
                    .orElseThrow();

            if (minimumRequiredInk <= command.maxAdditionalInk()) {
                throw new IllegalStateException("최소 필요 잉크가 선택 예산보다 크지 않습니다.");
            }
            return AiRouteGenerationResult.insufficientBudget(minimumRequiredInk);
        }

        return routeResult(proposal, entitlement, pagesByNumber, selectedPageNumbers);
    }

    private AiRouteGenerationResult routeResult(
            ValidatedRouteProposal proposal,
            AiRouteEntitlementSnapshot entitlement,
            Map<Integer, AiRouteAssemblyPage> pagesByNumber,
            Set<Integer> selectedPageNumbers) {
        boolean candidateIncluded = selectedPageNumbers.stream()
                .anyMatch(proposal.prerequisiteClosureByCandidate()::containsKey);
        if (!candidateIncluded) {
            throw new IllegalStateException("경로에는 관련 후보 페이지가 필요합니다.");
        }

        List<Item> resultItems = new ArrayList<>();
        for (ValidatedRouteItem validatedItem : proposal.items()) {
            if (!selectedPageNumbers.contains(validatedItem.pageNumber())) {
                continue;
            }

            AiRouteAssemblyPage page = pagesByNumber.get(validatedItem.pageNumber());
            resultItems.add(new Item(
                    validatedItem.pageId(),
                    validatedItem.pageNumber(),
                    resultItems.size() + 1,
                    validatedItem.relevance(),
                    validatedItem.prerequisite(),
                    validatedItem.role(),
                    estimatedMinutes(page.estimatedReadingSeconds()),
                    guideFactory.create(page.publicGuideTopic(), validatedItem.role()),
                    additionalCostStatus(validatedItem.pageNumber(), entitlement)));
        }

        return AiRouteGenerationResult.route(resultItems);
    }

    private void requireInputs(
            ValidatedRouteProposal proposal,
            AiRouteGenerationCommand command,
            AiRouteEntitlementSnapshot entitlement,
            List<AiRouteAssemblyPage> pages) {
        if (proposal == null || command == null || entitlement == null || pages == null) {
            throw new IllegalArgumentException("경로 조립 입력은 null일 수 없습니다.");
        }
        if (proposal.items().isEmpty()) {
            throw new IllegalArgumentException("검증된 경로 항목이 필요합니다.");
        }
        if (command.requestType() == AiRouteRequestType.OWNED_DEPTH && !entitlement.owned()) {
            throw new IllegalArgumentException("소장 깊이 요청에는 소장 권한 사본이 필요합니다.");
        }
        if (command.requestType() == AiRouteRequestType.INK_BUDGET) {
            if (entitlement.owned()) {
                throw new IllegalArgumentException("잉크 예산 요청에는 비소장 권한 사본이 필요합니다.");
            }
            if (command.maxAdditionalInk() > entitlement.inkBalance()) {
                throw new IllegalArgumentException("선택 예산이 생성 시점 잉크 잔액을 초과합니다.");
            }
        }
    }

    private Map<Integer, AiRouteAssemblyPage> pagesByNumber(
            List<AiRouteAssemblyPage> pages) {
        Map<Integer, AiRouteAssemblyPage> pagesByNumber = new HashMap<>();
        for (AiRouteAssemblyPage page : pages) {
            if (page == null || pagesByNumber.putIfAbsent(page.pageNumber(), page) != null) {
                throw new IllegalArgumentException("페이지 자료가 null이거나 페이지 번호가 중복되었습니다.");
            }
        }
        return pagesByNumber;
    }

    private Map<Integer, ValidatedRouteItem> itemsByPageNumber(
            ValidatedRouteProposal proposal,
            Map<Integer, AiRouteAssemblyPage> pagesByNumber) {
        Map<Integer, ValidatedRouteItem> itemsByPageNumber = new LinkedHashMap<>();
        for (ValidatedRouteItem item : proposal.items()) {
            AiRouteAssemblyPage page = pagesByNumber.get(item.pageNumber());
            if (page == null || page.pageId() != item.pageId()) {
                throw new IllegalArgumentException("검증 결과와 페이지 자료가 일치하지 않습니다.");
            }
            if (itemsByPageNumber.putIfAbsent(item.pageNumber(), item) != null) {
                throw new IllegalArgumentException("검증된 경로에 중복 페이지가 있습니다.");
            }
        }
        return itemsByPageNumber;
    }

    private List<Integer> requiredPageNumbers(
            int candidatePageNumber,
            ValidatedRouteProposal proposal,
            Map<Integer, ValidatedRouteItem> itemsByPageNumber) {
        Set<Integer> required = new LinkedHashSet<>(
                proposal.prerequisiteClosureByCandidate().get(candidatePageNumber));
        required.add(candidatePageNumber);

        List<Integer> orderedRequired = proposal.items().stream()
                .map(ValidatedRouteItem::pageNumber)
                .filter(required::contains)
                .toList();
        if (orderedRequired.size() != required.size()
                || !itemsByPageNumber.keySet().containsAll(required)) {
            throw new IllegalArgumentException("후보의 선수 페이지가 검증 결과에서 누락되었습니다.");
        }
        return orderedRequired;
    }

    private boolean hasDuplicateConflict(
            List<Integer> pageNumbers,
            Map<Integer, AiRouteAssemblyPage> pagesByNumber,
            Set<String> selectedDuplicateGroups) {
        Set<String> newGroups = new HashSet<>();
        for (Integer pageNumber : pageNumbers) {
            for (String group : pagesByNumber.get(pageNumber).duplicateGroupKeys()) {
                if (selectedDuplicateGroups.contains(group) || !newGroups.add(group)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasInternalDuplicate(
            List<Integer> pageNumbers, Map<Integer, AiRouteAssemblyPage> pagesByNumber) {
        return hasDuplicateConflict(pageNumbers, pagesByNumber, Set.of());
    }

    private int additionalInk(
            List<Integer> pageNumbers, AiRouteEntitlementSnapshot entitlement) {
        if (entitlement.owned()) {
            return 0;
        }
        return (int) pageNumbers.stream()
                .filter(pageNumber -> !entitlement.activeRentalPageNumbers().contains(pageNumber))
                .count();
    }

    private boolean fits(
            AiRouteGenerationCommand command,
            int selectedPageCount,
            int missingPageCount,
            int selectedAdditionalInk,
            int additionalInk,
            int pageLimit) {
        if (command.requestType() == AiRouteRequestType.OWNED_DEPTH) {
            return selectedPageCount + missingPageCount <= pageLimit;
        }
        return selectedAdditionalInk + additionalInk <= command.maxAdditionalInk();
    }

    private int pageLimit(AiRouteGenerationCommand command) {
        if (command.requestType() == AiRouteRequestType.INK_BUDGET) {
            return Integer.MAX_VALUE;
        }
        return switch (command.depth()) {
            case QUICK -> 5;
            case BALANCED -> 10;
            case DEEP -> 15;
        };
    }

    private int estimatedMinutes(int estimatedReadingSeconds) {
        return (int) Math.max(1L, (estimatedReadingSeconds + 59L) / 60L);
    }

    private AiRouteAdditionalCostStatus additionalCostStatus(
            int pageNumber, AiRouteEntitlementSnapshot entitlement) {
        if (entitlement.owned()) {
            return AiRouteAdditionalCostStatus.OWNED;
        }
        if (entitlement.activeRentalPageNumbers().contains(pageNumber)) {
            return AiRouteAdditionalCostStatus.ACTIVE_RENTAL;
        }
        return AiRouteAdditionalCostStatus.ONE_INK;
    }
}
