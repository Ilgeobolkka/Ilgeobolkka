package com.example.ilgeobolkka.infra.openai;

import java.util.List;

public interface OpenAiEmbeddingGateway {

    Embedding embedPurpose(PurposeInput input, String model, int dimensions);

    Embedding embedPageAnalysis(PageAnalysisInput input, String model, int dimensions);

    record PurposeInput(String normalizedPurpose) {

        @Override
        public String toString() {
            return "PurposeInput[type=purpose, length=" + lengthOf(normalizedPurpose) + "]";
        }
    }

    record PageAnalysisInput(String aiAnalysisText) {

        @Override
        public String toString() {
            return "PageAnalysisInput[type=pageAnalysis, length=" + lengthOf(aiAnalysisText) + "]";
        }
    }

    record Embedding(List<Double> vector, String model, int dimensions) {

        @Override
        public String toString() {
            return "Embedding[model=" + model + ", dimensions=" + dimensions + "]";
        }
    }

    private static int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }
}
