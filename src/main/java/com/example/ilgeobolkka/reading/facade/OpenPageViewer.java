package com.example.ilgeobolkka.reading.facade;

import java.util.UUID;

/**
 * 페이지 열기 요청이 새 뷰어(POST, {@code bookId} 지정)인지 기존 뷰어(PATCH,
 * {@code viewerSessionId} 지정)인지를 구분해 {@link ReadingFacade#openPage}에 전달한다.
 */
public sealed interface OpenPageViewer {

    record NewViewer(long bookId) implements OpenPageViewer {}

    record ExistingViewer(UUID viewerSessionId) implements OpenPageViewer {}
}
