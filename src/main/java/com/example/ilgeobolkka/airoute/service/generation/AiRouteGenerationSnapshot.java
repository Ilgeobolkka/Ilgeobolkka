package com.example.ilgeobolkka.airoute.service.generation;

import com.example.ilgeobolkka.airoute.AiRouteGenerationCommand;
import com.example.ilgeobolkka.airoute.service.assembly.AiRouteAssemblyPage;
import com.example.ilgeobolkka.airoute.service.candidate.AiRouteCandidatePage;
import java.util.List;
import java.util.Map;

/** 외부 호출 전에 한 읽기 트랜잭션에서 확정한 콘텐츠 입력 사본이다. */
public final class AiRouteGenerationSnapshot {

    private final AiRouteGenerationCommand command;
    private final String embeddingModel;
    private final int embeddingDimensions;
    private final List<AiRouteCandidatePage> candidatePages;
    private final List<AiRouteAssemblyPage> assemblyPages;
    private final List<AiRoutePrerequisiteService.PrerequisiteEdge> prerequisites;
    private final Map<String, String> analysisTexts;

    AiRouteGenerationSnapshot(
            AiRouteGenerationCommand command,
            String embeddingModel,
            int embeddingDimensions,
            List<AiRouteCandidatePage> candidatePages,
            List<AiRouteAssemblyPage> assemblyPages,
            List<AiRoutePrerequisiteService.PrerequisiteEdge> prerequisites,
            Map<String, String> analysisTexts) {
        this.command = command;
        this.embeddingModel = embeddingModel;
        this.embeddingDimensions = embeddingDimensions;
        this.candidatePages = List.copyOf(candidatePages);
        this.assemblyPages = List.copyOf(assemblyPages);
        this.prerequisites = List.copyOf(prerequisites);
        this.analysisTexts = Map.copyOf(analysisTexts);
    }

    /** production 권한 사본을 만들 때 필요한 후보 페이지 식별자만 노출한다. */
    public List<Long> candidatePageIds() {
        return candidatePages.stream().map(AiRouteCandidatePage::pageId).toList();
    }

    AiRouteGenerationCommand command() {
        return command;
    }

    String embeddingModel() {
        return embeddingModel;
    }

    int embeddingDimensions() {
        return embeddingDimensions;
    }

    List<AiRouteCandidatePage> candidatePages() {
        return candidatePages;
    }

    List<AiRouteAssemblyPage> assemblyPages() {
        return assemblyPages;
    }

    List<AiRoutePrerequisiteService.PrerequisiteEdge> prerequisites() {
        return prerequisites;
    }

    String analysisText(String reference) {
        String text = analysisTexts.get(reference);
        if (text == null) {
            throw new IllegalStateException("후보 페이지의 분석 텍스트를 찾지 못했습니다.");
        }
        return text;
    }
}
