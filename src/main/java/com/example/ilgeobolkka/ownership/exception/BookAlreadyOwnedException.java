package com.example.ilgeobolkka.ownership.exception;

public class BookAlreadyOwnedException extends RuntimeException {

    public BookAlreadyOwnedException(long readerId, long bookId) {
        super("이미 소장한 도서입니다. readerId=" + readerId + ", bookId=" + bookId);
    }
}
