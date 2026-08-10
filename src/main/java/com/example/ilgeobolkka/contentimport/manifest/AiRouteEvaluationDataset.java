package com.example.ilgeobolkka.contentimport.manifest;

import java.util.List;

public record AiRouteEvaluationDataset(String contentVersion, List<EvaluationCase> cases) {

    public AiRouteEvaluationDataset {
        if (cases != null) {
            cases = List.copyOf(cases);
        }
    }

    public record EvaluationCase(
            String caseId,
            long bookId,
            String purpose,
            boolean owned,
            Integer maxAdditionalInk,
            Depth depth,
            List<Integer> activeRentalPageNumbers,
            List<String> requiredConcepts,
            List<String> helpfulConcepts,
            List<RequiredPrerequisite> requiredPrerequisites,
            List<Integer> irrelevantPageNumbers,
            List<List<Integer>> duplicatePageGroups,
            List<Integer> referencePageNumbers,
            List<Integer> allowedAlternativePageNumbers) {

        public EvaluationCase {
            if (activeRentalPageNumbers != null) {
                activeRentalPageNumbers = List.copyOf(activeRentalPageNumbers);
            }
            if (requiredConcepts != null) {
                requiredConcepts = List.copyOf(requiredConcepts);
            }
            if (helpfulConcepts != null) {
                helpfulConcepts = List.copyOf(helpfulConcepts);
            }
            if (requiredPrerequisites != null) {
                requiredPrerequisites = List.copyOf(requiredPrerequisites);
            }
            if (irrelevantPageNumbers != null) {
                irrelevantPageNumbers = List.copyOf(irrelevantPageNumbers);
            }
            if (duplicatePageGroups != null) {
                duplicatePageGroups =
                        duplicatePageGroups.stream()
                                .map(group -> group == null ? null : List.copyOf(group))
                                .toList();
            }
            if (referencePageNumbers != null) {
                referencePageNumbers = List.copyOf(referencePageNumbers);
            }
            if (allowedAlternativePageNumbers != null) {
                allowedAlternativePageNumbers = List.copyOf(allowedAlternativePageNumbers);
            }
        }
    }

    public record RequiredPrerequisite(int beforePageNumber, int afterPageNumber) {}

    public enum Depth {
        QUICK,
        BALANCED,
        DEEP
    }
}
