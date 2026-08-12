package com.example.ilgeobolkka.contentimport.embedding;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 후보 페이지 전체의 vector를 담은 불변 batch. 부분 성공 상태가 없다 — 한 페이지라도 실패하면 이
 * 객체가 아예 만들어지지 않는다.
 *
 * <p>C04는 이 결과만 쓰고 Gateway를 다시 호출하거나 vector를 재계산하지 않는다.
 */
public record EmbeddedAiRouteContent(
        String contentVersion,
        String embeddingModel,
        int embeddingDimensions,
        Map<PageKey, List<Double>> vectors) {

    public EmbeddedAiRouteContent {
        Map<PageKey, List<Double>> copiedVectors = new LinkedHashMap<>();
        vectors.forEach((key, vector) -> copiedVectors.put(key, List.copyOf(vector)));
        vectors = Collections.unmodifiableMap(copiedVectors);
    }

    /** vector key. 같은 페이지 번호라도 도서·콘텐츠 버전이 다르면 다른 vector다. */
    public record PageKey(long bookId, int pageNumber, String contentVersion) {}

    /** 후보 페이지의 vector. 후보가 아닌 페이지는 key 자체가 없다. */
    public List<Double> vectorOf(long bookId, int pageNumber) {
        return vectors.get(new PageKey(bookId, pageNumber, contentVersion));
    }
}
