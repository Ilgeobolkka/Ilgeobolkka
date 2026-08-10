package com.example.ilgeobolkka.airoute.service.candidate;

import java.util.List;

/**
 * 후보 선택 결과. 확정 순서의 후보와 그 후보를 뽑은 정책 버전을 함께 들고 다닌다.
 *
 * <p>버전을 결과에 붙이는 이유는 추적성이다. G05는 생성 결과에 {@code candidatePolicyVersion}을 기록해야
 * 하는데, 후보 목록과 버전을 따로 받으면 정책이 바뀐 뒤 둘이 어긋난 채로 저장될 수 있다. 실제로 어떤 정책이
 * 적용됐는지는 selector만 알고 있으므로 selector가 함께 돌려준다.
 *
 * @param candidatePolicyVersion 이 후보를 뽑은 정책 버전
 * @param candidates similarity 내림차순·pageNumber 오름차순으로 확정된 후보. 없으면 빈 목록이다.
 */
public record AiRouteCandidateSelection(String candidatePolicyVersion, List<AiRouteCandidate> candidates) {

    public AiRouteCandidateSelection {
        candidates = List.copyOf(candidates);
    }
}
