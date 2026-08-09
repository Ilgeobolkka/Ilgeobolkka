package com.example.ilgeobolkka.airoute.service.candidate;

import com.example.ilgeobolkka.airoute.exception.InvalidAiRouteEmbeddingException;

/**
 * 후보 비교에 쓰는 임베딩. 모델·차원과 값이 붙어 다녀야 다른 프로필의 벡터를 실수로 비교하지 않는다.
 *
 * <p>이 타입이 존재한다는 것 자체가 "유한 실수이고 zero norm이 아닌 벡터"라는 뜻이다. 검증을 계산 쪽에
 * 두면 similarity를 0으로 조용히 대체하는 경로가 열린다. ADR-0014와 후보 정책 v1은 그 경우 실패를
 * 요구한다.
 */
public final class AiRouteEmbedding {

    private final String model;
    private final double[] values;
    private final double norm;

    private AiRouteEmbedding(String model, double[] values, double norm) {
        this.model = model;
        this.values = values;
        this.norm = norm;
    }

    /**
     * {@code dimensions}는 호출자가 선언한 차원이다. 값 개수와 다르면 적재 쪽이 이미 어긋난 것이므로
     * 여기서 막는다. 통과한 뒤에는 {@code values.length}가 곧 차원이라 따로 들고 있지 않는다.
     *
     * @throws InvalidAiRouteEmbeddingException 모델이 비었거나, 차원이 값 개수와 다르거나, 값에 유한하지
     *     않은 수가 있거나, 모든 값이 0이라 norm이 0일 때
     */
    public static AiRouteEmbedding of(String model, int dimensions, double[] values) {
        if (model == null || model.isBlank()) {
            throw new InvalidAiRouteEmbeddingException("임베딩 모델이 필요합니다.");
        }
        if (dimensions <= 0) {
            throw new InvalidAiRouteEmbeddingException("차원은 1 이상이어야 합니다. 입력 " + dimensions);
        }
        if (values == null) {
            throw new InvalidAiRouteEmbeddingException("벡터 값이 필요합니다.");
        }
        if (values.length != dimensions) {
            throw new InvalidAiRouteEmbeddingException(
                    "선언한 차원과 값 개수가 다릅니다. 차원 " + dimensions + ", 값 " + values.length);
        }

        double[] copied = values.clone();
        double squaredSum = 0.0;
        for (int index = 0; index < copied.length; index++) {
            double value = copied[index];
            if (!Double.isFinite(value)) {
                throw new InvalidAiRouteEmbeddingException("유한하지 않은 값이 있습니다. 위치 " + index);
            }
            squaredSum += value * value;
        }
        if (squaredSum == 0.0) {
            throw new InvalidAiRouteEmbeddingException("zero norm 벡터는 비교할 수 없습니다.");
        }

        double norm = Math.sqrt(squaredSum);
        if (!Double.isFinite(norm) || norm == 0.0) {
            throw new InvalidAiRouteEmbeddingException("norm을 계산할 수 없습니다.");
        }
        return new AiRouteEmbedding(model, copied, norm);
    }

    /**
     * 같은 모델·차원인지 확인한다. 계산을 시작하기 전에 모든 페이지에 대해 먼저 부른다.
     *
     * @throws InvalidAiRouteEmbeddingException 모델이나 차원이 다를 때
     */
    public void requireComparableWith(AiRouteEmbedding other, String subject) {
        if (!model.equals(other.model)) {
            throw new InvalidAiRouteEmbeddingException(
                    subject + "의 임베딩 모델이 다릅니다. 기준 " + model + ", 입력 " + other.model);
        }
        if (values.length != other.values.length) {
            throw new InvalidAiRouteEmbeddingException(
                    subject
                            + "의 임베딩 차원이 다릅니다. 기준 "
                            + values.length
                            + ", 입력 "
                            + other.values.length);
        }
    }

    /**
     * exact cosine similarity. 근사 검색을 쓰지 않고 값 전체를 그대로 곱한다.
     *
     * <p>{@link #requireComparableWith}를 통과한 뒤에만 부른다.
     */
    public double cosineSimilarityTo(AiRouteEmbedding other) {
        double dot = 0.0;
        for (int index = 0; index < values.length; index++) {
            dot += values[index] * other.values[index];
        }
        return dot / (norm * other.norm);
    }

    /** 벡터 값은 로그에 남기지 않는다. */
    @Override
    public String toString() {
        return "AiRouteEmbedding[model=%s, dimensions=%d]".formatted(model, values.length);
    }

    /** 테스트가 "norm이 정확히 1.0"이라는 전제를 확인하는 데 쓴다. 공개 계약이 아니다. */
    double norm() {
        return norm;
    }
}
