package com.example.ilgeobolkka.airoute.service.candidate;

/**
 * 후보 정책 {@code air-candidate-v1}의 고정 값.
 *
 * <p>GATE-AIR-02가 확정한 값이라 코드에서 튜닝하지 않는다. 값을 바꾸려면 정책 버전을 함께 올려야 한다.
 * 저장한 생성 결과가 어느 정책으로 뽑혔는지 추적할 수 있어야 하기 때문이다.
 */
public final class AiRouteCandidatePolicy {

    /** 후보 정책 버전. 생성 결과와 함께 기록해 재현 조건을 남긴다. */
    public static final String VERSION = "air-candidate-v1";

    /** 후보로 남길 최소 cosine similarity. 정확히 이 값은 포함한다. */
    public static final double MINIMUM_SIMILARITY = 0.30;

    /** 정렬 뒤 남길 후보 최대 개수. */
    public static final int MAXIMUM_CANDIDATES = 30;

    private AiRouteCandidatePolicy() {}
}
