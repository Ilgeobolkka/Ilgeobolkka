package com.example.ilgeobolkka.contentimport.embedding;

import com.example.ilgeobolkka.contentimport.validation.ValidatedAiRouteContent;
import com.example.ilgeobolkka.infra.openai.OpenAiEmbeddingGateway;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 검증된 콘텐츠의 후보 페이지 분석 텍스트를 vector로 바꾼다.
 *
 * <p>DB 트랜잭션을 열기 전에 완전한 batch를 만든다. 한 페이지라도 실패하면 전체를 버리므로 부분
 * vector가 파일이나 DB로 나가지 않는다. 이 서비스는 Repository를 주입받지 않아 저장 경로 자체가 없다.
 */
public final class AiRouteContentEmbeddingService {

    private static final int EMBEDDING_BATCH_SIZE = 100;

    private final OpenAiEmbeddingGateway gateway;

    public AiRouteContentEmbeddingService(OpenAiEmbeddingGateway gateway) {
        this.gateway = gateway;
    }

    /**
     * @param expectedDataPolicyVersion 환경이 확인한 데이터 정책. 호출 직전에 manifest 값과 다시 맞춰
     *     보고, 어긋나면 Gateway를 한 번도 부르지 않고 실패한다.
     */
    public EmbeddedAiRouteContent embed(
            ValidatedAiRouteContent content, String expectedDataPolicyVersion) {
        require(content != null, "검증된 콘텐츠가 필요합니다.");
        require(
                expectedDataPolicyVersion != null
                        && expectedDataPolicyVersion.equals(content.dataPolicyVersion()),
                "콘텐츠 dataPolicyVersion(%s)이 환경 설정(%s)과 달라 Embeddings를 호출하지 않습니다."
                        .formatted(content.dataPolicyVersion(), expectedDataPolicyVersion));

        String model = content.embeddingModel();
        int dimensions = content.embeddingDimensions();
        Map<EmbeddedAiRouteContent.PageKey, List<Double>> vectors = new LinkedHashMap<>();
        List<PendingPage> pendingPages = new ArrayList<>();

        // bookId ASC, pageNumber ASC의 결정적 순서로 요청한다. 검증 결과는 위상 순서라 그대로 쓰지 않는다.
        List<ValidatedAiRouteContent.ValidatedBook> books =
                content.books().stream()
                        .sorted(Comparator.comparingLong(
                                ValidatedAiRouteContent.ValidatedBook::bookId))
                        .toList();
        for (ValidatedAiRouteContent.ValidatedBook book : books) {
            List<ValidatedAiRouteContent.ValidatedPage> pages =
                    book.pages().stream()
                            .sorted(Comparator.comparingInt(
                                    ValidatedAiRouteContent.ValidatedPage::pageNumber))
                            .toList();
            for (ValidatedAiRouteContent.ValidatedPage page : pages) {
                // 후보 여부는 페이지 내용이 아니라 이 값으로만 판정한다.
                if (!page.aiRouteCandidatePage()) {
                    continue;
                }
                EmbeddedAiRouteContent.PageKey key =
                        new EmbeddedAiRouteContent.PageKey(
                                book.bookId(), page.pageNumber(), content.contentVersion());
                pendingPages.add(new PendingPage(
                        key,
                        new OpenAiEmbeddingGateway.PageAnalysisInput(page.analysisText())));
            }
        }

        for (int from = 0; from < pendingPages.size(); from += EMBEDDING_BATCH_SIZE) {
            int to = Math.min(from + EMBEDDING_BATCH_SIZE, pendingPages.size());
            List<PendingPage> batch = pendingPages.subList(from, to);
            List<OpenAiEmbeddingGateway.Embedding> embeddings = gateway.embedPageAnalyses(
                    batch.stream().map(PendingPage::input).toList(),
                    model,
                    dimensions);
            require(
                    embeddings != null && embeddings.size() == batch.size(),
                    "후보 페이지 embedding 응답 수가 요청 수와 다릅니다.");
            for (int index = 0; index < batch.size(); index++) {
                PendingPage pending = batch.get(index);
                vectors.put(
                        pending.key(),
                        validVector(
                                pending.key(),
                                embeddings.get(index),
                                model,
                                dimensions));
            }
        }

        require(
                vectors.size() == pendingPages.size(),
                "후보 페이지 %d개 중 %d개만 vector를 받았습니다."
                        .formatted(pendingPages.size(), vectors.size()));
        return new EmbeddedAiRouteContent(
                content.contentVersion(), model, dimensions, vectors);
    }

    private List<Double> validVector(
            EmbeddedAiRouteContent.PageKey key,
            OpenAiEmbeddingGateway.Embedding embedding,
            String model,
            int dimensions) {
        require(embedding != null, describe(key) + " vector 응답이 없습니다.");
        require(
                model.equals(embedding.model()),
                describe(key) + " vector 모델이 %s가 아니라 %s입니다.".formatted(model, embedding.model()));
        require(
                embedding.dimensions() == dimensions,
                describe(key) + " vector 차원이 %d가 아니라 %d입니다."
                        .formatted(dimensions, embedding.dimensions()));
        List<Double> vector = embedding.vector();
        require(vector != null, describe(key) + " vector가 비어 있습니다.");
        require(
                vector.size() == dimensions,
                describe(key) + " vector 길이가 %d가 아니라 %d입니다."
                        .formatted(dimensions, vector.size()));
        for (Double value : vector) {
            require(
                    value != null && Double.isFinite(value),
                    describe(key) + " vector에 유한하지 않은 값이 있습니다.");
        }
        return List.copyOf(vector);
    }

    /** 실패 메시지에 분석 텍스트나 vector 원문을 넣지 않는다. 페이지를 가리키는 식별자만 남긴다. */
    private String describe(EmbeddedAiRouteContent.PageKey key) {
        return "book %d p%d".formatted(key.bookId(), key.pageNumber());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AiRouteContentEmbeddingException(message);
        }
    }

    private record PendingPage(
            EmbeddedAiRouteContent.PageKey key,
            OpenAiEmbeddingGateway.PageAnalysisInput input) {}
}
