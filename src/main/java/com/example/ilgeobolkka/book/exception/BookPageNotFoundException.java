package com.example.ilgeobolkka.book.exception;

public class BookPageNotFoundException extends RuntimeException {

    public BookPageNotFoundException(long bookId, int pageNumber) {
        super("페이지를 찾을 수 없습니다: bookId=" + bookId + ", pageNumber=" + pageNumber);
    }
}
