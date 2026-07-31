package com.example.ilgeobolkka.reading.exception;

public class PageContentNotFoundException extends RuntimeException {

    public PageContentNotFoundException() {
        super("페이지 콘텐츠를 찾을 수 없습니다.");
    }
}
