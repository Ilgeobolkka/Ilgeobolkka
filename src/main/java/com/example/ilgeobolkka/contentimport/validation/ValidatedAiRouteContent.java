package com.example.ilgeobolkka.contentimport.validation;

import java.util.List;

/**
 * C02 검증을 통과한 콘텐츠. 다음 단계는 manifest를 다시 읽지 않고 이 결과만 입력으로 쓴다.
 *
 * <p>페이지는 선수 관계의 위상 순서로 담는다. 선수 페이지가 항상 앞에 오므로 적재·임베딩이 순서를
 * 다시 계산하지 않아도 된다.
 */
public record ValidatedAiRouteContent(
        String contentVersion,
        String dataPolicyVersion,
        String embeddingModel,
        int embeddingDimensions,
        List<ValidatedBook> books) {

    public ValidatedAiRouteContent {
        books = List.copyOf(books);
    }

    public record ValidatedBook(
            long bookId,
            boolean aiRouteCandidate,
            List<ValidatedPage> pages,
            List<PrerequisiteEdge> prerequisiteEdges) {

        public ValidatedBook {
            pages = List.copyOf(pages);
            prerequisiteEdges = List.copyOf(prerequisiteEdges);
        }
    }

    /**
     * @param aiRouteCandidatePage 후보 집합에 넣을 페이지인가. C03은 이 값으로 Gateway에 보낼 페이지를
     *     고르고 역할 이름이나 내용으로 추론하지 않는다.
     * @param analysisText 후보 검색용 비공개 분석 텍스트. 임베딩 입력이다.
     */
    public record ValidatedPage(
            int pageNumber, boolean aiRouteCandidatePage, String analysisText) {}

    /** {@code 선수 -> 의존} 방향 간선. */
    public record PrerequisiteEdge(int beforePageNumber, int afterPageNumber) {}
}
