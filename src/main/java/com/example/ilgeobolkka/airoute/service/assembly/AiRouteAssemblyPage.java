package com.example.ilgeobolkka.airoute.service.assembly;

import java.util.HashSet;
import java.util.List;

/**
 * 현재 AI 경로 생성이 지원되는 페이지의 공개 자료. 적재 검증을 통과한 현재 후보이므로 공개 주제와 예상
 * 시간이 필수이며, 비공개 분석 텍스트와 본문은 이 타입이 받지 않는다.
 *
 * @param pageId 영속화에 사용할 페이지 식별자
 * @param pageNumber 원본 PDF 페이지 번호
 * @param publicGuideTopic 사람이 검수한 공개 가이드 주제
 * @param estimatedReadingSeconds 콘텐츠 적재 때 계산한 예상 독서 시간(초)
 * @param duplicateGroupKeys 의미상 중복 페이지를 묶는 키. 고유 페이지는 빈 목록이다.
 */
public record AiRouteAssemblyPage(
        long pageId,
        int pageNumber,
        String publicGuideTopic,
        int estimatedReadingSeconds,
        List<String> duplicateGroupKeys) {

    public AiRouteAssemblyPage {
        if (pageId <= 0 || pageNumber <= 0) {
            throw new IllegalArgumentException("페이지 식별자와 번호는 양수여야 합니다.");
        }
        if (publicGuideTopic == null || publicGuideTopic.isBlank()) {
            throw new IllegalArgumentException("공개 가이드 주제가 필요합니다.");
        }
        if (estimatedReadingSeconds <= 0) {
            throw new IllegalArgumentException("예상 독서 시간은 양수여야 합니다.");
        }
        if (duplicateGroupKeys == null
                || duplicateGroupKeys.stream().anyMatch(key -> key == null || key.isBlank())) {
            throw new IllegalArgumentException("중복 그룹 키는 null이나 빈 문자열일 수 없습니다.");
        }
        if (new HashSet<>(duplicateGroupKeys).size() != duplicateGroupKeys.size()) {
            throw new IllegalArgumentException("한 페이지에 같은 중복 그룹 키를 반복할 수 없습니다.");
        }
        duplicateGroupKeys = List.copyOf(duplicateGroupKeys);
    }
}
