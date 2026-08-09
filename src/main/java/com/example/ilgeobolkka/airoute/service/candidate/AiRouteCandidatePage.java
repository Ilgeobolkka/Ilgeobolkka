package com.example.ilgeobolkka.airoute.service.candidate;

import com.example.ilgeobolkka.airoute.exception.InvalidAiRouteCandidateInputException;
import com.example.ilgeobolkka.book.entity.BookPageContentType;
import java.util.List;

/**
 * 후보 비교에 넣는 페이지 하나. selector가 DB를 모르므로 호출자가 지원 페이지를 모아 넘긴다.
 *
 * <p>페이지가 자기 {@code bookId}·{@code contentVersion}을 들고 다닌다. selector가 요청 대상과 대조해
 * 다른 도서·버전이 섞여 들어오면 거부하기 위해서다. 목록만 받으면 섞인 것을 알아낼 방법이 없다.
 *
 * <p>평가 정답(requiredConcepts, allowedAlternativePageNumbers)은 이 타입에 넣지 않는다. 평가 정답은
 * evaluation.json 계약에만 있고 runtime 검색 입력이 아니다. 필드가 없으면 실수로 흘러들어올 수 없다.
 *
 * @param bookId 이 페이지가 속한 도서
 * @param contentVersion 이 페이지를 적재한 콘텐츠 버전
 * @param pageId 페이지 식별자
 * @param pageNumber 원본 PDF 페이지 번호. 동점 정렬 기준이다.
 * @param contentType TEXT·IMAGE. selector는 둘을 구분하지 않고 같은 기준을 적용한다.
 * @param embedding 같은 모델·차원의 페이지 벡터
 * @param analysisTextRef 비공개 분석 텍스트를 가리키는 호출자 정의 참조. 텍스트 자체를 담지 않는다.
 * @param prerequisitePageNumbers 이 페이지의 직접 선수 페이지 번호. 전이 폐쇄는 G03이 한다.
 */
public record AiRouteCandidatePage(
        long bookId,
        String contentVersion,
        long pageId,
        int pageNumber,
        BookPageContentType contentType,
        AiRouteEmbedding embedding,
        String analysisTextRef,
        List<Integer> prerequisitePageNumbers) {

    public AiRouteCandidatePage {
        if (contentVersion == null || contentVersion.isBlank()) {
            throw new InvalidAiRouteCandidateInputException("페이지의 콘텐츠 버전이 필요합니다.");
        }
        if (pageNumber <= 0) {
            throw new InvalidAiRouteCandidateInputException(
                    "페이지 번호는 1 이상이어야 합니다. 입력 " + pageNumber);
        }
        if (contentType == null) {
            throw new InvalidAiRouteCandidateInputException("페이지 콘텐츠 종류가 필요합니다.");
        }
        if (embedding == null) {
            throw new InvalidAiRouteCandidateInputException("페이지 임베딩이 필요합니다.");
        }
        if (analysisTextRef == null || analysisTextRef.isBlank()) {
            throw new InvalidAiRouteCandidateInputException("분석 텍스트 참조가 필요합니다.");
        }
        if (prerequisitePageNumbers == null) {
            throw new InvalidAiRouteCandidateInputException(
                    "선수 페이지 목록은 null 대신 빈 목록이어야 합니다.");
        }
        prerequisitePageNumbers = List.copyOf(prerequisitePageNumbers);
    }
}
