package com.example.ilgeobolkka.airoute.service.candidate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 한 도서·콘텐츠 버전의 페이지 벡터를 목적 벡터와 메모리에서 exact cosine 비교해 후보를 고른다.
 *
 * <p>DB도 Embeddings API도 모른다. 호출자가 같은 도서·버전의 지원 페이지를 모아 넘기고, 이 타입은
 * {@link AiRouteCandidatePolicy}의 값만 적용한다. 근사 검색이나 vector DB를 쓰지 않는 것이 ADR-0014의
 * 결정이다.
 *
 * <p>선수 전이 폐쇄는 하지 않는다. G03이 후보를 고른 뒤 추가하며, 그때 threshold와 30개 상한을 선수
 * 페이지에 다시 적용하지 않는다.
 */
public final class AiRouteCandidateSelector {

    /**
     * 후보를 확정 순서로 고른다. 같은 입력에는 항상 같은 결과가 나온다.
     *
     * @param bookId 대상 도서
     * @param contentVersion 대상 콘텐츠 버전
     * @param purposeEmbedding 정규화한 목적의 벡터
     * @param pages 같은 도서·버전의 지원 페이지 전체
     * @throws InvalidAiRouteCandidateInputException 도서·콘텐츠 버전이 섞였거나 페이지 번호가 중복일 때
     * @throws InvalidAiRouteEmbeddingException 목적과 모델·차원이 다른 페이지가 있을 때
     */
    public List<AiRouteCandidate> select(
            long bookId,
            String contentVersion,
            AiRouteEmbedding purposeEmbedding,
            List<AiRouteCandidatePage> pages) {
        if (contentVersion == null || contentVersion.isBlank()) {
            throw new InvalidAiRouteCandidateInputException("대상 콘텐츠 버전이 필요합니다.");
        }
        if (purposeEmbedding == null) {
            throw new InvalidAiRouteCandidateInputException("목적 임베딩이 필요합니다.");
        }
        if (pages == null) {
            throw new InvalidAiRouteCandidateInputException("페이지 목록은 null 대신 빈 목록이어야 합니다.");
        }

        // 계산 전에 전수 확인한다. 중간에 실패하면 어떤 페이지까지 비교했는지가 결과에 남는다.
        requireSameBookAndVersion(bookId, contentVersion, pages);
        requireComparableEmbeddings(purposeEmbedding, pages);

        List<AiRouteCandidate> aboveThreshold = new ArrayList<>();
        for (AiRouteCandidatePage page : pages) {
            double similarity = purposeEmbedding.cosineSimilarityTo(page.embedding());
            // 반올림하지 않은 값으로 비교한다. 정확히 MINIMUM_SIMILARITY 인 페이지는 포함한다.
            if (similarity >= AiRouteCandidatePolicy.MINIMUM_SIMILARITY) {
                aboveThreshold.add(AiRouteCandidate.from(page, similarity));
            }
        }

        // similarity 내림차순, 같을 때만 pageNumber 오름차순.
        Comparator<AiRouteCandidate> bySimilarity =
                Comparator.comparingDouble(AiRouteCandidate::similarity);
        aboveThreshold.sort(bySimilarity.reversed().thenComparingInt(AiRouteCandidate::pageNumber));

        int size = Math.min(aboveThreshold.size(), AiRouteCandidatePolicy.MAXIMUM_CANDIDATES);
        return List.copyOf(aboveThreshold.subList(0, size));
    }

    private void requireSameBookAndVersion(
            long bookId, String contentVersion, List<AiRouteCandidatePage> pages) {
        Set<Integer> seenPageNumbers = new HashSet<>();
        for (AiRouteCandidatePage page : pages) {
            if (page.bookId() != bookId) {
                throw new InvalidAiRouteCandidateInputException(
                        "다른 도서의 페이지가 섞였습니다. 기준 " + bookId + ", 페이지 " + page.pageId());
            }
            if (!contentVersion.equals(page.contentVersion())) {
                throw new InvalidAiRouteCandidateInputException(
                        "다른 콘텐츠 버전의 페이지가 섞였습니다. 기준 "
                                + contentVersion
                                + ", 페이지 "
                                + page.pageId());
            }
            if (!seenPageNumbers.add(page.pageNumber())) {
                throw new InvalidAiRouteCandidateInputException(
                        "페이지 번호가 중복입니다. " + page.pageNumber());
            }
        }
    }

    private void requireComparableEmbeddings(
            AiRouteEmbedding purposeEmbedding, List<AiRouteCandidatePage> pages) {
        for (AiRouteCandidatePage page : pages) {
            purposeEmbedding.requireComparableWith(page.embedding(), "페이지 " + page.pageId());
        }
    }
}
