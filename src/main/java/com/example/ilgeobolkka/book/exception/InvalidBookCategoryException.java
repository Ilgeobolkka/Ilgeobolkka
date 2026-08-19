package com.example.ilgeobolkka.book.exception;

public class InvalidBookCategoryException extends RuntimeException {

    public InvalidBookCategoryException(String category) {
        super("존재하지 않는 도서 카테고리입니다. " + category);
    }
}
