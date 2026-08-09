package com.example.ilgeobolkka.infra.openai;

import java.util.List;

public interface OpenAiEmbeddingGateway {

    Embedding embedPurpose(PurposeInput input, String model, int dimensions);

    Embedding embedPageAnalysis(PageAnalysisInput input, String model, int dimensions);

    record PurposeInput(String normalizedPurpose) {
    }

    record PageAnalysisInput(String aiAnalysisText) {
    }

    record Embedding(List<Double> vector, String model, int dimensions) {
    }
}
