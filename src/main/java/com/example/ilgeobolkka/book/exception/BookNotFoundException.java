package com.example.ilgeobolkka.book.exception;

public class BookNotFoundException extends RuntimeException {

    public BookNotFoundException(long bookId) {
        super("도서를 찾을 수 없습니다: " + bookId);
    }
}
