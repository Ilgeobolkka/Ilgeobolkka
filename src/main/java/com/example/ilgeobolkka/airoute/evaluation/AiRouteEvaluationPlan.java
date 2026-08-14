package com.example.ilgeobolkka.airoute.evaluation;

import com.example.ilgeobolkka.contentimport.manifest.AiRouteEvaluationDataset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record AiRouteEvaluationPlan(
        String contentVersion,
        String manifestSha256,
        String manifestGitRevision,
        String evaluationGitRevision,
        List<Case> cases) {

    AiRouteEvaluationPlan {
        cases = List.copyOf(cases);
    }

    record Case(Input input, Reference reference) {}

    record Input(
            String caseId,
            long bookId,
            String purpose,
            boolean owned,
            Integer maxAdditionalInk,
            AiRouteEvaluationDataset.Depth depth,
            List<Integer> activeRentalPageNumbers) {

        Input {
            activeRentalPageNumbers = List.copyOf(activeRentalPageNumbers);
        }

        @Override
        public String toString() {
            return ("Input[caseId=%s, bookId=%d, purposeCodePoints=%d, owned=%s, "
                            + "maxAdditionalInk=%s, depth=%s, activeRentals=%d]")
                    .formatted(
                            caseId,
                            bookId,
                            purpose.codePointCount(0, purpose.length()),
                            owned,
                            maxAdditionalInk,
                            depth,
                            activeRentalPageNumbers.size());
        }
    }

    record Reference(
            List<String> requiredConcepts,
            List<String> helpfulConcepts,
            List<AiRouteEvaluationDataset.RequiredPrerequisite> requiredPrerequisites,
            List<Integer> irrelevantPageNumbers,
            List<List<Integer>> duplicatePageGroups,
            List<Integer> referencePageNumbers,
            List<Integer> allowedAlternativePageNumbers,
            Map<Integer, List<String>> primaryConceptsByPage) {

        Reference {
            requiredConcepts = List.copyOf(requiredConcepts);
            helpfulConcepts = List.copyOf(helpfulConcepts);
            requiredPrerequisites = List.copyOf(requiredPrerequisites);
            irrelevantPageNumbers = List.copyOf(irrelevantPageNumbers);
            duplicatePageGroups = duplicatePageGroups.stream().map(List::copyOf).toList();
            referencePageNumbers = List.copyOf(referencePageNumbers);
            allowedAlternativePageNumbers = List.copyOf(allowedAlternativePageNumbers);
            Map<Integer, List<String>> copiedConcepts = new LinkedHashMap<>();
            primaryConceptsByPage.forEach(
                    (pageNumber, concepts) -> copiedConcepts.put(pageNumber, List.copyOf(concepts)));
            primaryConceptsByPage = Map.copyOf(copiedConcepts);
        }
    }
}
