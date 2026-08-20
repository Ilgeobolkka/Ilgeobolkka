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
 * @param candidates 운영 임계값·상한을 적용해 확정한 후보. 없으면 빈 목록이다.
 * @param scoredCandidates 임계값·상한을 적용하기 전 전체 페이지 점수. 평가 외에는 사용하지 않는다.
 */
public record AiRouteCandidateSelection(
        String candidatePolicyVersion,
        List<AiRouteCandidate> candidates,
        List<AiRouteCandidate> scoredCandidates) {

    public AiRouteCandidateSelection {
        candidates = List.copyOf(candidates);
        scoredCandidates = List.copyOf(scoredCandidates);
    }
}
