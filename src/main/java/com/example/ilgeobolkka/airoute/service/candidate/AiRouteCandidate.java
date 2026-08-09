package com.example.ilgeobolkka.airoute.service.candidate;

import java.util.List;

/**
 * 후보 페이지 하나. 정렬이 끝난 확정 순서로 나온다.
 *
 * <p>생성 경로는 {@link #from}뿐이다. 입력 검증은 {@link AiRouteCandidatePage}가 이미 마쳤으므로 여기서
 * 다시 검사하지 않는다. record 라 canonical 생성자를 좁힐 수는 없지만(JLS 8.10.4), 직접 만들 이유가 없다.
 *
 * <p>{@code similarity}는 반올림하지 않은 원값이다. 백분율이나 등급으로 바꾸지 않는다. 사용자에게 보이는
 * 값이 아니라 후보 선정 근거이며, 백분율 DTO를 만들면 정책 값과 화면 표시가 서로를 끌고 다니게 된다.
 *
 * @param pageId 페이지 식별자
 * @param pageNumber 원본 PDF 페이지 번호
 * @param similarity 반올림하지 않은 exact cosine similarity
 * @param analysisTextRef 비공개 분석 텍스트를 가리키는 참조. 텍스트 자체를 담지 않는다.
 * @param prerequisitePageNumbers 이 페이지의 직접 선수 페이지 번호. 전이 폐쇄는 G03이 한다.
 */
public record AiRouteCandidate(
        long pageId,
        int pageNumber,
        double similarity,
        String analysisTextRef,
        List<Integer> prerequisitePageNumbers) {

    public AiRouteCandidate {
        prerequisitePageNumbers = List.copyOf(prerequisitePageNumbers);
    }

    static AiRouteCandidate from(AiRouteCandidatePage page, double similarity) {
        return new AiRouteCandidate(
                page.pageId(),
                page.pageNumber(),
                similarity,
                page.analysisTextRef(),
                page.prerequisitePageNumbers());
    }
}
