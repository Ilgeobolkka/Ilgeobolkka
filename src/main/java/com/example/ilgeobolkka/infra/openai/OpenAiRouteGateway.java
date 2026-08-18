package com.example.ilgeobolkka.infra.openai;

import java.util.List;

public interface OpenAiRouteGateway {

    /** 실행 전에도 평가 결과의 재현 조건을 기록할 수 있는 고정 모델·prompt·schema 계약이다. */
    RouteContract routeContract();

    RouteGatewayResult proposeRoute(RouteInput input);

    record RouteContract(String model, String promptVersion, String schemaVersion) {}

    record RouteInput(
            String normalizedPurpose,
            List<CandidatePage> candidates) {

        public RouteInput {
            candidates = candidates == null ? null : List.copyOf(candidates);
        }

        @Override
        public String toString() {
            return "RouteInput[purposeLength=" + lengthOf(normalizedPurpose)
                    + ", candidates=" + sizeOf(candidates) + "]";
        }
    }

    record CandidatePage(int pageNumber, String analysisText) {

        @Override
        public String toString() {
            return "CandidatePage[pageNumber=" + pageNumber
                    + ", analysisTextLength=" + lengthOf(analysisText) + "]";
        }
    }

    record RouteGatewayResult(
            ModelRouteProposal proposal,
            String promptVersion,
            String schemaVersion) {
    }

    record ModelRouteProposal(List<ModelRouteItem> items) {

        public ModelRouteProposal {
            items = List.copyOf(items);
        }
    }

    record ModelRouteItem(
            int pageNumber,
            Relevance relevance,
            Role role) {
    }

    enum Relevance {
        HIGH,
        MEDIUM
    }

    enum Role {
        CORE,
        EXAMPLE,
        COUNTERPOINT,
        CONCLUSION
    }

    private static int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }

    private static int sizeOf(List<?> values) {
        return values == null ? 0 : values.size();
    }
}
